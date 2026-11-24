package com.gratchev.mizoine;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Application settings, prefix {@code mizoine}.
 *
 * Paths are bound as text and turned into {@link Path}s here with {@link Path#of}: Spring's own conversion
 * would first look for the text on the classpath ({@code "."}, {@code "application.yaml"}), not in the
 * working directory.
 *
 * @param usersFile path to the users YAML file ({@code mizoine.users-file}), required
 * @param loginLimit limit for failed logins per client address ({@code mizoine.login-limit.*})
 * @param repositoriesDir folder with one {@code <name>.yaml} per repository ({@code mizoine.repositories-dir});
 *            default: the current directory
 * @param maxFileSize the largest file that can be written over HTTP ({@code mizoine.max-file-size}, D12)
 * @param publicUrl the address users and chat apps reach Mizoine at, e.g. {@code https://mizoine.example.org}
 *            ({@code mizoine.public-url}); needed for OAuth, because behind a proxy the request's host may be wrong
 * @param oauth the one OAuth client for chat apps ({@code mizoine.oauth.*}, step 34); OAuth is off without it
 */
@Validated
@ConfigurationProperties("mizoine")
public record MizoineProperties(
		@NotBlank(message = "set mizoine.users-file to the path of the users YAML file") String usersFile,
		@Valid @DefaultValue LoginLimit loginLimit,
		@NotBlank @DefaultValue(".") String repositoriesDir,
		@NotNull @DefaultValue("100MB") DataSize maxFileSize,
		String publicUrl,
		@Valid @DefaultValue OAuth oauth) {

	/** {@code mizoine.users-file}, relative to the working directory. */
	public Path usersFilePath() {
		return Path.of(usersFile);
	}

	/** {@code mizoine.repositories-dir}, relative to the working directory. */
	public Path repositoriesDirPath() {
		return Path.of(repositoriesDir);
	}

	/** @return whether OAuth for chat apps is configured: a client and the public address */
	public boolean oauthEnabled() {
		return oauth.clientId() != null && !oauth.clientId().isBlank();
	}

	/**
	 * D17: the one client a chat app is configured with ("Use your own OAuth client" in Claude).
	 *
	 * @param clientId e.g. {@code claude}; empty = OAuth off
	 * @param clientSecret only its hash, {@code {bcrypt}...} (create it with {@code hash-password})
	 * @param redirectUris where the chat app receives the code, e.g. {@code https://claude.ai/api/mcp/auth_callback}
	 */
	public record OAuth(String clientId, String clientSecret, @DefaultValue List<String> redirectUris) {
	}

	/**
	 * @param maxFailures failed logins after which the address is blocked
	 * @param window how long failures are remembered, and how long the block lasts after the last failure
	 */
	public record LoginLimit(
			@Positive @DefaultValue("5") int maxFailures,
			@NotNull @DefaultValue("5m") Duration window) {
	}
}
