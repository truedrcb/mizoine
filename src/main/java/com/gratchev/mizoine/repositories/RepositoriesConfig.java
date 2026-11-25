package com.gratchev.mizoine.repositories;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.users.KnownUsers;

/**
 * Repositories from {@code mizoine.repositories-dir} (default: the current directory). Nothing is read at
 * startup: files are looked up on request. No repositories is not an error. The users file is never a repository.
 */
@Configuration
public class RepositoriesConfig {

	private static final Logger log = LoggerFactory.getLogger(RepositoriesConfig.class);

	@Bean
	RepositoryRegistry repositoryRegistry(final MizoineProperties properties, final KnownUsers users) {
		final Path dir = properties.repositoriesDirPath();
		if (!Files.isDirectory(dir)) {
			log.warn("Repositories folder {} does not exist (yet): no repositories until it does", dir);
		}
		return new RepositoryRegistry(dir, new RepositoryFileParser(),
				users::exists, properties.usersFilePath());
	}

	@Bean
	RepositoryFiles repositoryFiles(final RepositoryRegistry registry, final RepositoryWriter writer) {
		return new RepositoryFiles(registry, writer);
	}

	/** One instance: it allows one writer at a time (version check and move). */
	@Bean
	RepositoryWriter repositoryWriter() {
		return new RepositoryWriter();
	}
}
