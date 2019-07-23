package com.gratchev.mizoine.repository2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.repository.RepositoryUtils;
import com.gratchev.mizoine.repository2.file.AdministratorImpl;

public class AdministratorTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(AdministratorTest.class);
	private AdministratorImpl admin;

	@BeforeEach
	void setup() throws IOException {
		LOGGER.info("Creating test repos");
		admin = new AdministratorImpl(Files.createTempDirectory("miz-tes-repos"));
	}

	@Test
	void createProject() throws IOException {
		assertThat(admin.getProjects()).isEmpty();
		admin.createProject("home");
		assertThatProjectIds(admin).hasSameElementsAs(List.of("home"));

		final AdministratorImpl admin2 = new AdministratorImpl(admin.getRootPath());
		assertThatProjectIds(admin2).as("New admin on the same root path must return same result")
				.hasSameElementsAs(List.of("home"));

		admin.createProject("work");
		assertThatProjectIds(admin).hasSameElementsAs(List.of("home", "work"));
		assertThatProjectIds(admin2).as("New admin on the same root path must return same result")
				.hasSameElementsAs(List.of("home", "work"));

		Files.createDirectories(admin.getRootPath().resolve("stuff"));
		assertThatProjectIds(admin).as("New directory must be interpreted as project")
				.hasSameElementsAs(List.of("home", "work", "stuff"));
		assertThatProjectIds(admin2).as("New admin on the same root path must return same result")
				.hasSameElementsAs(List.of("home", "work", "stuff"));

		RepositoryUtils.makeDirHidden(Files.createDirectories(admin.getRootPath().resolve(".hidden")));
		assertThatProjectIds(admin).as("Hidden directory must be ignored")
				.hasSameElementsAs(List.of("home", "work", "stuff"));
		assertThatProjectIds(admin2).as("New admin on the same root path must return same result")
				.hasSameElementsAs(List.of("home", "work", "stuff"));
	}

	private AbstractListAssert<?, List<? extends String>, String, ObjectAssert<String>> assertThatProjectIds(
			final AdministratorImpl admin) {
		return assertThat(admin.getProjects()).extracting(project -> project.getProjectId());
	}
}
