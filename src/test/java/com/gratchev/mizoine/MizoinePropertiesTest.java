package com.gratchev.mizoine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class MizoinePropertiesTest {

	@Configuration
	@EnableConfigurationProperties(MizoineProperties.class)
	static class PropertiesOnly {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(PropertiesOnly.class);

	@Test
	void bindsUsersFile() {
		runner.withPropertyValues("mizoine.users-file=/some/dir/users.yaml")
				.run(context -> assertThat(context.getBean(MizoineProperties.class).usersFilePath())
						.isEqualTo(Path.of("/some/dir/users.yaml")));
	}

	@Test
	void pathsAreFileSystemPathsNotClasspathResources() {
		// application.yaml exists on the classpath: Spring's default conversion would resolve it there
		runner.withPropertyValues("mizoine.users-file=application.yaml")
				.run(context -> assertThat(context.getBean(MizoineProperties.class).usersFilePath())
						.isEqualTo(Path.of("application.yaml")));
	}

	@Test
	void repositoriesDirIsTheCurrentDirectoryByDefault() {
		runner.withPropertyValues("mizoine.users-file=users.yaml")
				.run(context -> assertThat(context.getBean(MizoineProperties.class).repositoriesDirPath())
						.isEqualTo(Path.of(".")));
	}

	@Test
	void bindsRepositoriesDir() {
		runner.withPropertyValues("mizoine.users-file=users.yaml", "mizoine.repositories-dir=/srv/repositories")
				.run(context -> assertThat(context.getBean(MizoineProperties.class).repositoriesDirPath())
						.isEqualTo(Path.of("/srv/repositories")));
	}

	@Test
	void maxFileSizeIs100MegabytesByDefault() {
		runner.withPropertyValues("mizoine.users-file=users.yaml")
				.run(context -> assertThat(context.getBean(MizoineProperties.class).maxFileSize())
						.isEqualTo(DataSize.ofMegabytes(100)));
	}

	@Test
	void bindsMaxFileSize() {
		runner.withPropertyValues("mizoine.users-file=users.yaml", "mizoine.max-file-size=1GB")
				.run(context -> assertThat(context.getBean(MizoineProperties.class).maxFileSize())
						.isEqualTo(DataSize.ofGigabytes(1)));
	}

	@Test
	void loginLimitHasDefaults() {
		runner.withPropertyValues("mizoine.users-file=users.yaml")
				.run(context -> {
					final MizoineProperties.LoginLimit limit = context.getBean(MizoineProperties.class).loginLimit();
					assertThat(limit.maxFailures()).isEqualTo(5);
					assertThat(limit.window()).isEqualTo(Duration.ofMinutes(5));
				});
	}

	@Test
	void loginLimitCanBeChanged() {
		runner.withPropertyValues("mizoine.users-file=users.yaml",
				"mizoine.login-limit.max-failures=10", "mizoine.login-limit.window=30s")
				.run(context -> {
					final MizoineProperties.LoginLimit limit = context.getBean(MizoineProperties.class).loginLimit();
					assertThat(limit.maxFailures()).isEqualTo(10);
					assertThat(limit.window()).isEqualTo(Duration.ofSeconds(30));
				});
	}

	@Test
	void missingUsersFileFailsWithPropertyName() {
		runner.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("mizoine.users-file"));
	}
}
