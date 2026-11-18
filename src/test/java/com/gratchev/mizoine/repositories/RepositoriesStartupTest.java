package com.gratchev.mizoine.repositories;

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

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.users.UsersConfig;

/** Step 16: no repositories configured is not an error; nothing is read at startup. */
@ExtendWith(OutputCaptureExtension.class)
class RepositoriesStartupTest {

	private final Path dir;
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(UsersConfig.class, RepositoriesConfig.class)
			.withPropertyValues("mizoine.users-file=src/test/resources/users-test.yaml");

	RepositoriesStartupTest(@TempDir final Path dir) {
		this.dir = dir;
	}

	@Test
	void propertyNotSetUsesTheCurrentDirectoryWithoutWarning(final CapturedOutput output) {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(MizoineProperties.class).repositoriesDirPath()).isEqualTo(Path.of("."));
		});

		assertThat(output.getOut()).doesNotContain("WARN");
	}

	@Test
	void usersFileInTheRepositoriesFolderIsNotARepository() throws IOException {
		final Path usersFile = Files.copy(Path.of("src/test/resources/users-test.yaml"), dir.resolve("users.yaml"));

		runner.withPropertyValues("mizoine.users-file=" + usersFile, "mizoine.repositories-dir=" + dir)
				.run(context -> {
					final RepositoryRegistry registry = context.getBean(RepositoryRegistry.class);
					assertThat(registry.find("users")).isEmpty();
					assertThat(registry.all()).isEmpty();
				});
	}

	@Test
	void missingFolderStartsWithWarning(final CapturedOutput output) {
		final Path missing = dir.resolve("missing");

		runner.withPropertyValues("mizoine.repositories-dir=" + missing)
				.run(context -> assertThat(context).hasNotFailed());

		assertThat(output.getOut()).contains("WARN").contains(missing.toString());
	}

	@Test
	void emptyFolderStartsWithoutWarning(final CapturedOutput output) {
		runner.withPropertyValues("mizoine.repositories-dir=" + dir)
				.run(context -> assertThat(context).hasNotFailed());

		assertThat(output.getOut()).doesNotContain("WARN");
	}

	@Test
	void filesAreNotReadAtStartupEvenIfBroken(final CapturedOutput output) throws IOException {
		Files.writeString(dir.resolve("broken.yaml"), "access: [broken\n");

		runner.withPropertyValues("mizoine.repositories-dir=" + dir)
				.run(context -> assertThat(context).hasNotFailed());

		assertThat(output.getOut()).doesNotContain("broken.yaml");
	}

	@Test
	void unknownUserWarningUsesTheUsersFile(final CapturedOutput output) throws IOException {
		Files.createDirectory(dir.resolve("data"));
		Files.writeString(dir.resolve("notes.yaml"), "path: data\naccess:\n  artem: [\"/\"]\n  nobody: [public]\n");

		runner.withPropertyValues("mizoine.repositories-dir=" + dir)
				.run(context -> assertThat(context.getBean(RepositoryRegistry.class).find("notes")).isPresent());

		assertThat(output.getOut()).contains("'nobody'").doesNotContain("'artem'");
	}
}
