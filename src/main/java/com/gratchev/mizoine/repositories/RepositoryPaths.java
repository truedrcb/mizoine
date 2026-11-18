package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Turns folder segments (from a URL) into real paths below a repository root — never outside it.
 * <ul>
 * <li>Segments must be plain names: no {@code .}, {@code ..}, empty names, separators or NUL.</li>
 * <li>Hidden names (starting with {@code .}, such as {@code .git}) are never reachable (D3).</li>
 * <li>Symbolic links are resolved; a link whose target is outside the root, or hidden, counts as not existing.</li>
 * </ul>
 */
public final class RepositoryPaths {

	private RepositoryPaths() {
	}

	/** @return the real path of an existing folder inside the root, or empty */
	public static Optional<Path> resolveFolder(final Path root, final List<String> segments) {
		if (!segments.stream().allMatch(RepositoryPaths::isPlainName)) {
			return Optional.empty();
		}
		try {
			final Path realRoot = root.toRealPath();
			Path path = realRoot;
			for (final String segment : segments) {
				path = path.resolve(segment);
			}
			return safeFolder(realRoot, path);
		} catch (final IOException e) {
			return Optional.empty();
		}
	}

	/** @return names of the visible subfolders, sorted; empty if the folder is not reachable */
	public static List<String> childFolders(final Path root, final List<String> segments) {
		final Optional<Path> folder = resolveFolder(root, segments);
		if (folder.isEmpty()) {
			return List.of();
		}
		try (Stream<Path> entries = Files.list(folder.get())) {
			final Path realRoot = root.toRealPath();
			return entries
					.filter(entry -> isPlainName(entry.getFileName().toString()))
					.filter(entry -> safeFolder(realRoot, entry).isPresent())
					.map(entry -> entry.getFileName().toString())
					.sorted()
					.toList();
		} catch (final IOException e) {
			return List.of();
		}
	}

	private static Optional<Path> safeFolder(final Path realRoot, final Path path) {
		try {
			final Path real = path.toRealPath();
			if (!real.startsWith(realRoot) || !Files.isDirectory(real)) {
				return Optional.empty();
			}
			// the real target must not be hidden either (e.g. a link "git" -> ".git")
			for (final Path name : realRoot.relativize(real)) {
				if (!name.toString().isEmpty() && !isPlainName(name.toString())) {
					return Optional.empty();
				}
			}
			return Optional.of(real);
		} catch (final IOException e) {
			return Optional.empty();
		}
	}

	private static boolean isPlainName(final String name) {
		return !name.isEmpty()
				&& !name.startsWith(".")
				&& name.indexOf('/') < 0
				&& name.indexOf('\\') < 0
				&& name.indexOf('\0') < 0;
	}
}
