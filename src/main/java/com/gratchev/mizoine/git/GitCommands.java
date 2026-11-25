package com.gratchev.mizoine.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.gratchev.mizoine.repositories.RepositoryWriter;

/**
 * Runs the {@code git} program of the server (T4), as a human would, with these safeguards:
 * <ul>
 * <li>a fixed argument list, never a shell; {@code --literal-pathspecs}, so a path is always just a path;</li>
 * <li>always in the given folder ({@code -C}); variables that would point Git elsewhere are removed;</li>
 * <li>never interactive: no input, {@code GIT_TERMINAL_PROMPT=0}, SSH in batch mode;</li>
 * <li>a time limit and an output limit; past either, Git and what it started are ended.</li>
 * </ul>
 */
public class GitCommands {

	/** What {@code git rev-parse --local-env-vars} names: each would make Git act on another repository. */
	private static final List<String> REPOSITORY_VARIABLES = List.of("GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_CONFIG",
			"GIT_CONFIG_PARAMETERS", "GIT_CONFIG_COUNT", "GIT_OBJECT_DIRECTORY", "GIT_DIR", "GIT_WORK_TREE",
			"GIT_IMPLICIT_WORK_TREE", "GIT_GRAFT_FILE", "GIT_INDEX_FILE", "GIT_NO_REPLACE_OBJECTS", "GIT_REPLACE_REF_BASE",
			"GIT_PREFIX", "GIT_SHALLOW_FILE", "GIT_COMMON_DIR");
	private static final String SSH_COMMAND = "GIT_SSH_COMMAND";

	private final String executable;
	private final Duration timeout;
	private final int maxOutput;
	private final Map<String, String> environment;

	/**
	 * @param executable {@code git}, found on the {@code PATH}
	 * @param maxOutput bytes, for the output and for Git's messages each
	 * @param inherited the environment to start from, usually the server's ({@link System#getenv()})
	 */
	public GitCommands(final String executable, final Duration timeout, final int maxOutput,
			final Map<String, String> inherited) {
		this.executable = executable;
		this.timeout = timeout;
		this.maxOutput = maxOutput;
		this.environment = environment(inherited);
	}

	/** What Git did: its exit code, its output and its messages (stderr), as UTF-8. */
	public record Result(int exitCode, String output, String errors) {
	}

	/**
	 * The status (D36) of a repository whose root is the top of a Git working tree (D34). Mizoine's own temporary
	 * files of uploads in progress are left out: they are no change of anybody's.
	 *
	 * @return empty if the folder is no Git repository, or only a subfolder of one
	 */
	public Optional<GitStatus> status(final Path root) {
		if (!isTopOfWorkingTree(root)) {
			return Optional.empty();
		}
		final Result result = run(root, List.of("status", "--porcelain=v2", "--branch", "-z", "--untracked-files=all"));
		if (result.exitCode() != 0) {
			throw new GitException(result.errors().strip());
		}
		final GitStatus status = GitStatusParser.parse(result.output());
		return Optional.of(new GitStatus(status.branch(), status.hasCommits(), status.upstream(),
				status.changes().stream().filter(change -> !isTemporary(change.path())).toList()));
	}

	/**
	 * The time of the last commit (step 60), shown beside the search index's own dates.
	 *
	 * @return empty if the folder is no Git repository (or only a subfolder of one), or has no commit yet
	 */
	public Optional<Instant> lastCommit(final Path root) {
		if (!isTopOfWorkingTree(root)) {
			return Optional.empty();
		}
		final Result result = run(root, List.of("log", "-1", "--format=%cI"));
		// without a commit yet, Git exits with an error
		return result.exitCode() == 0
				? Optional.of(OffsetDateTime.parse(result.output().strip()).toInstant())
				: Optional.empty();
	}

	private boolean isTopOfWorkingTree(final Path root) {
		final Result result = run(root, List.of("rev-parse", "--show-toplevel"));
		if (result.exitCode() != 0) {
			return false;
		}
		try {
			return Path.of(result.output().replaceFirst("\n$", "")).equals(root.toRealPath());
		} catch (final IOException e) {
			return false;
		}
	}

	private static boolean isTemporary(final String path) {
		return path.substring(path.lastIndexOf('/') + 1).startsWith(RepositoryWriter.TEMPORARY_PREFIX);
	}

	/**
	 * Runs {@code git --literal-pathspecs -C <root> <arguments>}. Paths in the arguments belong after {@code --}.
	 *
	 * @throws GitTimeoutException if Git takes longer than the time limit
	 * @throws GitException if Git cannot be started, or gives more output than the limit
	 */
	Result run(final Path root, final List<String> arguments) {
		final Process process = start(root, arguments);
		final long deadline = System.nanoTime() + timeout.toNanos();
		try {
			// nothing to answer: whatever Git or SSH would ask is not asked
			process.getOutputStream().close();
			final Future<String> output = read(process.getInputStream(), process);
			final Future<String> errors = read(process.getErrorStream(), process);
			final String text = output.get(remaining(deadline), TimeUnit.NANOSECONDS);
			final String messages = errors.get(remaining(deadline), TimeUnit.NANOSECONDS);
			if (!process.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) {
				throw new TimeoutException();
			}
			return new Result(process.exitValue(), text, messages);
		} catch (final TimeoutException e) {
			end(process);
			throw new GitTimeoutException("Git did not finish within " + timeout.toSeconds() + " s: " + String.join(" ", arguments));
		} catch (final ExecutionException e) {
			end(process);
			throw e.getCause() instanceof final GitException tooMuch ? tooMuch : cannotRun(e.getCause());
		} catch (final IOException e) {
			end(process);
			throw cannotRun(e);
		} catch (final InterruptedException e) {
			end(process);
			Thread.currentThread().interrupt();
			throw new GitException("Interrupted while Git was running", e);
		}
	}

	private Process start(final Path root, final List<String> arguments) {
		final List<String> command = new ArrayList<>(List.of(executable, "--literal-pathspecs", "-C", root.toString()));
		command.addAll(arguments);
		final ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().clear();
		builder.environment().putAll(environment);
		try {
			return builder.start();
		} catch (final IOException e) {
			throw cannotRun(e);
		}
	}

	/** One stream of Git, read in its own thread so that neither can block Git; past the limit, Git is ended. */
	private Future<String> read(final InputStream stream, final Process process) {
		final FutureTask<String> task = new FutureTask<>(() -> {
			try (stream) {
				final byte[] bytes = stream.readNBytes(maxOutput + 1);
				if (bytes.length > maxOutput) {
					end(process);
					throw new GitException("Git gave more output than " + maxOutput + " bytes");
				}
				return new String(bytes, StandardCharsets.UTF_8);
			}
		});
		Thread.ofVirtual().start(task);
		return task;
	}

	/** Negative once the deadline has passed: then nothing waits any more. */
	private static long remaining(final long deadline) {
		return deadline - System.nanoTime();
	}

	private GitException cannotRun(final Throwable cause) {
		return new GitException("Cannot run " + executable + ": " + cause.getMessage(), cause);
	}

	/** Git and everything it started (a shell, SSH): taken while Git still runs, so none is missed. */
	private static void end(final Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}

	/**
	 * The environment for Git (T4): the inherited one, without the variables that would point Git to another
	 * repository, and never asking. SSH runs in batch mode unless the server sets its own SSH command.
	 */
	static Map<String, String> environment(final Map<String, String> inherited) {
		final Map<String, String> environment = new HashMap<>(inherited);
		REPOSITORY_VARIABLES.forEach(environment::remove);
		environment.put("GIT_TERMINAL_PROMPT", "0");
		if (!inherited.containsKey(SSH_COMMAND) && !inherited.containsKey("GIT_SSH")) {
			environment.put(SSH_COMMAND, "ssh -o BatchMode=yes");
		}
		return Map.copyOf(environment);
	}
}
