package com.gratchev.mizoine.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Kind;
import com.gratchev.mizoine.git.GitStatus.Upstream;
import com.gratchev.mizoine.git.GitStatusView.Line;

/** Step 45: what the Git page shows, as text, from a status. */
class GitStatusViewTest {

	private static final Function<String, Optional<String>> NO_LINKS = path -> Optional.empty();

	private static GitStatusView view(final Optional<String> branch, final Optional<Upstream> upstream) {
		return GitStatusView.of(new GitStatus(branch, true, upstream, List.of()), NO_LINKS);
	}

	@Test
	void branchAndUpstream() {
		assertThat(view(Optional.of("main"), Optional.of(new Upstream("origin/main", false, 2, 3))))
				.extracting(GitStatusView::branch, GitStatusView::upstream)
				.containsExactly("main", "origin/main: 2 ahead, 3 behind");
		assertThat(view(Optional.of("main"), Optional.of(new Upstream("origin/main", false, 0, 0))).upstream())
				.isEqualTo("origin/main: up to date");
		// only one side 0: not up to date ("behind" is what says: pull first)
		assertThat(view(Optional.of("main"), Optional.of(new Upstream("origin/main", false, 0, 1))).upstream())
				.isEqualTo("origin/main: 0 ahead, 1 behind");
		assertThat(view(Optional.of("main"), Optional.of(new Upstream("origin/main", false, 1, 0))).upstream())
				.isEqualTo("origin/main: 1 ahead, 0 behind");
		assertThat(view(Optional.of("main"), Optional.of(new Upstream("origin/old", true, 0, 0))).upstream())
				.isEqualTo("origin/old: gone");
		assertThat(view(Optional.of("main"), Optional.empty()).upstream()).isEqualTo("no upstream");
		assertThat(view(Optional.empty(), Optional.empty()).branch()).isEqualTo("detached HEAD");
	}

	@Test
	void linesWithKindLinkAndOldName() {
		final GitStatus status = new GitStatus(Optional.of("main"), true, Optional.empty(), List.of(
				new Change("new name.md", Optional.of("old.md"), Kind.RENAMED, Kind.TYPE_CHANGED),
				new Change("copy.md", Optional.of("orig.md"), Kind.COPIED, Kind.MODIFIED),
				new Change("c.md", Optional.empty(), Kind.CONFLICT, Kind.CONFLICT)));

		final GitStatusView view = GitStatusView.of(status, path -> Optional.of("/repo/" + path));

		assertThat(view.staged()).containsExactly(new Line("renamed", "new name.md", "old.md", "/repo/new name.md"),
				new Line("copied", "copy.md", "orig.md", "/repo/copy.md"));
		// changed again after the rename or copy was staged: the old name belongs to the staged line only
		assertThat(view.unstaged()).containsExactly(new Line("type changed", "new name.md", null, "/repo/new name.md"),
				new Line("modified", "copy.md", null, "/repo/copy.md"));
		assertThat(view.conflicts()).containsExactly(new Line("conflict", "c.md", null, "/repo/c.md"));
		assertThat(view.clean()).isFalse();
		assertThat(view.hasCommits()).isTrue();
	}

	@Test
	void unreadableFilesHaveNoLink() {
		final GitStatus status = new GitStatus(Optional.of("main"), false, Optional.empty(),
				List.of(new Change("a.md", Optional.empty(), Kind.UNCHANGED, Kind.UNTRACKED)));

		final GitStatusView view = GitStatusView.of(status, NO_LINKS);

		assertThat(view.unstaged()).containsExactly(new Line("untracked", "a.md", null, null));
		assertThat(view.hasCommits()).isFalse();
	}
}
