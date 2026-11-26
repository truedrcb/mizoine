package com.gratchev.mizoine.repositories;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.gratchev.mizoine.files.FileVersion;
import com.gratchev.mizoine.tags.TagsFile;

/**
 * Writes repository files (step 30). Paths are checked like reading ({@link RepositoryPaths}); access (grants)
 * is checked by the caller.
 * <ul>
 * <li>Atomic: the body goes into a hidden temporary file next to the target, which is then moved over it in one
 * step. Readers and Git see the old or the new content, never a part. Nothing is left behind after errors.</li>
 * <li>Size-limited while streaming: the {@code Content-Length} alone cannot be trusted.</li>
 * <li>Preconditions ({@code If-Match}, {@code If-None-Match: *}, D13) are checked together with the move, under
 * one lock: one writer at a time, so two clients cannot both win with the same ETag. (Editors outside Mizoine
 * cannot be locked; the ETag check still sees their changes.)</li>
 * <li>Folders are never created implicitly (D14). A symbolic link to a visible file inside the root is written
 * through to that file.</li>
 * </ul>
 */
public class RepositoryWriter {

	/** Starts with '.': hidden (D3), never listed or reachable over HTTP. */
	public static final String TEMPORARY_PREFIX = ".mizoine-upload-";
	private static final int BUFFER = 64 * 1024;

	/** {@code If-Match} (an ETag) and/or {@code If-None-Match: *} (create only). */
	public record Precondition(Optional<String> ifMatch, boolean createOnly) {

		public static final Precondition NONE = new Precondition(Optional.empty(), false);
		public static final Precondition CREATE_ONLY = new Precondition(Optional.empty(), true);

		public static Precondition ifMatch(final String etag) {
			return new Precondition(Optional.of(etag), false);
		}

		boolean isMetBy(final Optional<FileVersion> current) {
			return !(createOnly && current.isPresent())
					&& ifMatch.map(expected -> current.map(version -> version.isNamedBy(expected)).orElse(false)).orElse(true);
		}
	}

	/** Exactly one outcome per write. */
	public sealed interface Result {

		record Created(String etag) implements Result {
		}

		record Replaced(String etag) implements Result {
		}

		record PreconditionFailed() implements Result {
		}

		/** The folder to write into does not exist (D14: no implicit folders). */
		record ParentMissing() implements Result {
		}

		record IsFolder() implements Result {
		}

		/** Hidden, outside the root, or not a plain name: the same as not existing (D2, D3). */
		record NotFound() implements Result {
		}

		record TooLarge() implements Result {
		}
	}

	/** Creating a folder (step 31): exactly one outcome. */
	public sealed interface FolderResult {

		record Created() implements FolderResult {
		}

		/** Nothing changes: PUT is idempotent. */
		record Exists() implements FolderResult {
		}

		/** Create only ({@code If-None-Match: *}), but the folder exists. */
		record PreconditionFailed() implements FolderResult {
		}

		record ParentMissing() implements FolderResult {
		}

		record FileInTheWay() implements FolderResult {
		}

		record NotFound() implements FolderResult {
		}
	}

	/** Deleting a file or a folder (step 32): exactly one outcome. */
	public sealed interface DeleteResult {

		record Deleted() implements DeleteResult {
		}

		/** A folder with content, visible or hidden, is never deleted (D15). */
		record NotEmpty() implements DeleteResult {
		}

		record PreconditionFailed() implements DeleteResult {
		}

		/** Missing, hidden, outside the root, or not of the kind the URL names (folder or file, D5). */
		record NotFound() implements DeleteResult {
		}

		/** The repository root or one of the user's granted folders: not deleted (step 32). Decided by access. */
		record IsGrantedFolder() implements DeleteResult {
		}
	}

	/** Moving or renaming a file or folder (step 41): exactly one outcome. */
	public sealed interface MoveResult {

		record Moved() implements MoveResult {
		}

		/** Never overwritten (D30): the client deletes it first if wanted. */
		record DestinationExists() implements MoveResult {
		}

		/** The destination's folder does not exist (D14: no implicit folders). */
		record DestinationParentMissing() implements MoveResult {
		}

		record IntoItself() implements MoveResult {
		}

		/** {@code If-Match} on a file not met: changed since the client read it. */
		record PreconditionFailed() implements MoveResult {
		}

		/** Source and destination are on different file systems (e.g. a mount inside the repository). */
		record NotAtomic() implements MoveResult {
		}

		/** Source or destination missing, hidden, outside the root, or not of the kind the path names (D5). */
		record NotFound() implements MoveResult {
		}

		/** The repository root or one of the user's granted folders: not moved. Decided by access. */
		record IsGrantedFolder() implements MoveResult {
		}
	}

	private final Lock lock = new ReentrantLock();

	/**
	 * Runs the action while no write of Mizoine takes its final step (the replace, under the same lock): a Git pull
	 * (step 48) rewrites files, and an upload must not replace one in the middle of it. Uploads still read their
	 * body meanwhile; only their last step waits.
	 */
	public <T> T exclusively(final Supplier<T> action) {
		lock.lock();
		try {
			return action.get();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * @throws UncheckedIOException if reading the body or writing the disk fails; the old content stays
	 */
	public Result write(final Path root, final List<String> segments, final InputStream body, final long maxBytes,
			final Precondition precondition) {
		final Optional<Path> parent = parentOf(root, segments);
		if (parent.isEmpty()) {
			return isMissingParent(root, segments) ? new Result.ParentMissing() : new Result.NotFound();
		}
		final Path path = parent.get().resolve(segments.getLast());
		if (Files.isDirectory(path)) {
			return new Result.IsFolder();
		}
		final Optional<Path> target = Files.isSymbolicLink(path) ? RepositoryPaths.resolveFile(root, segments) : Optional.of(path);
		return target.map(file -> writeFile(file, body, maxBytes, precondition)).orElseGet(Result.NotFound::new);
	}

	/**
	 * Step 63: a folder's {@code .tags} file, written as any file (atomically, with the precondition). The one hidden
	 * name Mizoine writes, and only here: {@link #write} refuses every hidden name.
	 *
	 * @param folder an existing folder, checked by the caller
	 */
	public Result writeTags(final Path root, final List<String> folder, final String tags, final Precondition precondition) {
		final Optional<Path> target = RepositoryPaths.resolveFolder(root, folder).map(found -> found.resolve(TagsFile.NAME))
				.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file));
		return target.map(file -> writeFile(file, new ByteArrayInputStream(tags.getBytes(StandardCharsets.UTF_8)),
				Long.MAX_VALUE, precondition)).orElseGet(Result.NotFound::new);
	}

	/** Step 63: removes a folder's {@code .tags} file, with {@code If-Match} only at that version. */
	public DeleteResult deleteTags(final Path root, final List<String> folder, final Optional<String> ifMatch) {
		final Optional<Path> file = RepositoryPaths.resolveFolder(root, folder).map(found -> found.resolve(TagsFile.NAME))
				.filter(found -> Files.isRegularFile(found, LinkOption.NOFOLLOW_LINKS));
		if (file.isEmpty()) {
			return new DeleteResult.NotFound();
		}
		lock.lock();
		try {
			final Optional<DeleteResult> refused = refuseFile(file.get(), ifMatch);
			if (refused.isPresent()) {
				return refused.get();
			}
			Files.deleteIfExists(file.get());
			return new DeleteResult.Deleted();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			lock.unlock();
		}
	}

	/** @param createOnly {@code If-None-Match: *}: an existing folder is a failed precondition */
	public FolderResult createFolder(final Path root, final List<String> segments, final boolean createOnly) {
		final Optional<Path> parent = parentOf(root, segments);
		if (parent.isEmpty()) {
			return isMissingParent(root, segments) ? new FolderResult.ParentMissing() : new FolderResult.NotFound();
		}
		final Path path = parent.get().resolve(segments.getLast());
		lock.lock();
		try {
			if (RepositoryPaths.resolveFolder(root, segments).isPresent()) {
				return createOnly ? new FolderResult.PreconditionFailed() : new FolderResult.Exists();
			}
			if (RepositoryPaths.resolveFile(root, segments).isPresent()) {
				return new FolderResult.FileInTheWay();
			}
			if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
				// a link leading outside the root or to something hidden
				return new FolderResult.NotFound();
			}
			Files.createDirectory(path);
			return new FolderResult.Created();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Deletes the entry itself; a symbolic link is removed, never its target.
	 *
	 * @param folder the URL names a folder (ends with '/'); the entry must be of that kind
	 * @param ifMatch for files: the ETag the file must still have
	 */
	public DeleteResult delete(final Path root, final List<String> segments, final boolean folder,
			final Optional<String> ifMatch) {
		final Optional<Path> entry = entry(root, segments, folder);
		if (entry.isEmpty()) {
			return new DeleteResult.NotFound();
		}
		final Path path = entry.get();
		lock.lock();
		try {
			// the version of a link is that of its file
			final Optional<DeleteResult> refused = folder ? refuseFolder(path) : refuseFile(path, ifMatch);
			if (refused.isPresent()) {
				return refused.get();
			}
			if (folder && !Files.isSymbolicLink(path)) {
				// D70: a folder's own tags go with it
				Files.deleteIfExists(path.resolve(TagsFile.NAME));
			}
			Files.delete(path);
			return new DeleteResult.Deleted();
		} catch (final DirectoryNotEmptyException e) {
			return new DeleteResult.NotEmpty();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * D15: a folder with content, visible or hidden, is not deleted; a link to a folder is just a link. Its own tags
	 * are no content (D70): otherwise a tagged folder could never be deleted through Mizoine.
	 */
	private static Optional<DeleteResult> refuseFolder(final Path folder) throws IOException {
		return !Files.isSymbolicLink(folder) && hasEntries(folder) ? Optional.of(new DeleteResult.NotEmpty()) : Optional.empty();
	}

	/** D13: with {@code If-Match}, only the version the client knows is deleted. */
	private static Optional<DeleteResult> refuseFile(final Path file, final Optional<String> ifMatch) {
		return ifMatch.filter(expected -> !isVersion(file, expected)).map(expected -> new DeleteResult.PreconditionFailed());
	}

	/**
	 * Moves the entry itself in one atomic step; a symbolic link moves as a link. Never overwrites, never creates
	 * folders. Grants are checked by the caller.
	 *
	 * @param folder the paths name a folder; the source must be of that kind
	 * @param ifMatch for files: the version the file must still have
	 */
	public MoveResult move(final Path root, final List<String> from, final List<String> to, final boolean folder,
			final Optional<String> ifMatch) {
		final Optional<Path> source = entry(root, from, folder);
		if (source.isEmpty()) {
			return new MoveResult.NotFound();
		}
		if (folder && isInside(to, from)) {
			return new MoveResult.IntoItself();
		}
		final Optional<Path> targetParent = parentOf(root, to);
		if (targetParent.isEmpty()) {
			return isMissingParent(root, to) ? new MoveResult.DestinationParentMissing() : new MoveResult.NotFound();
		}
		return moveAtomically(source.get(), targetParent.get().resolve(to.getLast()),
				folder ? Optional.empty() : ifMatch.filter(expected -> !isVersion(source.get(), expected)));
	}

	/** @return the entry itself (a link stays a link) of a visible folder or file of that kind, or empty */
	private static Optional<Path> entry(final Path root, final List<String> segments, final boolean folder) {
		final Optional<Path> visible = folder ? RepositoryPaths.resolveFolder(root, segments) : RepositoryPaths.resolveFile(root, segments);
		return visible.flatMap(real -> parentOf(root, segments)).map(parent -> parent.resolve(segments.getLast()));
	}

	/** @return {@code inner} lies below {@code outer} */
	private static boolean isInside(final List<String> inner, final List<String> outer) {
		return inner.size() > outer.size() && inner.subList(0, outer.size()).equals(outer);
	}

	/** @param staleVersion present if the client's version of the source file no longer fits */
	private MoveResult moveAtomically(final Path source, final Path target, final Optional<String> staleVersion) {
		lock.lock();
		try {
			if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
				return new MoveResult.DestinationExists();
			}
			if (staleVersion.isPresent()) {
				return new MoveResult.PreconditionFailed();
			}
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
			return new MoveResult.Moved();
		} catch (final AtomicMoveNotSupportedException e) {
			return new MoveResult.NotAtomic();
		} catch (final FileAlreadyExistsException e) {
			return new MoveResult.DestinationExists();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			lock.unlock();
		}
	}

	/** D13: whether a file is still at the version a client knows (its ETag, weak or strong). */
	private static boolean isVersion(final Path file, final String expected) {
		return FileVersion.of(file).map(version -> version.isNamedBy(expected)).orElse(false);
	}

	/** @return the real parent folder of a plain, non-empty path, or empty */
	private static Optional<Path> parentOf(final Path root, final List<String> segments) {
		if (segments.isEmpty() || !segments.stream().allMatch(FolderNames::isPlain)) {
			return Optional.empty();
		}
		return RepositoryPaths.resolveFolder(root, segments.subList(0, segments.size() - 1));
	}

	/** The parent folder does not exist at all, so the path is not hidden or outside, just missing (D14 → 409). */
	private static boolean isMissingParent(final Path root, final List<String> segments) {
		return !segments.isEmpty() && segments.stream().allMatch(FolderNames::isPlain)
				&& Files.notExists(root.resolve(String.join("/", segments.subList(0, segments.size() - 1))));
	}

	private static boolean hasEntries(final Path folder) throws IOException {
		try (Stream<Path> entries = Files.list(folder)) {
			return entries.anyMatch(entry -> !entry.getFileName().toString().equals(TagsFile.NAME)
					|| !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS));
		}
	}

	private Result writeFile(final Path target, final InputStream body, final long maxBytes,
			final Precondition precondition) {
		final Path temporary = target.resolveSibling(TEMPORARY_PREFIX + UUID.randomUUID());
		try {
			Files.createFile(temporary);
			if (!copy(body, temporary, maxBytes)) {
				return new Result.TooLarge();
			}
			lock.lock();
			try {
				final Optional<FileVersion> current = FileVersion.of(target);
				if (!precondition.isMetBy(current)) {
					return new Result.PreconditionFailed();
				}
				current.ifPresent(etag -> keepPermissions(target, temporary));
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				final String etag = FileVersion.of(target).orElseThrow().etag();
				return current.isPresent() ? new Result.Replaced(etag) : new Result.Created(etag);
			} finally {
				lock.unlock();
			}
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteQuietly(temporary);
		}
	}

	/** @return false if the body is longer than {@code maxBytes} */
	private static boolean copy(final InputStream body, final Path file, final long maxBytes) throws IOException {
		final byte[] buffer = new byte[BUFFER];
		long total = 0;
		try (OutputStream out = Files.newOutputStream(file)) {
			for (int read = body.read(buffer); read >= 0; read = body.read(buffer)) {
				total += read;
				if (total > maxBytes) {
					return false;
				}
				out.write(buffer, 0, read);
			}
		}
		return true;
	}

	/** A replaced file keeps its permissions (e.g. readable for the group), where the file system has them. */
	private static void keepPermissions(final Path from, final Path to) {
		final PosixFileAttributeView source = Files.getFileAttributeView(from, PosixFileAttributeView.class);
		if (source != null) {
			try {
				Files.setPosixFilePermissions(to, source.readAttributes().permissions());
			} catch (final IOException e) {
				// not essential: the content is what counts
			}
		}
	}

	private static void deleteQuietly(final Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (final IOException e) {
			// a hidden leftover; it is never listed
		}
	}
}
