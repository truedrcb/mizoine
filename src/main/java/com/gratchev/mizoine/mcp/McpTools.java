package com.gratchev.mizoine.mcp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryFiles.FileContent;
import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.MoveResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;
import com.gratchev.mizoine.search.Search;
import com.gratchev.mizoine.tags.Tags;
import com.gratchev.mizoine.tags.TagsFile;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * The tools an AI agent gets over MCP (step 33): thin adapters over {@link RepositoryFiles}, so the same grants and
 * rules apply as for the HTTP file API. Files are always given raw, never rendered (step 38). Paths are relative
 * to the repository root; a path ending with '/' is a folder.
 */
final class McpTools {

	/** The logged-in user, put into the transport context by {@link McpConfig}. */
	static final String USER = "user";

	private static final String REPOSITORY = "repository";
	private static final String PATH = "path";
	private static final String CONTENT = "content";
	private static final String IF_MATCH = "if_match";
	private static final String FROM = "from";
	private static final String TO = "to";
	static final String TAGS = "tags";
	/** Step 74 (D80): the largest file read or uploaded as base64 through MCP; HTTP takes up to the file size limit. */
	static final int MAX_BINARY_BYTES = 10 * 1024 * 1024;
	/** Base64 of {@link #MAX_BINARY_BYTES}: a longer text is refused before it is decoded. */
	private static final int MAX_BASE64 = (MAX_BINARY_BYTES + 2) / 3 * 4;
	private static final String REVIEW = " Files are plain files in a Git working folder: a human reviews them with git diff "
			+ "and commits; nothing is versioned until then.";

	private final RepositoryFiles files;
	private final long maxFileSize;
	private final McpJsonMapper json;
	private final Optional<McpSearchTool> search;

	/** @param search the search, if it is switched on: then agents get the tool {@code search} (step 59) */
	McpTools(final RepositoryFiles files, final long maxFileSize, final McpJsonMapper json, final Optional<Search> search) {
		this.files = files;
		this.maxFileSize = maxFileSize;
		this.json = json;
		this.search = search.map(found -> new McpSearchTool(found, json));
	}

	List<SyncToolSpecification> all() {
		return Stream.concat(fileTools().stream(), search.stream().flatMap(tool -> tool.specifications().stream())).toList();
	}

	private List<SyncToolSpecification> fileTools() {
		return List.of(
				tool("list_repositories", "List the repositories you have access to.", schema(), this::listRepositories),
				tool("list_folder", "List the subfolders and files of a folder you may see. Path '/' is the repository root.",
						schema(REPOSITORY, PATH), this::listFolder),
				tool("read_file", "Read a file exactly as stored: a text file (UTF-8, up to 2 MB) as text; a picture as an "
						+ "image; any other file (PDF, office document, …) as an embedded resource with its bytes in base64; "
						+ "binary files up to 10 MB. The structured result also has its version: pass it as if_match when "
						+ "writing the file back.", schema(REPOSITORY, PATH), this::readFile),
				tool("write_file", "Create or replace a text file with exactly this content. The folder must exist "
						+ "(create_folder). With if_match (the version from read_file, or from an earlier write_file), the "
						+ "file is written only if nobody changed it since: always pass it when changing a file you read. "
						+ "For pictures, PDFs and other binary files use upload_file." + REVIEW,
						schema(REPOSITORY, PATH, CONTENT, IF_MATCH), this::writeFile),
				tool("upload_file", "Create or replace a binary file (a picture, PDF, office document, …) with exactly these "
						+ "bytes, given in base64; at most 10 MB. Its type comes from its name (photo.jpg, report.pdf). The "
						+ "folder must exist (create_folder); if_match as for write_file. For text use write_file." + REVIEW,
						uploadSchema(), this::uploadFile),
				tool("create_folder", "Create a folder; its parent folder must exist." + REVIEW, schema(REPOSITORY, PATH),
						this::createFolder),
				tool("delete", "Delete a file, or an empty folder (path ending with '/'). Folders with content are not "
						+ "deleted." + REVIEW, schema(REPOSITORY, PATH), this::delete),
				tool("move", "Move or rename a file, or a folder with everything in it (paths ending with '/'), within the "
						+ "repository. The destination's folder must exist; an existing destination is never overwritten. "
						+ "With if_match (the version from read_file), a file is moved only if nobody changed it since."
						+ REVIEW, schema(REPOSITORY, FROM, TO, IF_MATCH), this::move),
				tool("set_tags", "Replace the tags of a folder (path ending with '/'); an empty list removes them. Tags "
						+ "are words: letters, digits, -, _, /; statuses are key:value (status:open), one value per key. "
						+ "list_folder shows the tags; list_tags the tags in use: keep to those where they fit. With "
						+ "if_match (the folder's tags version), only if nobody changed them since." + REVIEW,
						tagsSchema(), this::setTags));
	}

	/** Step 64: a folder's tags, as the HTTP API sets them. */
	private CallToolResult setTags(final String user, final Map<String, Object> arguments) {
		final String path = text(arguments, PATH);
		if (!RepositoryFiles.isFolder(path)) {
			return error(TagsFile.FOLDERS_ONLY);
		}
		final String where = where(arguments);
		return switch (files.setFolderTags(user, text(arguments, REPOSITORY), RepositoryFiles.segments(path),
				Tags.of(texts(arguments, TAGS)), version(arguments))) {
			case RepositoryFiles.FolderTags written -> ok("Tags of " + where + ": " + (written.tags().isEmpty()
					? "none" : String.join(", ", written.tags().texts())
							+ written.version().map(version -> " (version " + version + ")").orElse("")));
			case RepositoryFiles.TagsResult.PreconditionFailed failed -> error("Not changed: the tags of " + where
					+ " were changed since that version. Read them again first (list_folder).");
		};
	}

	private static Map<String, Object> tagsSchema() {
		final Map<String, Object> properties = new LinkedHashMap<>();
		properties.put(REPOSITORY, Map.of("type", "string", "description", DESCRIPTIONS.get(REPOSITORY)));
		properties.put(PATH, Map.of("type", "string", "description", "The folder, ending with '/'"));
		properties.put(TAGS, Map.of("type", "array", "items", Map.of("type", "string"),
				"description", "The folder's new tags, e.g. [\"project\", \"status:open\"]"));
		properties.put(IF_MATCH, Map.of("type", "string", "description", DESCRIPTIONS.get(IF_MATCH)));
		return Map.of("type", "object", "properties", properties, "required", List.of(REPOSITORY, PATH, TAGS));
	}

	/** A list of texts; anything else is the agent's error, said in the answer. */
	static List<String> texts(final Map<String, Object> arguments, final String name) {
		if (!(arguments.get(name) instanceof final List<?> list) || !list.stream().allMatch(String.class::isInstance)) {
			throw new IllegalArgumentException("Not a list of texts: " + name);
		}
		return list.stream().map(String.class::cast).toList();
	}

	private CallToolResult listRepositories(final String user, final Map<String, Object> arguments) {
		return ok(json(files.repositories(user)));
	}

	private CallToolResult listFolder(final String user, final Map<String, Object> arguments) {
		return ok(json(files.listFolder(user, text(arguments, REPOSITORY), RepositoryFiles.segments(text(arguments, PATH)))));
	}

	/** The text as content (for every client), and text with version as structured content (step 42). */
	/** Step 74 (D80): text as text; otherwise MCP's own content types, a picture as an image, else a base64 blob. */
	private CallToolResult readFile(final String user, final Map<String, Object> arguments) {
		final String path = text(arguments, PATH);
		return switch (files.readContent(user, text(arguments, REPOSITORY), RepositoryFiles.segments(path),
				MAX_BINARY_BYTES)) {
			case FileContent.Text file -> CallToolResult.builder()
					.addTextContent(file.content())
					.structuredContent(Map.of("text", file.content(), "version", file.version()))
					.isError(false)
					.build();
			case FileContent.Bytes file -> {
				final String data = Base64.getEncoder().encodeToString(file.content());
				yield CallToolResult.builder()
						.addTextContent(where(arguments) + ": " + file.mimeType() + ", " + file.content().length
								+ " bytes, version " + file.version())
						.addContent(file.mimeType().startsWith("image/")
								? McpSchema.ImageContent.builder(data, file.mimeType()).build()
								: McpSchema.EmbeddedResource.builder(McpSchema.BlobResourceContents.builder(url(arguments), data)
										.mimeType(file.mimeType()).build()).build())
						.structuredContent(Map.of("mimeType", file.mimeType(), "size", file.content().length,
								"version", file.version()))
						.isError(false)
						.build();
			}
			case FileContent.TooLarge large -> error("Not read: " + where(arguments) + " has " + large.size()
					+ " bytes, more than 10 MB through MCP. Download it over HTTP: " + url(arguments));
		};
	}

	private CallToolResult writeFile(final String user, final Map<String, Object> arguments) {
		return written(arguments, files.write(user, text(arguments, REPOSITORY), RepositoryFiles.segments(text(arguments, PATH)),
				new ByteArrayInputStream(text(arguments, CONTENT).getBytes(StandardCharsets.UTF_8)), maxFileSize,
				new Precondition(version(arguments), false)));
	}

	/** Step 74 (D80): base64 checked where it comes in, its length before it is decoded; nothing written otherwise. */
	private CallToolResult uploadFile(final String user, final Map<String, Object> arguments) {
		final String content = text(arguments, CONTENT);
		if (content.length() > MAX_BASE64) {
			return error("Not written: more than 10 MB through MCP. Upload it over HTTP (PUT on the file's URL).");
		}
		final byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(content);
		} catch (final IllegalArgumentException e) {
			return error("Not written: content is no base64 (RFC 4648, no line breaks).");
		}
		if (bytes.length > MAX_BINARY_BYTES) {
			return error("Not written: more than 10 MB through MCP. Upload it over HTTP (PUT on the file's URL).");
		}
		return written(arguments, files.write(user, text(arguments, REPOSITORY),
				RepositoryFiles.segments(text(arguments, PATH)), new ByteArrayInputStream(bytes), maxFileSize,
				new Precondition(version(arguments), false)));
	}

	private CallToolResult written(final Map<String, Object> arguments, final Result result) {
		final String where = where(arguments);
		return switch (result) {
			case Result.Created created -> ok("Created " + where + ", version " + created.etag());
			case Result.Replaced replaced -> ok("Replaced " + where + ", version " + replaced.etag());
			case Result.PreconditionFailed failed -> error("Not written: " + where + " was changed since that version. "
					+ "Read it again first.");
			case Result.ParentMissing missing -> error("Not written: the folder of " + where + " does not exist. "
					+ "Create it with create_folder first.");
			case Result.IsFolder folder -> error("Not written: " + where + " is a folder.");
			case Result.TooLarge large -> error("Not written: larger than " + maxFileSize + " bytes.");
			case Result.NotFound notFound -> error("Not found: " + where);
		};
	}

	private CallToolResult createFolder(final String user, final Map<String, Object> arguments) {
		final String where = where(arguments);
		return switch (files.createFolder(user, text(arguments, REPOSITORY), RepositoryFiles.segments(text(arguments, PATH)),
				false)) {
			case FolderResult.Created created -> ok("Created folder " + where);
			case FolderResult.Exists exists -> ok("Folder exists: " + where);
			// not asked for here (no create-only), but the same fact
			case FolderResult.PreconditionFailed failed -> ok("Folder exists: " + where);
			case FolderResult.ParentMissing missing -> error("Not created: the parent folder of " + where + " does not exist.");
			case FolderResult.FileInTheWay file -> error("Not created: a file is at " + where);
			case FolderResult.NotFound notFound -> error("Not found: " + where);
		};
	}

	private CallToolResult delete(final String user, final Map<String, Object> arguments) {
		final String path = text(arguments, PATH);
		final String where = where(arguments);
		return switch (files.delete(user, text(arguments, REPOSITORY), RepositoryFiles.segments(path),
				RepositoryFiles.isFolder(path), Optional.empty())) {
			case DeleteResult.Deleted deleted -> ok("Deleted " + where);
			case DeleteResult.NotEmpty notEmpty -> error("Not deleted: folder " + where + " is not empty.");
			case DeleteResult.IsGrantedFolder grantedFolder -> error("Not deleted: " + where + " is one of your granted folders.");
			case DeleteResult.PreconditionFailed failed -> error("Not deleted: " + where + " was changed.");
			case DeleteResult.NotFound notFound -> error("Not found: " + where);
		};
	}

	private CallToolResult move(final String user, final Map<String, Object> arguments) {
		final String from = text(arguments, FROM);
		final String source = where(arguments);
		final String destination = where(arguments, TO);
		return switch (files.move(user, text(arguments, REPOSITORY), RepositoryFiles.segments(from),
				RepositoryFiles.segments(text(arguments, TO)), RepositoryFiles.isFolder(from),
				version(arguments))) {
			case MoveResult.Moved moved -> ok("Moved " + source + " to " + destination);
			case MoveResult.DestinationExists exists -> error("Not moved: " + destination + " exists. "
					+ "Choose another name, or delete it first.");
			case MoveResult.DestinationParentMissing missing -> error("Not moved: the folder of " + destination
					+ " does not exist. Create it with create_folder first.");
			case MoveResult.IntoItself itself -> error("Not moved: a folder cannot move into itself.");
			case MoveResult.PreconditionFailed failed -> error("Not moved: " + source + " was changed since that version. "
					+ "Read it again first.");
			case MoveResult.NotAtomic notAtomic -> error("Not moved: source and destination are on different file systems.");
			case MoveResult.IsGrantedFolder grantedFolder -> error("Not moved: " + source + " is one of your granted folders.");
			case MoveResult.NotFound notFound -> error("Not found: " + source);
		};
	}

	/** A tool whose errors from {@link RepositoryFiles} (not found, misconfigured, not text) become tool errors. */
	private static SyncToolSpecification tool(final String name, final String description,
			final Map<String, Object> schema, final BiFunction<String, Map<String, Object>, CallToolResult> call) {
		return SyncToolSpecification.builder()
				.tool(McpSchema.Tool.builder(name, schema).description(description).build())
				.callHandler((final McpTransportContext context, final CallToolRequest request) -> {
					final Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
					try {
						return call.apply((String) context.get(USER), arguments);
					} catch (final RepositoryFiles.NotVisibleException e) {
						// the path as the agent wrote it, as in the tools' own "Not found"
						return error("Not found: " + where(arguments));
					} catch (final RepositoryFiles.MisconfiguredException | IllegalArgumentException e) {
						return error(e.getMessage());
					}
				})
				.build();
	}

	private static final Map<String, String> DESCRIPTIONS = Map.of(
			REPOSITORY, "Repository name, from list_repositories",
			PATH, "Path below the repository root, e.g. knowledge/ideas.md; a folder ends with '/'",
			CONTENT, "The complete new content of the file",
			IF_MATCH, "Optional: the version from read_file or an earlier write_file; done only if the file still has it",
			FROM, "Path of the file or folder to move, e.g. inbox/note.md; a folder ends with '/'",
			TO, "Its new path, e.g. knowledge/note.md; the folder it goes into must exist");

	/** JSON schema of the arguments; all but {@code if_match} are required. */
	/** The arguments of upload_file: the content declared as base64 (JSON Schema {@code contentEncoding}). */
	private static Map<String, Object> uploadSchema() {
		final Map<String, Object> properties = new LinkedHashMap<>();
		properties.put(REPOSITORY, Map.of("type", "string", "description", DESCRIPTIONS.get(REPOSITORY)));
		properties.put(PATH, Map.of("type", "string", "description", DESCRIPTIONS.get(PATH)));
		properties.put(CONTENT, Map.of("type", "string", "contentEncoding", "base64",
				"description", "The complete new content of the file, its bytes in base64 (RFC 4648)"));
		properties.put(IF_MATCH, Map.of("type", "string", "description", DESCRIPTIONS.get(IF_MATCH)));
		return Map.of("type", "object", "properties", properties, "required", List.of(REPOSITORY, PATH, CONTENT));
	}

	private static Map<String, Object> schema(final String... arguments) {
		final Map<String, Object> properties = new LinkedHashMap<>();
		for (final String argument : arguments) {
			properties.put(argument, Map.of("type", "string", "description", DESCRIPTIONS.get(argument)));
		}
		return Map.of("type", "object", "properties", properties,
				"required", List.of(arguments).stream().filter(argument -> !IF_MATCH.equals(argument)).toList());
	}

	/** A text argument; missing or not text is the agent's error, said in the tool's answer. */
	static String text(final Map<String, Object> arguments, final String name) {
		if (!(arguments.get(name) instanceof final String value)) {
			throw new IllegalArgumentException("Missing argument: " + name);
		}
		return value;
	}

	/** The optional {@code if_match}: absent, or a version as text. */
	private static Optional<String> version(final Map<String, Object> arguments) {
		final Object value = arguments.get(IF_MATCH);
		if (value != null && !(value instanceof String)) {
			throw new IllegalArgumentException("Not a text: " + IF_MATCH);
		}
		return Optional.ofNullable((String) value);
	}

	/** The path the tool works on, as the agent wrote it: {@code path}, or for {@code move} its {@code from}. */
	private static String where(final Map<String, Object> arguments) {
		return where(arguments, arguments.containsKey(FROM) ? FROM : PATH);
	}

	/** The file's URL path on this server, encoded, as for HTTP (step 74). */
	private static String url(final Map<String, Object> arguments) {
		return RepositoryFiles.url(text(arguments, REPOSITORY), RepositoryFiles.segments(text(arguments, PATH)), false);
	}

		private static String where(final Map<String, Object> arguments, final String path) {
		return text(arguments, REPOSITORY) + "/" + text(arguments, path).replaceFirst("^/", "");
	}

	private String json(final Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (final java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static CallToolResult ok(final String text) {
		return CallToolResult.builder().addTextContent(text).isError(false).build();
	}

	static CallToolResult error(final String text) {
		return CallToolResult.builder().addTextContent(text).isError(true).build();
	}
}
