package com.gratchev.mizoine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
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
				.run(context -> assertThat(context.getBean(MizoineProperties.class).usersFile())
						.isEqualTo(Path.of("/some/dir/users.yaml")));
	}

	@Test
	void missingUsersFileFailsWithPropertyName() {
		runner.run(context -> assertThat(context).hasFailed()
				.getFailure().rootCause().hasMessageContaining("mizoine.users-file"));
	}
}
