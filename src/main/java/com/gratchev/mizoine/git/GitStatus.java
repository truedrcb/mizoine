package com.gratchev.mizoine.git;

import java.util.List;
import java.util.Optional;

/**
 * The Git status of a repository (step 44): what {@code git status} says, as data.
 *
 * @param branch the current branch; empty for a detached HEAD
 * @param hasCommits false before the first commit
 * @param upstream the branch it pulls from and pushes to, if set
 * @param changes in Git's order; paths relative to the repository root, with '/'
 */
public record GitStatus(Optional<String> branch, boolean hasCommits, Optional<Upstream> upstream, List<Change> changes) {

	public GitStatus {
		changes = List.copyOf(changes);
	}

	/** @param gone the upstream branch no longer exists: ahead and behind are unknown (0) */
	public record Upstream(String name, boolean gone, int ahead, int behind) {
	}

	/**
	 * One changed file.
	 *
	 * @param renamedFrom the old path of a renamed or copied file
	 * @param staged the change in the index (what the next commit takes)
	 * @param unstaged the change in the working tree, not staged yet
	 */
	public record Change(String path, Optional<String> renamedFrom, Kind staged, Kind unstaged) {
	}

	public enum Kind {
		UNCHANGED, MODIFIED, TYPE_CHANGED, ADDED, DELETED, RENAMED, COPIED, UNTRACKED, CONFLICT
	}
}
