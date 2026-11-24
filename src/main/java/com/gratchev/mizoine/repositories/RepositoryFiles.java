package com.gratchev.mizoine.repositories;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;

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

	public RepositoryFiles(final RepositoryRegistry registry, final RepositoryWriter writer) {
		this.registry = registry;
		this.writer = writer;
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
		if (!granted.access().canEnter(segments) || RepositoryPaths.resolveFolder(granted.config().root(), segments).isEmpty()) {
			throw new NotVisibleException(repository, segments);
		}
		return listing(granted.config(), granted.access(), segments);
	}

	/** The real path of a file the user may read. */
	public Path readableFile(final String user, final String repository, final List<String> segments) {
		final GrantedRepository granted = usable(user, repository, segments);
		return Optional.of(segments).filter(granted.access()::canReadFile)
				.flatMap(file -> RepositoryPaths.resolveFile(granted.config().root(), file))
				.orElseThrow(() -> new NotVisibleException(repository, segments));
	}

	public String readText(final String user, final String repository, final List<String> segments) {
		return TextFile.read(readableFile(user, repository, segments))
				.orElseThrow(() -> new NotTextException(repository, segments));
	}

	public Result write(final String user, final String repository, final List<String> segments, final InputStream body,
			final long maxBytes, final Precondition precondition) {
		final GrantedRepository granted = writable(user, repository, segments);
		return writer.write(granted.config().root(), segments, body, maxBytes, precondition);
	}

	public FolderResult createFolder(final String user, final String repository, final List<String> segments,
			final boolean createOnly) {
		final GrantedRepository granted = writable(user, repository, segments);
		return writer.createFolder(granted.config().root(), segments, createOnly);
	}

	/** @param folder the path names a folder (URL or argument ends with '/'); the entry must be of that kind */
	public DeleteResult delete(final String user, final String repository, final List<String> segments,
			final boolean folder, final Optional<String> ifMatch) {
		final GrantedRepository granted = usable(user, repository, segments);
		if (granted.access().isGrantedFolder(segments)) {
			return new DeleteResult.IsGrantedFolder();
		}
		checkWritable(granted, repository, segments);
		return writer.delete(granted.config().root(), segments, folder, ifMatch);
	}

	/** The subfolders and files the user may see in a folder already checked. */
	static FolderListing listing(final RepositoryConfig config, final RepositoryAccess access, final List<String> segments) {
		final Path root = config.root();
		final String path = segments.isEmpty() ? "/" : "/" + String.join("/", segments) + "/";
		return new FolderListing(config.name(), path,
				access.visibleChildren(segments, RepositoryPaths.childFolders(root, segments)),
				access.visibleFiles(segments, RepositoryPaths.childFiles(root, segments)));
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
		checkWritable(granted, repository, segments);
		return granted;
	}

	private static void checkWritable(final GrantedRepository granted, final String repository, final List<String> segments) {
		if (segments.isEmpty() || !granted.access().canWriteIn(segments.subList(0, segments.size() - 1))) {
			throw new NotVisibleException(repository, segments);
		}
	}
}
