package com.gratchev.mizoine.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Kind;

/** Step 45: the page's three lists. A file changed again after staging is in both of the first two. */
class GitStatusTest {

	private static final Change STAGED = new Change("staged.md", Optional.empty(), Kind.ADDED, Kind.UNCHANGED);
	private static final Change BOTH = new Change("both.md", Optional.empty(), Kind.MODIFIED, Kind.MODIFIED);
	private static final Change UNSTAGED = new Change("unstaged.md", Optional.empty(), Kind.UNCHANGED, Kind.DELETED);
	private static final Change NEW = new Change("new.md", Optional.empty(), Kind.UNCHANGED, Kind.UNTRACKED);
	private static final Change CONFLICT = new Change("conflict.md", Optional.empty(), Kind.CONFLICT, Kind.CONFLICT);
	private static final GitStatus STATUS = new GitStatus(Optional.of("main"), true, Optional.empty(),
			List.of(STAGED, BOTH, UNSTAGED, NEW, CONFLICT));

	@Test
	void stagedIsWhatTheNextCommitTakes() {
		assertThat(STATUS.staged()).containsExactly(STAGED, BOTH);
	}

	@Test
	void unstagedIsChangedOrNewInTheWorkingTree() {
		assertThat(STATUS.unstaged()).containsExactly(BOTH, UNSTAGED, NEW);
	}

	@Test
	void conflictsAreAListOfTheirOwn() {
		assertThat(STATUS.conflicts()).containsExactly(CONFLICT);
	}

	@Test
	void cleanWithoutChanges() {
		assertThat(STATUS.isClean()).isFalse();
		assertThat(new GitStatus(Optional.of("main"), true, Optional.empty(), List.of()).isClean()).isTrue();
	}
}
