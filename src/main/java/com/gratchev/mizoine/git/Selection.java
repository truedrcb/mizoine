package com.gratchev.mizoine.git;

import java.util.List;

/**
 * Which changes to stage or unstage (step 46): all of them, or the named paths (as Git names them, from the
 * repository root). Checked where it comes in: a selection always names something.
 */
public record Selection(List<String> paths, boolean all) {

	public static final Selection ALL = new Selection(List.of(), true);

	/** @throws IllegalArgumentException if it names nothing, or a blank path; the message says so for the page */
	public Selection {
		// JSON {"all": true} has no paths
		paths = paths == null ? List.of() : paths;
		if (!all && paths.isEmpty()) {
			throw new IllegalArgumentException("Choose files, or all.");
		}
		if (paths.stream().anyMatch(path -> path == null || path.isBlank())) {
			throw new IllegalArgumentException("A file name is empty.");
		}
		paths = paths.stream().distinct().toList();
	}

	public static Selection of(final List<String> paths) {
		return new Selection(paths, false);
	}
}
