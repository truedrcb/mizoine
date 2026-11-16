package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;

/** Step 6: the application does not start with a missing or invalid users file. */
@ExtendWith(OutputCaptureExtension.class)
class UsersStartupTest {

	private final Path dir;
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(UsersConfig.class);

	UsersStartupTest(@TempDir final Path dir) {
		this.dir = dir;
	}

	@Test
	void propertyNotSetFailsWithPropertyName() {
		runner.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("mizoine.users-file"));
	}

	@Test
	void missingFileFailsWithPath() {
		final Path missing = dir.resolve("missing.yaml");

		runner.withPropertyValues("mizoine.users-file=" + missing)
				.run(context -> assertThat(usersFileException(context.getStartupFailure()))
						.hasMessageContaining(missing.toString()));
	}

	@Test
	void invalidFileFails() throws IOException {
		final Path file = Files.writeString(dir.resolve("users.yaml"), "users:\n  - username: artem\n    password: plain\n");

		runner.withPropertyValues("mizoine.users-file=" + file)
				.run(context -> assertThat(usersFileException(context.getStartupFailure()))
						.hasMessageContaining("artem"));
	}

	private static Throwable usersFileException(final Throwable failure) {
		Throwable cause = failure;
		while (cause != null && !(cause instanceof UsersFileException)) {
			cause = cause.getCause();
		}
		return cause;
	}

	@Test
	void validFileStarts() {
		runner.withPropertyValues("mizoine.users-file=src/test/resources/users-test.yaml")
				.run(context -> assertThat(context).hasNotFailed().hasSingleBean(UserDetailsService.class));
	}

	@Test
	void fileWithZeroUsersStartsWithWarning(final CapturedOutput output) throws IOException {
		final Path file = Files.writeString(dir.resolve("users.yaml"), "users: []\n");

		runner.withPropertyValues("mizoine.users-file=" + file)
				.run(context -> assertThat(context).hasNotFailed());

		assertThat(output.getOut())
				.contains("WARN")
				.contains("no users")
				.contains(file.toString());
	}
}
