package com.gratchev.mizoine.repositories;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.gratchev.mizoine.MizoineProperties;

/**
 * Repositories from {@code mizoine.repositories-dir} (default: the current directory). Nothing is read at
 * startup: files are looked up on request. No repositories is not an error. The users file is never a repository.
 */
@Configuration
public class RepositoriesConfig {

	private static final Logger log = LoggerFactory.getLogger(RepositoriesConfig.class);

	@Bean
	RepositoryRegistry repositoryRegistry(final MizoineProperties properties, final UserDetailsService users) {
		final Path dir = properties.repositoriesDirPath();
		if (!Files.isDirectory(dir)) {
			log.warn("Repositories folder {} does not exist (yet): no repositories until it does", dir);
		}
		return new RepositoryRegistry(dir, new RepositoryFileParser(),
				username -> exists(users, username), properties.usersFilePath());
	}

	private static boolean exists(final UserDetailsService users, final String username) {
		try {
			users.loadUserByUsername(username);
			return true;
		} catch (final UsernameNotFoundException e) {
			return false;
		}
	}
}
