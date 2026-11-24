package com.gratchev.mizoine.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Kind;
import com.gratchev.mizoine.git.GitStatus.Upstream;

/** Step 44: running the git program safely (T4) and reading the status of real repositories. */
class GitCommandsTest {

	private static final long MEGABYTE = 1024 * 1024;

	private final Path dir;
	private final TestGit git;
	private final GitCommands commands;

	GitCommandsTest(@TempDir final Path dir) throws IOException {
		this.dir = dir.toRealPath();
		this.git = new TestGit(this.dir.resolve("home"));
		this.commands = new GitCommands("git", Duration.ofSeconds(20), MEGABYTE, git.environment());
	}

	// which folders are Git repositories (D34)

	@Test
	void aFolderWithoutGitIsNoGitRepository() throws IOException {
		assertThat(commands.status(Files.createDirectories(dir.resolve("plain")))).isEmpty();
	}

	@Test
	void aSubfolderOfAWorkingTreeIsNoGitRepository() throws Exception {
		// its commit would take files outside the repository
		final Path repository = git.repository(dir.resolve("repo"));

		assertThat(commands.status(Files.createDirectories(repository.resolve("sub")))).isEmpty();
	}

	@Test
	void aRepositoryWithoutCommits() throws Exception {
		final Path repository = Files.createDirectories(dir.resolve("new"));
		git.git(repository, "init", "-q");
		Files.writeString(repository.resolve("first.md"), "x");

		assertThat(commands.status(repository)).contains(new GitStatus(Optional.of("main"), false, Optional.empty(),
				List.of(new Change("first.md", Optional.empty(), Kind.UNCHANGED, Kind.UNTRACKED))));
	}

	// the status

	@Test
	void changesStagedAndNot() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("a b ü.md"), "a");
		Files.writeString(repository.resolve("old.md"), "old");
		git.git(repository, "add", ".");
		git.git(repository, "commit", "-q", "-m", "more");
		Files.writeString(repository.resolve("README.md"), "changed\n");
		Files.writeString(repository.resolve("a b ü.md"), "staged");
		git.git(repository, "add", "a b ü.md");
		git.git(repository, "mv", "old.md", "new name.md");
		Files.createDirectories(repository.resolve("sub/deeper"));
		Files.writeString(repository.resolve("sub/deeper/n.md"), "n");

		assertThat(commands.status(repository).orElseThrow().changes()).containsExactlyInAnyOrder(
				new Change("README.md", Optional.empty(), Kind.UNCHANGED, Kind.MODIFIED),
				new Change("a b ü.md", Optional.empty(), Kind.MODIFIED, Kind.UNCHANGED),
				new Change("new name.md", Optional.of("old.md"), Kind.RENAMED, Kind.UNCHANGED),
				new Change("sub/deeper/n.md", Optional.empty(), Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void mizoinesTemporaryFilesAreNotChanges() throws Exception {
		// an upload in progress (RepositoryWriter) is no change of the user's
		final Path repository = git.repository(dir.resolve("repo"));
		Files.createDirectories(repository.resolve("sub"));
		Files.writeString(repository.resolve("sub/.mizoine-upload-1234"), "part");
		Files.writeString(repository.resolve(".mizoine-upload-5678"), "part");
		Files.writeString(repository.resolve(".gitignore"), "*.tmp\n");

		assertThat(commands.status(repository).orElseThrow().changes()).extracting(Change::path).containsExactly(".gitignore");
	}

	@Test
	void aConflict() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		git.git(repository, "checkout", "-q", "-b", "other");
		Files.writeString(repository.resolve("README.md"), "other\n");
		git.git(repository, "commit", "-q", "-am", "other");
		git.git(repository, "checkout", "-q", "main");
		Files.writeString(repository.resolve("README.md"), "main\n");
		git.git(repository, "commit", "-q", "-am", "main");
		commands.run(repository, List.of("merge", "other"));

		assertThat(commands.status(repository).orElseThrow().changes())
				.containsExactly(new Change("README.md", Optional.empty(), Kind.CONFLICT, Kind.CONFLICT));
	}

	@Test
	void aheadAndBehindTheUpstream() throws Exception {
		final Path remote = Files.createDirectories(dir.resolve("remote.git"));
		git.git(remote, "init", "-q", "--bare");
		final Path repository = git.repository(dir.resolve("repo"));
		git.git(repository, "remote", "add", "origin", remote.toString());
		git.git(repository, "push", "-q", "-u", "origin", "main");
		git.git(dir, "clone", "-q", remote.toString(), "other");
		Files.writeString(dir.resolve("other/theirs.md"), "t");
		git.git(dir.resolve("other"), "add", ".");
		git.git(dir.resolve("other"), "commit", "-q", "-m", "theirs");
		git.git(dir.resolve("other"), "push", "-q");
		Files.writeString(repository.resolve("mine.md"), "m");
		git.git(repository, "add", ".");
		git.git(repository, "commit", "-q", "-m", "mine");
		git.git(repository, "fetch", "-q");

		final GitStatus status = commands.status(repository).orElseThrow();

		assertThat(status.branch()).contains("main");
		assertThat(status.upstream()).contains(new Upstream("origin/main", false, 1, 1));
	}

	@Test
	void aDetachedHead() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		git.git(repository, "checkout", "-q", "--detach");

		assertThat(commands.status(repository).orElseThrow().branch()).isEmpty();
	}

	// running git safely (T4)

	@Test
	void aCommandThatTakesTooLongIsEnded() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		final GitCommands impatient = new GitCommands("git", Duration.ofMillis(500), MEGABYTE, git.environment());
		final long start = System.nanoTime();

		// a hanging remote, as an alias that runs a shell command
		assertThatThrownBy(() -> impatient.run(repository, List.of("-c", "alias.hang=!sleep 30", "hang")))
				.isInstanceOf(GitTimeoutException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
	}

	@Test
	void tooMuchOutputIsAnError() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));

		assertThatThrownBy(() -> commands.run(repository, List.of("-c", "alias.big=!head -c 3000000 /dev/zero", "big")))
				.isInstanceOf(GitException.class).hasMessageContaining("output");
	}

	@Test
	void gitThatIsNotInstalledIsSaid() throws IOException {
		final GitCommands missing = new GitCommands("no-such-git-program", Duration.ofSeconds(5), MEGABYTE, git.environment());

		assertThatThrownBy(() -> missing.status(Files.createDirectories(dir.resolve("plain"))))
				.isInstanceOf(GitException.class).hasMessageContaining("no-such-git-program");
	}

	@Test
	void theResultHasExitCodeOutputAndGitsMessage() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));

		final GitCommands.Result result = commands.run(repository, List.of("log", "--format=%s", "no-such-branch"));

		assertThat(result.exitCode()).isNotZero();
		assertThat(result.errors()).contains("no-such-branch");
		assertThat(commands.run(repository, List.of("log", "--format=%s")).output()).isEqualTo("first\n");
	}

	@Test
	void pathsAreLiteral() throws Exception {
		// --literal-pathspecs: '*' is a file name, not a pattern
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("a.md"), "a");

		commands.run(repository, List.of("add", "--", "*.md"));

		assertThat(commands.status(repository).orElseThrow().changes())
				.containsExactly(new Change("a.md", Optional.empty(), Kind.UNCHANGED, Kind.UNTRACKED));
	}

	// the environment of git (T4: never interactive, never another repository)

	@Test
	void gitNeverAsksAndActsOnlyOnTheGivenFolder() {
		final Map<String, String> environment = GitCommands.environment(Map.of("PATH", "/bin", "GIT_DIR", "/elsewhere/.git",
				"GIT_WORK_TREE", "/elsewhere", "GIT_INDEX_FILE", "/elsewhere/index"));

		assertThat(environment).containsEntry("PATH", "/bin").containsEntry("GIT_TERMINAL_PROMPT", "0")
				.containsEntry("GIT_SSH_COMMAND", "ssh -o BatchMode=yes")
				.doesNotContainKeys("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE");
	}

	@Test
	void anOwnSshCommandIsKept() {
		assertThat(GitCommands.environment(Map.of("GIT_SSH_COMMAND", "ssh -i key"))).containsEntry("GIT_SSH_COMMAND", "ssh -i key");
		assertThat(GitCommands.environment(Map.of("GIT_SSH", "/usr/bin/plink"))).doesNotContainKey("GIT_SSH_COMMAND");
	}
}
