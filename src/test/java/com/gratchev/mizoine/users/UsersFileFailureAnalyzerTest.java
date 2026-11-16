package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class UsersFileFailureAnalyzerTest {

	private final UsersFileFailureAnalyzer analyzer = new UsersFileFailureAnalyzer();

	@Test
	void explainsUsersFileProblemFoundInsideBeanCreationFailure() {
		final Throwable failure = new BeanCreationException("userDetailsService",
				"Error creating bean", new UsersFileException("Users file not found: /x/users.yaml"));

		final FailureAnalysis analysis = analyzer.analyze(failure);

		assertThat(analysis).isNotNull();
		assertThat(analysis.getDescription()).contains("Users file not found: /x/users.yaml");
		assertThat(analysis.getAction()).contains("mizoine.users-file").contains("users.example.yaml");
	}

	@Test
	void ignoresOtherFailures() {
		assertThat(analyzer.analyze(new IllegalStateException("something else"))).isNull();
	}

	@Test
	void isRegisteredWithSpringBoot() throws IOException {
		// read the registrations only: instantiating all analyzers needs Spring Boot's own wiring
		final List<String> registered = new ArrayList<>();
		for (final URL url : Collections.list(getClass().getClassLoader().getResources("META-INF/spring.factories"))) {
			final Properties factories = PropertiesLoaderUtils.loadProperties(new UrlResource(url));
			registered.add(factories.getProperty(FailureAnalyzer.class.getName(), ""));
		}

		assertThat(registered).anyMatch(analyzers -> analyzers.contains(UsersFileFailureAnalyzer.class.getName()));
	}
}
