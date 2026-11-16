package com.gratchev.mizoine.users;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

/**
 * {@code java -jar mizoine.jar hash-password}: prints a password hash for the users file,
 * without starting the web server.
 * <p>
 * In a terminal the password is asked twice and not shown. Piped input ({@code echo ... | java -jar ...})
 * is read once. Only the hash goes to standard output; messages go to standard error.
 */
public class HashPasswordCommand {

	public static final String NAME = "hash-password";

	/** Reads one password; returns {@code null} at end of input. */
	@FunctionalInterface
	public interface PasswordReader {
		String read(String prompt);
	}

	private final PasswordReader reader;
	private final boolean confirm;
	private final PrintStream out;
	private final PrintStream err;

	public HashPasswordCommand(final PasswordReader reader, final boolean confirm, final PrintStream out,
			final PrintStream err) {
		this.reader = reader;
		this.confirm = confirm;
		this.out = out;
		this.err = err;
	}

	/** Reads from the terminal when there is one, otherwise from standard input. */
	public static HashPasswordCommand fromSystem() {
		final Console console = System.console();
		if (console != null && console.isTerminal()) {
			return new HashPasswordCommand(prompt -> {
				final char[] password = console.readPassword("%s", prompt);
				return password == null ? null : new String(password);
			}, true, System.out, System.err);
		}
		final BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
		return new HashPasswordCommand(prompt -> {
			try {
				return stdin.readLine();
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}, false, System.out, System.err);
	}

	public static boolean isRequested(final String[] args) {
		return args.length > 0 && NAME.equals(args[0]);
	}

	/** @return process exit code: 0 on success */
	public int run() {
		final String password = reader.read("Password: ");
		if (password == null || password.isEmpty()) {
			err.println("No password given: the password must not be empty.");
			return 1;
		}
		if (confirm && !password.equals(reader.read("Repeat password: "))) {
			err.println("The passwords do not match.");
			return 1;
		}
		out.println(PasswordHashing.encoder().encode(password));
		return 0;
	}
}
