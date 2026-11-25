package com.gratchev.mizoine.git;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Kind;
import com.gratchev.mizoine.git.GitStatus.Upstream;

/**
 * What the Git page (step 45) shows: the status as text, with a link to each file's page where the user may read
 * it. Values the page leaves out are {@code null}, as the template expects.
 *
 * @param branch the branch, or "detached HEAD"
 * @param upstream e.g. "origin/main: 2 ahead, 3 behind", "origin/old: gone" or "no upstream"
 */
record GitStatusView(String branch, String upstream, boolean hasCommits, boolean clean, List<Line> conflicts,
		List<Line> staged, List<Line> unstaged) {

	/**
	 * One file in a list.
	 *
	 * @param kind e.g. "modified", "type changed"
	 * @param renamedFrom the old path of a renamed or copied file, or {@code null}
	 * @param href the file's page, or {@code null} where the user may not read it (or it is deleted)
	 */
	record Line(String kind, String path, String renamedFrom, String href) {
	}

	/** @param links the page of a file, given its path, where the user may read it */
	static GitStatusView of(final GitStatus status, final Function<String, Optional<String>> links) {
		return new GitStatusView(status.branch().orElse("detached HEAD"),
				status.upstream().map(GitStatusView::upstream).orElse("no upstream"),
				status.hasCommits(), status.isClean(),
				lines(status.conflicts(), Change::staged, links),
				lines(status.staged(), Change::staged, links),
				lines(status.unstaged(), Change::unstaged, links));
	}

	private static String upstream(final Upstream upstream) {
		if (upstream.gone()) {
			return upstream.name() + ": gone";
		}
		if (upstream.ahead() == 0 && upstream.behind() == 0) {
			return upstream.name() + ": up to date";
		}
		return upstream.name() + ": " + upstream.ahead() + " ahead, " + upstream.behind() + " behind";
	}

	private static List<Line> lines(final List<Change> changes, final Function<Change, Kind> side,
			final Function<String, Optional<String>> links) {
		return changes.stream().map(change -> line(change, side.apply(change), links)).toList();
	}

	/** The old name only where this side is the rename or copy: a renamed file may be modified after staging too. */
	private static Line line(final Change change, final Kind kind, final Function<String, Optional<String>> links) {
		final boolean moved = kind == Kind.RENAMED || kind == Kind.COPIED;
		return new Line(kind.name().toLowerCase(Locale.ROOT).replace('_', ' '), change.path(),
				moved ? change.renamedFrom().orElse(null) : null, links.apply(change.path()).orElse(null));
	}
}
