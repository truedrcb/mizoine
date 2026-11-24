package com.gratchev.mizoine.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
	private static final int BUFFER = 8192;

	private final String executable;
	private final Duration timeout;
	private final long maxOutput;
	private final Map<String, String> environment;

	/**
	 * @param executable {@code git}, found on the {@code PATH}
	 * @param maxOutput bytes, for the output and for Git's messages each
	 * @param inherited the environment to start from, usually the server's ({@link System#getenv()})
	 */
	public GitCommands(final String executable, final Duration timeout, final long maxOutput,
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
		final List<String> command = new ArrayList<>(List.of(executable, "--literal-pathspecs", "-C", root.toString()));
		command.addAll(arguments);
		final ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().clear();
		builder.environment().putAll(environment);
		final Process process;
		try {
			process = builder.start();
		} catch (final IOException e) {
			throw new GitException("Cannot run " + executable + ": " + e.getMessage(), e);
		}
		try {
			// nothing to answer: whatever Git or SSH would ask is not asked
			process.getOutputStream().close();
			final Collector output = Collector.start(process.getInputStream(), maxOutput, process);
			final Collector errors = Collector.start(process.getErrorStream(), maxOutput, process);
			final long deadline = System.nanoTime() + timeout.toNanos();
			if (!process.waitFor(timeout) || !output.finishedBy(deadline) || !errors.finishedBy(deadline)) {
				end(process);
				throw new GitTimeoutException("Git did not finish within " + timeout.toSeconds() + " s: "
						+ String.join(" ", arguments));
			}
			if (output.isTooLarge() || errors.isTooLarge()) {
				throw new GitException("Git gave more output than " + maxOutput + " bytes: " + String.join(" ", arguments));
			}
			return new Result(process.exitValue(), output.text(), errors.text());
		} catch (final IOException e) {
			end(process);
			throw new GitException("Cannot run " + executable + ": " + e.getMessage(), e);
		} catch (final InterruptedException e) {
			end(process);
			Thread.currentThread().interrupt();
			throw new GitException("Interrupted while Git was running", e);
		}
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

	/** Reads one stream of Git in its own thread, so neither stream can block Git; past the limit, Git is ended. */
	private static final class Collector implements Runnable {

		private final InputStream stream;
		private final long limit;
		private final Process process;
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private Thread thread;
		private volatile boolean tooLarge;
		private volatile IOException failure;

		private Collector(final InputStream stream, final long limit, final Process process) {
			this.stream = stream;
			this.limit = limit;
			this.process = process;
		}

		static Collector start(final InputStream stream, final long limit, final Process process) {
			final Collector collector = new Collector(stream, limit, process);
			collector.thread = Thread.ofVirtual().start(collector);
			return collector;
		}

		@Override
		public void run() {
			final byte[] buffer = new byte[BUFFER];
			try (stream) {
				for (int read = stream.read(buffer); read >= 0; read = stream.read(buffer)) {
					if (bytes.size() + read > limit) {
						tooLarge = true;
						end(process);
						return;
					}
					bytes.write(buffer, 0, read);
				}
			} catch (final IOException e) {
				failure = e;
			}
		}

		/** @return false if the stream is still open at the deadline (something Git started still writes) */
		boolean finishedBy(final long deadline) throws InterruptedException {
			return thread.join(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
		}

		boolean isTooLarge() {
			return tooLarge;
		}

		String text() throws IOException {
			if (failure != null) {
				throw failure;
			}
			return bytes.toString(StandardCharsets.UTF_8);
		}
	}
}
