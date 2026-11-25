package com.gratchev.mizoine.git;

import java.util.List;
import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Git status of a repository (step 44): what {@code git status} says, as data.
 *
 * @param branch the current branch; empty for a detached HEAD
 * @param hasCommits false before the first commit
 * @param upstream the branch it pulls from and pushes to, if set
 * @param changes in Git's order; paths relative to the repository root, with '/'
 */
@Schema(description = "The Git status of a repository, as 'git status' gives it")
public record GitStatus(
		@Schema(description = "The current branch; null for a detached HEAD", nullable = true) Optional<String> branch,
		@Schema(description = "false before the first commit") boolean hasCommits,
		@Schema(description = "The branch it pulls from and pushes to; null if none is set")
		Optional<Upstream> upstream,
		@Schema(description = "The changed files in Git's order; paths from the repository root, with '/'")
		List<Change> changes) {

	public GitStatus {
		changes = List.copyOf(changes);
	}

	/** @return what the next commit takes: changed in the index */
	public List<Change> staged() {
		return changes.stream().filter(change -> change.staged() != Kind.UNCHANGED && change.staged() != Kind.CONFLICT).toList();
	}

	/** @return changed or new in the working tree, not staged yet */
	public List<Change> unstaged() {
		return changes.stream().filter(change -> change.unstaged() != Kind.UNCHANGED && change.unstaged() != Kind.CONFLICT)
				.toList();
	}

	/** @return files in conflict after a merge: to be resolved by hand on the server (D37) */
	public List<Change> conflicts() {
		return changes.stream().filter(change -> change.staged() == Kind.CONFLICT).toList();
	}

	/** @return nothing to commit and nothing changed */
	@Schema(description = "Nothing to commit and nothing changed")
	public boolean isClean() {
		return changes.isEmpty();
	}

	/** @param gone the upstream branch no longer exists: ahead and behind are unknown (0) */
	public record Upstream(
			@Schema(description = "e.g. origin/main") String name,
			@Schema(description = "The upstream branch no longer exists; ahead and behind are then 0") boolean gone,
			@Schema(description = "Commits here that the upstream does not have") int ahead,
			@Schema(description = "Commits in the upstream that are not here: pull first") int behind) {
	}

	/**
	 * One changed file.
	 *
	 * @param renamedFrom the old path of a renamed or copied file
	 * @param staged the change in the index (what the next commit takes)
	 * @param unstaged the change in the working tree, not staged yet
	 */
	public record Change(
			@Schema(description = "From the repository root, with '/'") String path,
			@Schema(description = "The old path of a renamed or copied file; null otherwise", nullable = true)
			Optional<String> renamedFrom,
			@Schema(description = "The change in the index: what the next commit takes") Kind staged,
			@Schema(description = "The change in the working tree, not staged yet") Kind unstaged) {
	}

	public enum Kind {
		UNCHANGED, MODIFIED, TYPE_CHANGED, ADDED, DELETED, RENAMED, COPIED, UNTRACKED, CONFLICT
	}
}
