package com.gratchev.mizoine;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Application settings, prefix {@code mizoine}.
 *
 * @param usersFile path to the users YAML file ({@code mizoine.users-file}), required
 */
@Validated
@ConfigurationProperties("mizoine")
public record MizoineProperties(
		@NotNull(message = "set mizoine.users-file to the path of the users YAML file") Path usersFile) {
}
