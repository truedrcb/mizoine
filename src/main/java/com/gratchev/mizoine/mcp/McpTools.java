package com.gratchev.mizoine.mcp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.search.Search;
import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.MoveResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;

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
		return Stream.concat(fileTools().stream(), search.map(McpSearchTool::specification).stream()).toList();
	}

	private List<SyncToolSpecification> fileTools() {
		return List.of(
				tool("list_repositories", "List the repositories you have access to.", schema(), this::listRepositories),
				tool("list_folder", "List the subfolders and files of a folder you may see. Path '/' is the repository root.",
						schema(REPOSITORY, PATH), this::listFolder),
				tool("read_file", "Read a text file (UTF-8, up to 2 MB) exactly as stored. The structured result also "
						+ "has its version: pass it as if_match when writing the file back.", schema(REPOSITORY, PATH),
						this::readFile),
				tool("write_file", "Create or replace a text file with exactly this content. The folder must exist "
						+ "(create_folder). With if_match (the version from read_file, or from an earlier write_file), the "
						+ "file is written only if nobody changed it since: always pass it when changing a file you read." + REVIEW,
						schema(REPOSITORY, PATH, CONTENT, IF_MATCH), this::writeFile),
				tool("create_folder", "Create a folder; its parent folder must exist." + REVIEW, schema(REPOSITORY, PATH),
						this::createFolder),
				tool("delete", "Delete a file, or an empty folder (path ending with '/'). Folders with content are not "
						+ "deleted." + REVIEW, schema(REPOSITORY, PATH), this::delete),
				tool("move", "Move or rename a file, or a folder with everything in it (paths ending with '/'), within the "
						+ "repository. The destination's folder must exist; an existing destination is never overwritten. "
						+ "With if_match (the version from read_file), a file is moved only if nobody changed it since."
						+ REVIEW, schema(REPOSITORY, FROM, TO, IF_MATCH), this::move));
	}

	private CallToolResult listRepositories(final String user, final Map<String, Object> arguments) {
		return ok(json(files.repositories(user)));
	}

	private CallToolResult listFolder(final String user, final Map<String, Object> arguments) {
		return ok(json(files.listFolder(user, text(arguments, REPOSITORY), RepositoryFiles.segments(text(arguments, PATH)))));
	}

	/** The text as content (for every client), and text with version as structured content (step 42). */
	private CallToolResult readFile(final String user, final Map<String, Object> arguments) {
		final RepositoryFiles.FileText file = files.readText(user, text(arguments, REPOSITORY),
				RepositoryFiles.segments(text(arguments, PATH)));
		return CallToolResult.builder()
				.addTextContent(file.content())
				.structuredContent(Map.of("text", file.content(), "version", file.version()))
				.isError(false)
				.build();
	}

	private CallToolResult writeFile(final String user, final Map<String, Object> arguments) {
		final String where = where(arguments);
		final Result result = files.write(user, text(arguments, REPOSITORY), RepositoryFiles.segments(text(arguments, PATH)),
				new ByteArrayInputStream(text(arguments, CONTENT).getBytes(StandardCharsets.UTF_8)), maxFileSize,
				new Precondition(version(arguments), false));
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
					} catch (final RepositoryFiles.MisconfiguredException | RepositoryFiles.NotTextException
							| IllegalArgumentException e) {
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
