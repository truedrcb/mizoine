package com.gratchev.mizoine.repositories;

import java.util.Arrays;
import java.util.List;

/**
 * A folder of a repository that a user may access, including everything below it.
 * Stored as path segments relative to the repository root; no segments = the whole repository.
 */
public record FolderGrant(List<String> segments) {

	public static final FolderGrant WHOLE_REPOSITORY = new FolderGrant(List.of());
	/** The name of the Git grant and of the Git page's address (step 43). */
	public static final String GIT = ".git";

	public FolderGrant {
		segments = List.copyOf(segments);
	}

	/**
	 * @param folder relative to the repository root, separated by {@code /}; leading, trailing and
	 *            double slashes are ignored; {@code "/"} is the whole repository
	 * @throws IllegalArgumentException for empty text, backslashes, NUL, {@code .}, {@code ..}
	 *             or hidden names ({@link FolderNames}: starting with {@code .} or containing {@code %})
	 */
	public static FolderGrant of(final String folder) {
		if (folder.isBlank()) {
			throw new IllegalArgumentException("empty folder, use \"/\" for the whole repository");
		}
		if (folder.indexOf('\\') >= 0 || folder.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("folder '" + folder + "' must use '/' as separator");
		}
		final List<String> segments = Arrays.stream(folder.split("/")).filter(segment -> !segment.isEmpty()).toList();
		for (final String segment : segments) {
			if (FolderNames.isHidden(segment)) {
				throw new IllegalArgumentException("folder '" + folder
						+ "' must not contain '.', '..' or hidden names (starting with '.' or containing '%')");
			}
		}
		return new FolderGrant(segments);
	}

	/**
	 * @return the text is the grant {@code .git} at the top ({@code .git}, {@code /.git}, {@code .git/}): Git access
	 *         to the repository (step 43), not a folder
	 */
	public static boolean isGit(final String folder) {
		return Arrays.stream(folder.split("/")).filter(segment -> !segment.isEmpty()).toList().equals(List.of(GIT));
	}

	public boolean isWholeRepository() {
		return segments.isEmpty();
	}
}
