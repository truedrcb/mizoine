package com.gratchev.mizoine.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real Git repositories for tests, with Git cut off from the machine's settings: an own empty home, no system
 * settings, a fixed identity and branch name. Runs {@code git} directly, not through {@link GitCommands}.
 */
public final class TestGit {

	private final Map<String, String> environment;

	public TestGit(final Path home) throws IOException {
		Files.createDirectories(home);
		final Path settings = Files.writeString(home.resolve(".gitconfig"),
				"[user]\n\tname = Test\n\temail = test@example.org\n[init]\n\tdefaultBranch = main\n");
		final Map<String, String> variables = new HashMap<>();
		variables.put("PATH", System.getenv("PATH"));
		variables.put("HOME", home.toString());
		variables.put("GIT_CONFIG_GLOBAL", settings.toString());
		variables.put("GIT_CONFIG_NOSYSTEM", "1");
		this.environment = Map.copyOf(variables);
	}

	/** The environment for {@link GitCommands} in tests: the same isolation. */
	public Map<String, String> environment() {
		return environment;
	}

	/** Runs git in a folder; fails the test if git fails. */
	public String git(final Path folder, final String... arguments) throws IOException, InterruptedException {
		final List<String> command = new ArrayList<>(List.of("git", "-C", folder.toString()));
		command.addAll(List.of(arguments));
		final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
		builder.environment().clear();
		builder.environment().putAll(environment);
		final Process process = builder.start();
		final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor()).as(String.join(" ", arguments) + ": " + output).isZero();
		return output;
	}

	/** A new repository with one committed file {@code README.md}. */
	public Path repository(final Path folder) throws IOException, InterruptedException {
		Files.createDirectories(folder);
		git(folder, "init", "-q");
		Files.writeString(folder.resolve("README.md"), "readme\n");
		git(folder, "add", "README.md");
		git(folder, "commit", "-q", "-m", "first");
		return folder;
	}
}
