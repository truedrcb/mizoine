package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.jupiter.api.Test;

/** Step 11: {@code java -jar mizoine.jar hash-password}. */
class HashPasswordCommandTest {

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	/** Returns the given answers in order, then {@code null} (end of input). */
	private static HashPasswordCommand.PasswordReader answers(final String... answers) {
		final Deque<String> queue = new ArrayDeque<>(Arrays.asList(answers));
		return prompt -> queue.poll();
	}

	private int run(final boolean confirm, final String... answers) {
		return new HashPasswordCommand(answers(answers), confirm,
				new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8)).run();
	}

	private String out() {
		return out.toString(StandardCharsets.UTF_8);
	}

	private String err() {
		return err.toString(StandardCharsets.UTF_8);
	}

	@Test
	void printsBcryptHashThatMatchesThePassword() {
		final int exitCode = run(false, "my secret");

		final String hash = out().strip();
		assertThat(exitCode).isZero();
		assertThat(hash).startsWith("{bcrypt}$2a$12$");
		assertThat(PasswordHashing.isSupported(hash)).isTrue();
		assertThat(PasswordHashing.encoder().matches("my secret", hash)).isTrue();
	}

	@Test
	void printsOnlyOneLineSoTheResultCanBeCaptured() {
		run(false, "my secret");

		assertThat(out().lines()).hasSize(1);
	}

	@Test
	void neverPrintsThePassword() {
		run(true, "my secret", "my secret");

		assertThat(out()).doesNotContain("my secret");
		assertThat(err()).doesNotContain("my secret");
	}

	@Test
	void keepsLeadingAndTrailingSpaces() {
		run(false, "  pass word ");

		final String hash = out().strip();
		assertThat(PasswordHashing.encoder().matches("  pass word ", hash)).isTrue();
		assertThat(PasswordHashing.encoder().matches("pass word", hash)).isFalse();
	}

	@Test
	void emptyPasswordIsRejected() {
		final int exitCode = run(false, "");

		assertThat(exitCode).isNotZero();
		assertThat(out()).isEmpty();
		assertThat(err()).contains("empty");
	}

	@Test
	void missingInputIsRejected() {
		final int exitCode = run(false);

		assertThat(exitCode).isNotZero();
		assertThat(out()).isEmpty();
	}

	@Test
	void matchingConfirmationIsAccepted() {
		assertThat(run(true, "my secret", "my secret")).isZero();
	}

	@Test
	void differentConfirmationIsRejected() {
		final int exitCode = run(true, "my secret", "my secrte");

		assertThat(exitCode).isNotZero();
		assertThat(out()).isEmpty();
		assertThat(err()).contains("do not match");
	}

	@Test
	void isRequestedOnlyByFirstArgument() {
		assertThat(HashPasswordCommand.isRequested(new String[] {"hash-password"})).isTrue();
		assertThat(HashPasswordCommand.isRequested(new String[] {})).isFalse();
		assertThat(HashPasswordCommand.isRequested(new String[] {"--server.port=9000"})).isFalse();
		assertThat(HashPasswordCommand.isRequested(new String[] {"--x", "hash-password"})).isFalse();
	}
}
