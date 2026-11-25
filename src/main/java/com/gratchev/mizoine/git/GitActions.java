package com.gratchev.mizoine.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.gratchev.mizoine.git.GitOutcome.Done;
import com.gratchev.mizoine.git.GitOutcome.Refused;
import com.gratchev.mizoine.git.GitStatus.Change;
import com.gratchev.mizoine.git.GitStatus.Upstream;
import com.gratchev.mizoine.repositories.RepositoryWriter;

/**
 * The Git actions of the Git page (steps 46–48), each one or a few {@code git} commands as a human would type them
 * (T4). Every action starts from the current status: a repository that is no Git working tree (D34), or a path
 * that is not among the changes, is refused before Git runs, so no request can name any other file.
 */
public class GitActions {

	public static final String NOT_A_REPOSITORY = "The repository's folder is not the top of a Git working tree.";

	private static final String NO_UPSTREAM = "No upstream branch: set one on the server.";

	private final GitCommands git;
	private final RepositoryWriter writer;

	/** @param writer whose writes a pull waits for, and which waits for a pull */
	public GitActions(final GitCommands git, final RepositoryWriter writer) {
		this.git = git;
		this.writer = writer;
	}

	/** Stage: {@code git add -- <paths>}; "all" is every change of the status, so never a temporary upload file. */
	public GitOutcome stage(final Path root, final Selection selection) {
		return withStatus(root, status -> withChosen(selection, status.unstaged(), "Not in the changes to stage: ",
				"Nothing to stage.", paths -> run(root, command(List.of("add"), paths), "Staged " + files(paths.size()) + ".")));
	}

	/**
	 * Unstage: {@code git restore --staged -- <paths>}, before the first commit {@code git rm --cached}. A rename
	 * is unstaged with its old name, so both names are back as they were.
	 */
	public GitOutcome unstage(final Path root, final Selection selection) {
		return withStatus(root, status -> withChosen(selection, status.staged(), "Not in the staged changes: ",
				"Nothing to unstage.", paths -> {
					final List<String> command = status.hasCommits() ? List.of("restore", "--staged")
							: List.of("rm", "--cached", "-q");
					return run(root, command(command, withOldNames(paths, status.staged())),
							"Unstaged " + files(paths.size()) + ".");
				}));
	}

	/**
	 * Commit the staged changes: {@code git commit -m <message> --author <user>} (D38). Hooks run as with a commit by
	 * hand; a refusing hook gives its own message.
	 */
	public GitOutcome commit(final Path root, final CommitMessage message, final Author author) {
		return withStatus(root, status -> status.staged().isEmpty() ? new Refused("Nothing staged to commit.")
				: run(root, List.of("commit", "-q", "-m", message.text(), "--author=" + author.git()),
						"Committed " + files(status.staged().size()) + "."));
	}

	/**
	 * Pull: {@code git pull --ff-only --no-rebase} (D37), never a merge or a rebase; a diverged branch is refused, to
	 * be resolved by hand on the server. One at a time with Mizoine's own writes.
	 */
	public GitOutcome pull(final Path root) {
		return withUpstream(root, upstream -> writer.exclusively(() -> run(root,
				List.of("pull", "--ff-only", "--no-rebase", "-q"), "Pulled from " + upstream.name() + ".")));
	}

	/** Push: the current branch to its upstream only ({@code push.default=upstream}), never forced (D37). */
	public GitOutcome push(final Path root) {
		return withUpstream(root, upstream -> run(root, List.of("-c", "push.default=upstream", "push", "-q"),
				"Pushed to " + upstream.name() + "."));
	}

	private GitOutcome withUpstream(final Path root, final Function<Upstream, GitOutcome> action) {
		return withStatus(root, status -> status.upstream().map(action).orElse(new Refused(NO_UPSTREAM)));
	}

	private GitOutcome withStatus(final Path root, final Function<GitStatus, GitOutcome> action) {
		return git.status(root).map(action).orElse(new Refused(NOT_A_REPOSITORY));
	}

	/**
	 * Runs the action with the chosen paths, all of them among the candidates; refused if one is not, or if
	 * "all" finds none.
	 */
	private static GitOutcome withChosen(final Selection selection, final List<Change> candidates, final String notAmong,
			final String nothing, final Function<List<String>, GitOutcome> action) {
		final List<String> paths = candidates.stream().map(Change::path).toList();
		if (selection.all()) {
			return paths.isEmpty() ? new Refused(nothing) : action.apply(paths);
		}
		return selection.paths().stream().filter(path -> !paths.contains(path)).findFirst()
				.<GitOutcome>map(path -> new Refused(notAmong + path))
				.orElseGet(() -> action.apply(selection.paths()));
	}

	private static List<String> withOldNames(final List<String> paths, final List<Change> staged) {
		return Stream.concat(paths.stream(), staged.stream()
				.filter(change -> paths.contains(change.path()))
				.flatMap(change -> change.renamedFrom().stream())).distinct().toList();
	}

	private static List<String> command(final List<String> command, final List<String> paths) {
		final List<String> arguments = new ArrayList<>(command);
		arguments.add("--");
		arguments.addAll(paths);
		return arguments;
	}

	private GitOutcome run(final Path root, final List<String> arguments, final String done) {
		final GitCommands.Result result = git.run(root, arguments);
		return result.exitCode() == 0 ? new Done(done) : new Refused(message(result));
	}

	/** Git writes some refusals to its output, most to its messages: both, as Git wrote them. */
	private static String message(final GitCommands.Result result) {
		return (result.errors().strip() + "\n" + result.output().strip()).strip();
	}

	private static String files(final int count) {
		return count == 1 ? "1 file" : count + " files";
	}
}
