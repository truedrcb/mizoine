package com.gratchev.mizoine;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 * @param oauth the OAuth clients for chat apps ({@code mizoine.oauth.clients}, step 34); OAuth is off without any
 * @param git Git in the browser ({@code mizoine.git.*}, steps 43–48)
 */
@Validated
@ConfigurationProperties("mizoine")
public record MizoineProperties(
		@NotBlank(message = "set mizoine.users-file to the path of the users YAML file") String usersFile,
		@Valid @DefaultValue LoginLimit loginLimit,
		@NotBlank @DefaultValue(".") String repositoriesDir,
		@NotNull @DefaultValue("100MB") DataSize maxFileSize,
		String publicUrl,
		@Valid @DefaultValue OAuth oauth,
		@Valid @DefaultValue Git git) {

	/** {@code mizoine.users-file}, relative to the working directory. */
	public Path usersFilePath() {
		return Path.of(usersFile);
	}

	/** {@code mizoine.repositories-dir}, relative to the working directory. */
	public Path repositoriesDirPath() {
		return Path.of(repositoriesDir);
	}

	/** @return whether OAuth for chat apps is configured: at least one client */
	public boolean oauthEnabled() {
		return !oauth.clients().isEmpty();
	}

	/** D17 (option 1): one client per chat app, each with its own secret, so each can be shut out on its own. */
	public record OAuth(@DefaultValue List<@Valid Client> clients) {

		@AssertTrue(message = "the ids of mizoine.oauth.clients must be unique")
		public boolean isEachIdOnce() {
			return clients.stream().map(Client::id).filter(Objects::nonNull).distinct().count()
					== clients.stream().map(Client::id).filter(Objects::nonNull).count();
		}
	}

	/**
	 * A chat app's OAuth client ("Use your own OAuth client" in Claude).
	 *
	 * @param id e.g. {@code claude}
	 * @param secret only its hash, {@code {bcrypt}...} (create it with {@code hash-password}), never the secret itself
	 * @param redirectUris where the chat app receives the code, e.g. {@code https://claude.ai/api/mcp/auth_callback}
	 */
	public record Client(
			@NotBlank(message = "each of mizoine.oauth.clients needs an id") String id,
			@NotBlank(message = "each of mizoine.oauth.clients needs a secret")
			@Pattern(regexp = "^\\{bcrypt}.+", message = "mizoine.oauth.clients[].secret must be a bcrypt hash "
					+ "({bcrypt}..., from hash-password), never the secret itself") String secret,
			@NotEmpty(message = "each of mizoine.oauth.clients needs redirect-uris") List<String> redirectUris) {
	}

	/**
	 * @param maxFailures failed logins after which the address is blocked
	 * @param window how long failures are remembered, and how long the block lasts after the last failure
	 */
	/** @param timeout how long one Git command may take (a pull or push waits for the remote), then it is ended */
	public record Git(@NotNull @DefaultValue("60s") Duration timeout) {
	}

	public record LoginLimit(
			@Positive @DefaultValue("5") int maxFailures,
			@NotNull @DefaultValue("5m") Duration window) {
	}
}
