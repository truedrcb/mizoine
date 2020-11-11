package com.gratchev.mizoine.repository2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Repository2Test {
	private static final Logger LOGGER = LoggerFactory.getLogger(Repository2Test.class);

	@BeforeAll
	static void setupRepos() throws IOException {
		LOGGER.info("Creating test repos");
		final Path testReposRoot = Files.createTempDirectory("miz-tes-repos");
	}

	@Test
	void projectsList() {
		
	}
}
