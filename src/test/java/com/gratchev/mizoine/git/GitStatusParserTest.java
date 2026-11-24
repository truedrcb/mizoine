package com.gratchev.mizoine.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Kind;
import com.gratchev.mizoine.git.GitStatus.Upstream;

/**
 * Step 44, D36: {@code git status --porcelain=v2 --branch -z --untracked-files=all}, Git's stable machine format.
 * Records end with NUL; a rename's old path follows as a field of its own; names are never quoted.
 */
class GitStatusParserTest {

	private static final String HASHES = "100644 100644 100644 6178079822 6178079822";

	private static String output(final String... records) {
		return String.join("\0", records) + "\0";
	}

	@Test
	void aNewRepositoryHasNoCommitsYet() {
		final GitStatus status = GitStatusParser.parse(output("# branch.oid (initial)", "# branch.head main"));

		assertThat(status).isEqualTo(new GitStatus(Optional.of("main"), false, Optional.empty(), java.util.List.of()));
	}

	@Test
	void changesWithSpacesUtf8AndRenames() {
		final GitStatus status = GitStatusParser.parse(output("# branch.oid 77c71dc7", "# branch.head main",
				"1 .M N... " + HASHES + " c d ü.md",
				"2 R. N... " + HASHES + " R100 x y.md", "a.md",
				"1 A. N... 000000 100644 100644 0000000000 6178079822 new.md",
				"1 MD N... " + HASHES + " both.md",
				"? sub/n.md"));

		assertThat(status.hasCommits()).isTrue();
		assertThat(status.changes()).containsExactly(
				new Change("c d ü.md", Optional.empty(), Kind.UNCHANGED, Kind.MODIFIED),
				new Change("x y.md", Optional.of("a.md"), Kind.RENAMED, Kind.UNCHANGED),
				new Change("new.md", Optional.empty(), Kind.ADDED, Kind.UNCHANGED),
				new Change("both.md", Optional.empty(), Kind.MODIFIED, Kind.DELETED),
				new Change("sub/n.md", Optional.empty(), Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void aTypeChangeAndACopy() {
		// a file that became a symbolic link; a copy (only with copy detection)
		final GitStatus status = GitStatusParser.parse(output("# branch.oid 77c71dc7", "# branch.head main",
				"1 .T N... " + HASHES + " link.md", "2 C. N... " + HASHES + " C75 copy.md", "orig.md"));

		assertThat(status.changes()).containsExactly(
				new Change("link.md", Optional.empty(), Kind.UNCHANGED, Kind.TYPE_CHANGED),
				new Change("copy.md", Optional.of("orig.md"), Kind.COPIED, Kind.UNCHANGED));
	}

	@Test
	void aNameMayContainANewline() {
		// only -z keeps such a name intact
		final GitStatus status = GitStatusParser.parse(output("# branch.oid 77c71dc7", "# branch.head main", "? a\nb.md"));

		assertThat(status.changes()).extracting(Change::path).containsExactly("a\nb.md");
	}

	@Test
	void aConflictIsAConflictOnBothSides() {
		final GitStatus status = GitStatusParser.parse(output("# branch.oid 77c71dc7", "# branch.head main",
				"u UU N... 100644 100644 100644 100644 1111111111 2222222222 3333333333 the file.md"));

		assertThat(status.changes()).containsExactly(new Change("the file.md", Optional.empty(), Kind.CONFLICT, Kind.CONFLICT));
	}

	@Test
	void upstreamWithAheadAndBehind() {
		final GitStatus status = GitStatusParser.parse(output("# branch.oid 77c71dc7", "# branch.head main",
				"# branch.upstream origin/main", "# branch.ab +2 -3"));

		assertThat(status.upstream()).contains(new Upstream("origin/main", false, 2, 3));
	}

	@Test
	void anUpstreamThatIsGoneHasNoAheadOrBehind() {
		final GitStatus status = GitStatusParser.parse(output("# branch.oid 77c71dc7", "# branch.head main",
				"# branch.upstream origin/old"));

		assertThat(status.upstream()).contains(new Upstream("origin/old", true, 0, 0));
	}

	@Test
	void aDetachedHeadHasNoBranch() {
		final GitStatus status = GitStatusParser.parse(output("# branch.oid 77c71dc7", "# branch.head (detached)",
				"# stash 1"));

		assertThat(status.branch()).isEmpty();
	}

	@Test
	void unexpectedOutputIsAnError() {
		assertThatThrownBy(() -> GitStatusParser.parse(output("# branch.head main", "X something")))
				.isInstanceOf(GitException.class).hasMessageContaining("X something");
		assertThatThrownBy(() -> GitStatusParser.parse(output("# branch.head main", "1 Q. N... " + HASHES + " a.md")))
				.isInstanceOf(GitException.class);
		assertThatThrownBy(() -> GitStatusParser.parse(output("# branch.head main", "1 .M N...")))
				.isInstanceOf(GitException.class);
		// a rename without its old path
		assertThatThrownBy(() -> GitStatusParser.parse("# branch.head main\0" + "2 R. N... " + HASHES + " R100 x.md"))
				.isInstanceOf(GitException.class);
	}
}
