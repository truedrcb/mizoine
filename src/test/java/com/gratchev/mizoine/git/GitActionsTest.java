package com.gratchev.mizoine.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gratchev.mizoine.git.GitOutcome.Done;
import com.gratchev.mizoine.git.GitOutcome.Refused;
import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Kind;
import com.gratchev.mizoine.repositories.RepositoryWriter;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;

/** Steps 46–48: the Git actions on real repositories, with Git cut off from this machine's settings. */
class GitActionsTest {

	private final Path dir;
	private final TestGit git;
	private final GitCommands commands;
	private final RepositoryWriter writer;
	private final GitActions actions;

	GitActionsTest(@TempDir final Path dir) throws IOException {
		this.dir = dir.toRealPath();
		this.git = new TestGit(this.dir.resolve("home"));
		this.commands = new GitCommands("git", Duration.ofSeconds(20), 1024 * 1024, git.environment());
		this.writer = new RepositoryWriter();
		this.actions = new GitActions(commands, writer);
	}

	private List<Change> changes(final Path repository) {
		return commands.status(repository).orElseThrow().changes();
	}

	private static Change change(final String path, final Kind staged, final Kind unstaged) {
		return new Change(path, Optional.empty(), staged, unstaged);
	}

	// step 46: stage and unstage

	@Test
	void stageSingleFiles() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("a b ü.md"), "a");
		Files.writeString(repository.resolve("other.md"), "o");

		assertThat(actions.stage(repository, Selection.of(List.of("a b ü.md")))).isEqualTo(new Done("Staged 1 file."));
		assertThat(changes(repository)).containsExactly(change("a b ü.md", Kind.ADDED, Kind.UNCHANGED),
				change("other.md", Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void stageAllIsEveryChangeOfTheStatus() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("README.md"), "changed");
		Files.createDirectories(repository.resolve("sub"));
		Files.writeString(repository.resolve("sub/new.md"), "n");
		// an upload in progress: not part of the status, so not staged either
		Files.writeString(repository.resolve(".mizoine-upload-1"), "part");

		assertThat(actions.stage(repository, Selection.ALL)).isEqualTo(new Done("Staged 2 files."));
		assertThat(changes(repository)).containsExactly(change("README.md", Kind.MODIFIED, Kind.UNCHANGED),
				change("sub/new.md", Kind.ADDED, Kind.UNCHANGED));
	}

	@Test
	void aDeletionIsStagedLikeAnyChange() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.delete(repository.resolve("README.md"));

		actions.stage(repository, Selection.of(List.of("README.md")));

		assertThat(changes(repository)).containsExactly(change("README.md", Kind.DELETED, Kind.UNCHANGED));
	}

	@Test
	void onlyPathsOfTheStatusAreStaged() throws Exception {
		// nothing else of the working tree can be named: not unchanged files, not other places
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("new.md"), "n");

		for (final String path : List.of("README.md", "../outside.md", "missing.md", ".git/config", "*.md")) {
			assertThat(actions.stage(repository, Selection.of(List.of("new.md", path)))).as(path)
					.isEqualTo(new Refused("Not in the changes to stage: " + path));
		}
		assertThat(changes(repository)).containsExactly(change("new.md", Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void nothingToStage() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));

		assertThat(actions.stage(repository, Selection.ALL)).isEqualTo(new Refused("Nothing to stage."));
	}

	@Test
	void unstageSingleFilesAndAll() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("README.md"), "changed");
		Files.writeString(repository.resolve("new.md"), "n");
		actions.stage(repository, Selection.ALL);

		assertThat(actions.unstage(repository, Selection.of(List.of("new.md")))).isEqualTo(new Done("Unstaged 1 file."));
		assertThat(changes(repository)).containsExactly(change("README.md", Kind.MODIFIED, Kind.UNCHANGED),
				change("new.md", Kind.UNCHANGED, Kind.UNTRACKED));

		assertThat(actions.unstage(repository, Selection.ALL)).isEqualTo(new Done("Unstaged 1 file."));
		assertThat(changes(repository)).containsExactly(change("README.md", Kind.UNCHANGED, Kind.MODIFIED),
				change("new.md", Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void unstagingARenameUnstagesBothNames() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		git.git(repository, "mv", "README.md", "renamed.md");

		actions.unstage(repository, Selection.of(List.of("renamed.md")));

		assertThat(changes(repository)).containsExactlyInAnyOrder(change("README.md", Kind.UNCHANGED, Kind.DELETED),
				change("renamed.md", Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void unstageBeforeTheFirstCommit() throws Exception {
		final Path repository = Files.createDirectories(dir.resolve("new"));
		git.git(repository, "init", "-q");
		Files.writeString(repository.resolve("first.md"), "f");
		actions.stage(repository, Selection.ALL);

		assertThat(actions.unstage(repository, Selection.ALL)).isEqualTo(new Done("Unstaged 1 file."));
		assertThat(changes(repository)).containsExactly(change("first.md", Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void onlyStagedPathsAreUnstaged() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("new.md"), "n");

		assertThat(actions.unstage(repository, Selection.of(List.of("new.md"))))
				.isEqualTo(new Refused("Not in the staged changes: new.md"));
		assertThat(actions.unstage(repository, Selection.ALL)).isEqualTo(new Refused("Nothing to unstage."));
	}

	@Test
	void aConflictIsNotStaged() throws Exception {
		// D37: resolved by hand on the server; staging would mark it resolved
		final Path repository = git.repository(dir.resolve("repo"));
		git.git(repository, "checkout", "-q", "-b", "other");
		Files.writeString(repository.resolve("README.md"), "other\n");
		git.git(repository, "commit", "-q", "-am", "other");
		git.git(repository, "checkout", "-q", "main");
		Files.writeString(repository.resolve("README.md"), "main\n");
		git.git(repository, "commit", "-q", "-am", "main");
		commands.run(repository, List.of("merge", "other"));

		assertThat(actions.stage(repository, Selection.ALL)).isEqualTo(new Refused("Nothing to stage."));
		assertThat(actions.stage(repository, Selection.of(List.of("README.md"))))
				.isEqualTo(new Refused("Not in the changes to stage: README.md"));
	}

	@Test
	void notAGitRepository() throws IOException {
		final Path plain = Files.createDirectories(dir.resolve("plain"));

		assertThat(actions.stage(plain, Selection.ALL)).isEqualTo(new Refused(GitActions.NOT_A_REPOSITORY));
		assertThat(actions.unstage(plain, Selection.ALL)).isEqualTo(new Refused(GitActions.NOT_A_REPOSITORY));
	}

	// step 47: commit

	private static final Author ARTEM = Author.of("artem", Optional.of("artem@example.org"));

	@Test
	void commitTakesTheStagedChangesWithTheUserAsAuthor() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("README.md"), "changed");
		Files.writeString(repository.resolve("not staged.md"), "n");
		actions.stage(repository, Selection.of(List.of("README.md")));

		assertThat(actions.commit(repository, new CommitMessage("Change the readme\n\nBecause."), ARTEM))
				.isEqualTo(new Done("Committed 1 file."));
		// the committer is the server's own Git identity
		assertThat(git.git(repository, "log", "-1", "--format=%an <%ae>|%cn <%ce>|%B"))
				.isEqualTo("artem <artem@example.org>|Test <test@example.org>|Change the readme\n\nBecause.\n\n");
		assertThat(changes(repository)).containsExactly(change("not staged.md", Kind.UNCHANGED, Kind.UNTRACKED));
	}

	@Test
	void nothingStagedIsNothingToCommit() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));
		Files.writeString(repository.resolve("README.md"), "changed");

		assertThat(actions.commit(repository, new CommitMessage("Nothing"), ARTEM))
				.isEqualTo(new Refused("Nothing staged to commit."));
	}

	@Test
	void aFailingHookRefusesWithItsMessage() throws Exception {
		// hooks run as with a commit by hand; they are the owner's (users cannot write into .git)
		final Path repository = git.repository(dir.resolve("repo"));
		final Path hook = repository.resolve(".git/hooks/pre-commit");
		Files.writeString(hook, "#!/bin/sh\necho 'no commits on Fridays' >&2\nexit 1\n");
		hook.toFile().setExecutable(true);
		Files.writeString(repository.resolve("README.md"), "changed");
		actions.stage(repository, Selection.ALL);

		assertThat(actions.commit(repository, new CommitMessage("Try"), ARTEM)).isEqualTo(new Refused("no commits on Fridays"));
		assertThat(git.git(repository, "log", "--format=%s")).isEqualTo("first\n");
	}

	@Test
	void aCommitNeedsAMessage() {
		assertThatThrownBy(() -> new CommitMessage(" \n ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CommitMessage(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void commitInANoRepository() throws IOException {
		assertThat(actions.commit(Files.createDirectories(dir.resolve("plain")), new CommitMessage("x"), ARTEM))
				.isEqualTo(new Refused(GitActions.NOT_A_REPOSITORY));
	}

	// step 48: pull and push (D37)

	/** A repository with a bare remote as upstream, and a second clone that also pushes there. */
	private Path withRemote() throws Exception {
		final Path remote = Files.createDirectories(dir.resolve("remote.git"));
		git.git(remote, "init", "-q", "--bare");
		final Path repository = git.repository(dir.resolve("repo"));
		git.git(repository, "remote", "add", "origin", remote.toString());
		git.git(repository, "push", "-q", "-u", "origin", "main");
		git.git(dir, "clone", "-q", remote.toString(), "other");
		return repository;
	}

	private void theirCommit(final String file) throws Exception {
		Files.writeString(dir.resolve("other").resolve(file), "theirs");
		git.git(dir.resolve("other"), "add", ".");
		git.git(dir.resolve("other"), "commit", "-q", "-m", "theirs");
		git.git(dir.resolve("other"), "push", "-q");
	}

	private void myCommit(final Path repository, final String file) throws Exception {
		Files.writeString(repository.resolve(file), "mine");
		git.git(repository, "add", ".");
		git.git(repository, "commit", "-q", "-m", "mine");
	}

	@Test
	void pullFastForwards() throws Exception {
		final Path repository = withRemote();
		theirCommit("theirs.md");

		assertThat(actions.pull(repository)).isEqualTo(new Done("Pulled from origin/main."));
		assertThat(repository.resolve("theirs.md")).hasContent("theirs");
	}

	@Test
	void pullNeverMerges() throws Exception {
		// diverged: resolved by hand on the server
		final Path repository = withRemote();
		theirCommit("theirs.md");
		myCommit(repository, "mine.md");

		assertThat(actions.pull(repository)).isInstanceOf(Refused.class);
		assertThat(git.git(repository, "log", "--format=%s")).isEqualTo("mine\nfirst\n");
	}

	@Test
	void pushTheCurrentBranchToItsUpstream() throws Exception {
		final Path repository = withRemote();
		myCommit(repository, "mine.md");

		assertThat(actions.push(repository)).isEqualTo(new Done("Pushed to origin/main."));
		assertThat(git.git(dir.resolve("remote.git"), "log", "--format=%s", "main")).isEqualTo("mine\nfirst\n");
	}

	@Test
	void aPushThatIsBehindIsRejectedNeverForced() throws Exception {
		final Path repository = withRemote();
		theirCommit("theirs.md");
		myCommit(repository, "mine.md");

		final GitOutcome outcome = actions.push(repository);

		assertThat(outcome).isInstanceOf(Refused.class);
		assertThat(((Refused) outcome).message()).contains("rejected");
		assertThat(git.git(dir.resolve("remote.git"), "log", "--format=%s", "main")).isEqualTo("theirs\nfirst\n");
	}

	@Test
	void withoutUpstreamNeitherPullNorPush() throws Exception {
		final Path repository = git.repository(dir.resolve("repo"));

		assertThat(actions.pull(repository)).isEqualTo(new Refused("No upstream branch: set one on the server."));
		assertThat(actions.push(repository)).isEqualTo(new Refused("No upstream branch: set one on the server."));
		assertThat(actions.pull(Files.createDirectories(dir.resolve("plain")))).isEqualTo(new Refused(GitActions.NOT_A_REPOSITORY));
	}

	@Test
	void aRemoteThatDoesNotAnswerIsEnded() throws Exception {
		final Path repository = withRemote();
		// Git appends the remote's path to the command: "true" takes it
		git.git(repository, "config", "remote.origin.uploadpack", "sleep 30; true");
		final GitActions impatient = new GitActions(new GitCommands("git", Duration.ofSeconds(1), 1024 * 1024,
				git.environment()), writer);

		assertThatThrownBy(() -> impatient.pull(repository)).isInstanceOf(GitTimeoutException.class);
	}

	@Test
	void aPullAndMizoinesWritesTakeTurns() throws Exception {
		// a pull rewrites files: an upload must not replace one in the middle of it
		final Path repository = withRemote();
		final Path started = dir.resolve("started");
		git.git(repository, "config", "remote.origin.uploadpack", "touch '" + started + "'; sleep 2; false");
		final Thread pulling = Thread.ofPlatform().start(() -> actions.pull(repository));
		final long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!Files.exists(started) && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}

		final Thread writing = Thread.ofPlatform().start(() -> writer.write(repository, List.of("upload.md"),
				new ByteArrayInputStream("u".getBytes(StandardCharsets.UTF_8)), 100, Precondition.NONE));
		waitUntilWaiting(writing);

		pulling.join(10000);
		writing.join(5000);
		assertThat(repository.resolve("upload.md")).hasContent("u");
	}

	/** Parked on the writer's lock: the state a waiting thread shows. */
	private static void waitUntilWaiting(final Thread thread) throws InterruptedException {
		final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertThat(thread.getState()).isEqualTo(Thread.State.WAITING);
	}

	// the selection, checked where it comes in

	@Test
	void aSelectionNamesFilesOrAll() {
		assertThatThrownBy(() -> Selection.of(List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Selection.of(List.of("a.md", " "))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Selection.of(null)).isInstanceOf(IllegalArgumentException.class);
		assertThat(Selection.of(List.of("a.md", "a.md")).paths()).containsExactly("a.md");
	}
}
