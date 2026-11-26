package com.gratchev.mizoine.repositories;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.gratchev.mizoine.files.FileVersion;

import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.MoveResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;
import com.gratchev.mizoine.tags.Tags;
import com.gratchev.mizoine.tags.TagsFile;

/**
 * What a user may do with repository files, the same for every way in: the HTTP file API, the MCP tools for agents.
 * Access follows the grants: read and write inside a granted folder, folders above it only show the way down (D4),
 * hidden names never (D3). Paths are segments below the repository root, already split from a URL or a tool
 * argument and not yet checked: everything is checked here.
 */
public class RepositoryFiles {

	/** Missing, hidden, outside the grants, or an unknown repository: one answer, so nothing is revealed (D2). */
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public static final class NotVisibleException extends RuntimeException {

		NotVisibleException(final String repository, final List<String> segments) {
			super("Not found: " + repository + "/" + String.join("/", segments));
		}
	}

	/** The repository file is readable, but the repository is configured wrongly (D8). */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public static final class MisconfiguredException extends RuntimeException {

		MisconfiguredException(final String repository) {
			super("Repository " + repository + " is configured wrongly; see its repository file");
		}
	}

	/** Readable, but not as text (D21: not UTF-8, or larger than 2 MB). */
	public static final class NotTextException extends RuntimeException {

		NotTextException(final String repository, final List<String> segments) {
			super("Not a text file (UTF-8, up to 2 MB): " + repository + "/" + String.join("/", segments));
		}
	}

	private final RepositoryRegistry registry;
	private final RepositoryWriter writer;
	private final Consumer<RepositoryChange> changes;

	public RepositoryFiles(final RepositoryRegistry registry, final RepositoryWriter writer) {
		this(registry, writer, change -> { });
	}

	/** @param changes told of every write, new folder, delete and move, after it is done (step 56) */
	public RepositoryFiles(final RepositoryRegistry registry, final RepositoryWriter writer,
			final Consumer<RepositoryChange> changes) {
		this.registry = registry;
		this.writer = writer;
		this.changes = changes;
	}

	/** A path below a repository as text ({@code knowledge/ideas.md}, {@code inbox/topic/}, {@code /}) → segments. */
	public static List<String> segments(final String path) {
		return RepositoryUrls.segments(path.startsWith("/") ? path : "/" + path);
	}

	/** Whether a path as text names a folder: it ends with '/' (D5). */
	public static boolean isFolder(final String path) {
		return RepositoryUrls.isFolder(path);
	}

	/** @return names of the repositories the user has a grant in, sorted */
	public List<String> repositories(final String user) {
		return registry.all().stream()
				.filter(config -> RepositoryAccess.of(config, user).hasAccess())
				.map(RepositoryConfig::name)
				.toList();
	}

	public FolderListing listFolder(final String user, final String repository, final List<String> segments) {
		final GrantedRepository granted = usable(user, repository, segments);
		if (!mayEnter(granted.config(), granted.access(), segments)) {
			throw new NotVisibleException(repository, segments);
		}
		return listing(granted.config(), granted.access(), segments);
	}

	/**
	 * Step 43, D33: the root of a repository whose Git page the user may use (grant {@code .git} or {@code /}).
	 * Git always acts on the whole repository, so no path is checked here.
	 */
	public Path gitRoot(final String user, final String repository) {
		final GrantedRepository granted = usable(user, repository, List.of(FolderGrant.GIT));
		if (!granted.access().canUseGit()) {
			throw new NotVisibleException(repository, List.of(FolderGrant.GIT));
		}
		return granted.config().root();
	}

	/**
	 * Step 45: links for the Git page. For a path below the repository root as Git names it ({@code notes/a b.md}),
	 * the URL of the file's page where the user may read it; empty for anything else (no grant, hidden, deleted).
	 */
	public Function<String, Optional<String>> fileLinks(final String user, final String repository) {
		final GrantedRepository granted = usable(user, repository, List.of());
		return path -> readable(granted.config(), granted.access(), segments(path))
				.map(file -> RepositoryUrls.url(repository, segments(path), false));
	}

	/**
	 * Step 57, D54: where a user may search in a repository: the granted folders, inside which everything may be
	 * read. Folders above a grant show only the way down (D4) and are not searched.
	 *
	 * @param folders path segments of the granted folders; an empty list for the whole repository
	 */
	public record SearchArea(String repository, Path root, List<List<String>> folders) {

		public boolean wholeRepository() {
			return folders.stream().anyMatch(List::isEmpty);
		}
	}

	/** @throws NotVisibleException without a grant; MisconfiguredException for a repository with problems */
	public SearchArea searchArea(final String user, final String repository) {
		final GrantedRepository granted = usable(user, repository, List.of());
		return new SearchArea(repository, granted.config().root(), granted.access().readableFolders());
	}

	/**
	 * Step 60, D56: the search area of a user with the whole repository ({@code /}), who may reindex it.
	 *
	 * @throws NotVisibleException for everyone else, as for anything not visible (D2)
	 */
	public SearchArea wholeSearchArea(final String user, final String repository) {
		final SearchArea area = searchArea(user, repository);
		if (!area.wholeRepository()) {
			throw new NotVisibleException(repository, List.of());
		}
		return area;
	}

	/** @return where the user may search in every repository: those with a file grant and without problems */
	public List<SearchArea> searchAreas(final String user) {
		return registry.all().stream()
				.filter(config -> config.problems().isEmpty())
				.map(config -> new SearchArea(config.name(), config.root(), RepositoryAccess.of(config, user).readableFolders()))
				.filter(area -> !area.folders().isEmpty())
				.toList();
	}

	/**
	 * Step 57, D54: a search hit checked again with the rules of reading before it is shown, links followed.
	 *
	 * @return the real path of the file, or of the folder, if the user may read it now
	 */
	public Optional<Path> findable(final String user, final String repository, final List<String> segments,
			final boolean folder) {
		final Optional<GrantedRepository> granted = GrantedRepository.find(registry, repository, user)
				.filter(found -> !found.hasProblems());
		if (granted.isEmpty()) {
			return Optional.empty();
		}
		final RepositoryConfig config = granted.get().config();
		final RepositoryAccess access = granted.get().access();
		return folder ? readableFolder(config, access, segments) : readable(config, access, segments);
	}

	/**
	 * Step 62: a folder's own tags (D64, D68), with the version of its {@code .tags} file, if there is one; also
	 * what a change of them wrote (step 63).
	 *
	 * @param version the ETag of the {@code .tags} file; empty if it does not exist (also after a change to no tags),
	 *                or the folder shows no tags
	 */
	public record FolderTags(Tags tags, Optional<String> version) implements TagsResult {
	}

	/**
	 * @return the tags of a folder the user may read in; none for a folder only on the way to a grant (D4)
	 * @throws NotVisibleException for a folder the user may not enter
	 */
	public FolderTags folderTags(final String user, final String repository, final List<String> segments) {
		final GrantedRepository granted = usable(user, repository, segments);
		if (!mayEnter(granted.config(), granted.access(), segments)) {
			throw new NotVisibleException(repository, segments);
		}
		return readableFolder(granted.config(), granted.access(), segments)
				.map(folder -> new FolderTags(TagsFile.read(folder),
						FileVersion.of(folder.resolve(TagsFile.NAME)).map(FileVersion::etag)))
				.orElse(new FolderTags(Tags.NONE, Optional.empty()));
	}

	/** The URL of a file or folder (folders end with '/', D5), fully encoded. */
	public static String url(final String repository, final List<String> segments, final boolean folder) {
		return RepositoryUrls.url(repository, segments, folder);
	}

	/** The real path of a file the user may read. */
	public Path readableFile(final String user, final String repository, final List<String> segments) {
		final GrantedRepository granted = usable(user, repository, segments);
		return readable(granted.config(), granted.access(), segments)
				.orElseThrow(() -> new NotVisibleException(repository, segments));
	}

	/** A text file's content with the version it was read at (step 42): what an agent needs to write back safely. */
	public record FileText(String content, String version) {
	}

	/**
	 * The version is read before the content: if the file changes in between, the version is the older one, and a
	 * write back with it is refused (412) instead of overwriting the change.
	 */
	public FileText readText(final String user, final String repository, final List<String> segments) {
		final Path file = readableFile(user, repository, segments);
		final String version = FileVersion.of(file).orElseThrow(() -> new NotVisibleException(repository, segments)).etag();
		return TextFile.read(file).map(content -> new FileText(content, version))
				.orElseThrow(() -> new NotTextException(repository, segments));
	}

	public Result write(final String user, final String repository, final List<String> segments, final InputStream body,
			final long maxBytes, final Precondition precondition) {
		final GrantedRepository granted = writable(user, repository, segments);
		final Result result = writer.write(granted.config().root(), segments, body, maxBytes, precondition);
		changed(granted, RepositoryPaths.realSegments(granted.config().root(), segments));
		return result;
	}

	public FolderResult createFolder(final String user, final String repository, final List<String> segments,
			final boolean createOnly) {
		final GrantedRepository granted = writable(user, repository, segments);
		final FolderResult result = writer.createFolder(granted.config().root(), segments, createOnly);
		changed(granted, RepositoryPaths.realSegments(granted.config().root(), segments));
		return result;
	}

	/** @param folder the path names a folder (URL or argument ends with '/'); the entry must be of that kind */
	public DeleteResult delete(final String user, final String repository, final List<String> segments,
			final boolean folder, final Optional<String> ifMatch) {
		final GrantedRepository granted = usable(user, repository, segments);
		final List<String> real = RepositoryPaths.realEntry(granted.config().root(), segments);
		if (isGrantedFolder(granted, segments, real)) {
			return new DeleteResult.IsGrantedFolder();
		}
		checkChangeable(granted, repository, segments, real);
		final DeleteResult result = writer.delete(granted.config().root(), segments, folder, ifMatch);
		changed(granted, real);
		return result;
	}

	/**
	 * Step 41: within one repository (D31); source and destination writable for the user (possibly two grants);
	 * never the root or a granted folder; never overwrites (D30).
	 *
	 * @param folder the paths name a folder (end with '/'); the source must be of that kind
	 */
	public MoveResult move(final String user, final String repository, final List<String> from, final List<String> to,
			final boolean folder, final Optional<String> ifMatch) {
		final GrantedRepository granted = usable(user, repository, from);
		final Path root = granted.config().root();
		final List<String> realFrom = RepositoryPaths.realEntry(root, from);
		if (isGrantedFolder(granted, from, realFrom)) {
			return new MoveResult.IsGrantedFolder();
		}
		checkChangeable(granted, repository, from, realFrom);
		final List<String> realTo = RepositoryPaths.realEntry(root, to);
		checkChangeable(granted, repository, to, realTo);
		final MoveResult result = writer.move(granted.config().root(), from, to, folder, ifMatch);
		changed(granted, realFrom);
		changed(granted, realTo);
		return result;
	}

	/** Also after a refused change: looking again at an unchanged path changes nothing. */
	private void changed(final GrantedRepository granted, final List<String> real) {
		changes.accept(new RepositoryChange(granted.config().name(), granted.config().root(), real));
	}

	/** What changing a folder's tags did (step 63): the new tags ({@link FolderTags}), or nothing changed. */
	public sealed interface TagsResult {

		/** {@code If-Match} names another version, or there is no tags file yet. */
		record PreconditionFailed() implements TagsResult {
		}
	}

	/**
	 * Step 63: replaces a folder's own tags, atomically, in its {@code .tags} file; no tags remove the file. Only
	 * where the user may write in the folder (the root needs the whole repository, D68).
	 *
	 * @throws NotVisibleException elsewhere, as for a file write there (D2)
	 */
	public TagsResult setFolderTags(final String user, final String repository, final List<String> folder,
			final Tags tags, final Optional<String> ifMatch) {
		final List<String> file = child(folder, TagsFile.NAME);
		final GrantedRepository granted = writable(user, repository, file);
		final Path root = granted.config().root();
		if (RepositoryPaths.resolveFolder(root, folder).isEmpty()) {
			throw new NotVisibleException(repository, folder);
		}
		final TagsResult result = tags.isEmpty() ? removeTags(root, folder, ifMatch) : writeTags(repository, root, folder, tags, ifMatch);
		changed(granted, RepositoryPaths.realSegments(root, file));
		return result;
	}

	private TagsResult removeTags(final Path root, final List<String> folder, final Optional<String> ifMatch) {
		return writer.deleteTags(root, folder, ifMatch) instanceof DeleteResult.PreconditionFailed
				? new TagsResult.PreconditionFailed()
				: new FolderTags(Tags.NONE, Optional.empty());
	}

	private TagsResult writeTags(final String repository, final Path root, final List<String> folder, final Tags tags,
			final Optional<String> ifMatch) {
		return switch (writer.writeTags(root, folder, tags.format(), new Precondition(ifMatch, false))) {
			case Result.Created created -> new FolderTags(tags, Optional.of(created.etag()));
			case Result.Replaced replaced -> new FolderTags(tags, Optional.of(replaced.etag()));
			case Result.PreconditionFailed failed -> new TagsResult.PreconditionFailed();
			// a folder named .tags, or a link in its place: no tags file of this folder
			default -> throw new NotVisibleException(repository, folder);
		};
	}

	/**
	 * The subfolders and files the user may see in a folder already checked, with the tags of the folder and of its
	 * subfolders where the user may read inside them (step 62).
	 */
	static FolderListing listing(final RepositoryConfig config, final RepositoryAccess access, final List<String> segments) {
		final Path root = config.root();
		final String path = segments.isEmpty() ? "/" : "/" + String.join("/", segments) + "/";
		final List<String> folders = access.visibleChildren(segments, RepositoryPaths.childFolders(root, segments)).stream()
				.filter(name -> mayEnter(config, access, child(segments, name))).toList();
		final Map<String, List<String>> folderTags = new LinkedHashMap<>();
		folders.forEach(name -> tags(config, access, child(segments, name))
				.filter(tags -> !tags.isEmpty()).ifPresent(tags -> folderTags.put(name, tags.texts())));
		return new FolderListing(config.name(), path, folders,
				access.visibleFiles(segments, RepositoryPaths.childFiles(root, segments)).stream()
						.filter(name -> readable(config, access, child(segments, name)).isPresent()).toList(),
				tags(config, access, segments).map(Tags::texts).orElse(List.of()), folderTags);
	}

	private static Optional<Tags> tags(final RepositoryConfig config, final RepositoryAccess access,
			final List<String> folder) {
		return readableFolder(config, access, folder).map(TagsFile::read);
	}

	/** An existing folder the user may read in: inside a grant, also where it really is (links followed). */
	static Optional<Path> readableFolder(final RepositoryConfig config, final RepositoryAccess access,
			final List<String> segments) {
		return RepositoryPaths.resolveFolder(config.root(), segments)
				.filter(found -> holds(access::canReadIn, segments, RepositoryPaths.realSegments(config.root(), segments)));
	}

	/*
	 * The access rule for every operation: the grants hold for the requested path and for the place it really
	 * touches. Without the second check a link in a granted folder would lead to any folder of the repository.
	 * Reading and writing follow every link (a file is written through a link to it); deleting and moving act on
	 * the entry itself, a link stays a link (RepositoryWriter).
	 */

	/** An existing folder the user may enter. */
	static boolean mayEnter(final RepositoryConfig config, final RepositoryAccess access, final List<String> segments) {
		return RepositoryPaths.resolveFolder(config.root(), segments).isPresent()
				&& holds(access::canEnter, segments, RepositoryPaths.realSegments(config.root(), segments));
	}

	/** The real path of an existing file the user may read. */
	static Optional<Path> readable(final RepositoryConfig config, final RepositoryAccess access, final List<String> segments) {
		return Optional.of(segments)
				.filter(file -> holds(access::canReadFile, file, RepositoryPaths.realSegments(config.root(), file)))
				.flatMap(file -> RepositoryPaths.resolveFile(config.root(), file));
	}

	private static boolean holds(final Predicate<List<String>> rule, final List<String> requested, final List<String> real) {
		return rule.test(requested) && rule.test(real);
	}

	/** The root or the top of a grant, also when reached through a link: never deleted or moved (step 32). */
	private static boolean isGrantedFolder(final GrantedRepository granted, final List<String> requested,
			final List<String> real) {
		return granted.access().isGrantedFolder(requested) || granted.access().isGrantedFolder(real);
	}

	private static List<String> child(final List<String> segments, final String name) {
		return Stream.concat(segments.stream(), Stream.of(name)).toList();
	}

	private GrantedRepository usable(final String user, final String repository, final List<String> segments) {
		final GrantedRepository granted = GrantedRepository.find(registry, repository, user)
				.orElseThrow(() -> new NotVisibleException(repository, segments));
		if (granted.hasProblems()) {
			throw new MisconfiguredException(repository);
		}
		return granted;
	}

	/**
	 * Whether the user may write at this path, before anything is read: an upload already known to be too large
	 * must still get "not found" where the user may not write, not "too large" (D2).
	 */
	public void checkWritable(final String user, final String repository, final List<String> segments) {
		writable(user, repository, segments);
	}

	/** Only inside a granted folder, never hidden; everything else is the same "not found" (D2, D4). */
	private GrantedRepository writable(final String user, final String repository, final List<String> segments) {
		final GrantedRepository granted = usable(user, repository, segments);
		checkChangeable(granted, repository, segments, RepositoryPaths.realSegments(granted.config().root(), segments));
		return granted;
	}

	private static void checkChangeable(final GrantedRepository granted, final String repository,
			final List<String> requested, final List<String> real) {
		if (!holds(granted.access()::canChange, requested, real)) {
			throw new NotVisibleException(repository, requested);
		}
	}
}
