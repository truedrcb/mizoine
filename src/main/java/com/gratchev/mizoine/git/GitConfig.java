package com.gratchev.mizoine.git;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gratchev.mizoine.MizoineProperties;

/** Git in the browser (steps 43–48): the server's {@code git}, with the server's settings and credentials (T4). */
@Configuration
class GitConfig {

	/** Git's output is kept to 16 MB, for the output and Git's messages each: a status of very many files fits. */
	private static final int MAX_OUTPUT = 16 * 1024 * 1024;

	@Bean
	GitCommands gitCommands(final MizoineProperties properties) {
		return new GitCommands("git", properties.git().timeout(), MAX_OUTPUT, System.getenv());
	}
}
