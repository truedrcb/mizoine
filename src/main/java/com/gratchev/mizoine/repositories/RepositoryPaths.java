package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Turns folder segments (from a URL) into real paths below a repository root — never outside it — and lists
 * the subfolders and files there by the same rules.
 * <ul>
 * <li>Segments must be plain names ({@link FolderNames}): no hidden names (D3), {@code .}, {@code ..},
 * empty names, separators or NUL.</li>
 * <li>Symbolic links are resolved; a link whose target is outside the root, or hidden, counts as not existing.</li>
 * <li>Files are regular files only (no devices, sockets, ...).</li>
 * </ul>
 */
public final class RepositoryPaths {

	private RepositoryPaths() {
	}

	/** @return the real path of an existing folder inside the root, or empty */
	public static Optional<Path> resolveFolder(final Path root, final List<String> segments) {
		if (!segments.stream().allMatch(FolderNames::isPlain)) {
			return Optional.empty();
		}
		try {
			final Path realRoot = root.toRealPath();
			Path path = realRoot;
			for (final String segment : segments) {
				path = path.resolve(segment);
			}
			return safe(realRoot, path, Files::isDirectory);
		} catch (final IOException e) {
			return Optional.empty();
		}
	}

	/** @return names of the visible subfolders, sorted; empty if the folder is not reachable */
	public static List<String> childFolders(final Path root, final List<String> segments) {
		return children(root, segments, Files::isDirectory);
	}

	/** @return names of the visible regular files, sorted; empty if the folder is not reachable */
	public static List<String> childFiles(final Path root, final List<String> segments) {
		return children(root, segments, Files::isRegularFile);
	}

	private static List<String> children(final Path root, final List<String> segments, final Predicate<Path> kind) {
		final Optional<Path> folder = resolveFolder(root, segments);
		if (folder.isEmpty()) {
			return List.of();
		}
		try (Stream<Path> entries = Files.list(folder.get())) {
			final Path realRoot = root.toRealPath();
			return entries
					.filter(entry -> FolderNames.isPlain(entry.getFileName().toString()))
					.filter(entry -> safe(realRoot, entry, kind).isPresent())
					.map(entry -> entry.getFileName().toString())
					.sorted()
					.toList();
		} catch (final IOException e) {
			return List.of();
		}
	}

	/** @return the real path, if it is inside the root, not hidden and of the given kind (folder or file) */
	private static Optional<Path> safe(final Path realRoot, final Path path, final Predicate<Path> kind) {
		try {
			final Path real = path.toRealPath();
			if (!real.startsWith(realRoot) || !kind.test(real)) {
				return Optional.empty();
			}
			// the real target must not be hidden either (e.g. a link "git" -> ".git")
			for (final Path name : realRoot.relativize(real)) {
				if (!name.toString().isEmpty() && !FolderNames.isPlain(name.toString())) {
					return Optional.empty();
				}
			}
			return Optional.of(real);
		} catch (final IOException e) {
			return Optional.empty();
		}
	}

}
