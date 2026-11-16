package com.gratchev.mizoine;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Application settings, prefix {@code mizoine}.
 *
 * @param usersFile path to the users YAML file ({@code mizoine.users-file}), required
 * @param loginLimit limit for failed logins per client address ({@code mizoine.login-limit.*})
 */
@Validated
@ConfigurationProperties("mizoine")
public record MizoineProperties(
		@NotNull(message = "set mizoine.users-file to the path of the users YAML file") Path usersFile,
		@Valid @DefaultValue LoginLimit loginLimit) {

	/**
	 * @param maxFailures failed logins after which the address is blocked
	 * @param window how long failures are remembered, and how long the block lasts after the last failure
	 */
	public record LoginLimit(
			@Positive @DefaultValue("5") int maxFailures,
			@NotNull @DefaultValue("5m") Duration window) {
	}
}
