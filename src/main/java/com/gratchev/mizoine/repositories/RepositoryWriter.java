package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

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
	private static final String TEMPORARY_PREFIX = ".mizoine-upload-";
	private static final int BUFFER = 64 * 1024;

	/** {@code If-Match} (an ETag) and/or {@code If-None-Match: *} (create only). */
	public record Precondition(Optional<String> ifMatch, boolean createOnly) {

		public static final Precondition NONE = new Precondition(Optional.empty(), false);
		public static final Precondition CREATE_ONLY = new Precondition(Optional.empty(), true);

		public static Precondition ifMatch(final String etag) {
			return new Precondition(Optional.of(etag), false);
		}

		boolean isMetBy(final Optional<String> currentEtag) {
			return !(createOnly && currentEtag.isPresent())
					&& ifMatch.map(expected -> currentEtag.map(etag -> FileDownload.isSameVersion(expected, etag)).orElse(false))
							.orElse(true);
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
	}

	private final Lock lock = new ReentrantLock();

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
		final Optional<Path> parent = parentOf(root, segments);
		final Optional<Path> visible = folder ? RepositoryPaths.resolveFolder(root, segments) : RepositoryPaths.resolveFile(root, segments);
		if (parent.isEmpty() || visible.isEmpty()) {
			return new DeleteResult.NotFound();
		}
		final Path path = parent.get().resolve(segments.getLast());
		lock.lock();
		try {
			if (folder && !Files.isSymbolicLink(path) && hasEntries(path)) {
				return new DeleteResult.NotEmpty();
			}
			if (!folder && ifMatch.isPresent()
					&& !etag(visible.get()).map(etag -> FileDownload.isSameVersion(ifMatch.get(), etag)).orElse(false)) {
				return new DeleteResult.PreconditionFailed();
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
			return entries.findAny().isPresent();
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
				final Optional<String> current = etag(target);
				if (!precondition.isMetBy(current)) {
					return new Result.PreconditionFailed();
				}
				current.ifPresent(etag -> keepPermissions(target, temporary));
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				final String etag = etag(target).orElseThrow();
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

	private static Optional<String> etag(final Path file) throws IOException {
		return Files.isRegularFile(file)
				? Optional.of(FileDownload.etag(Files.readAttributes(file, BasicFileAttributes.class)))
				: Optional.empty();
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
