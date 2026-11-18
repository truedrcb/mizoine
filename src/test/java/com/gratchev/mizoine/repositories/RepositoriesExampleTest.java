package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.gratchev.mizoine.users.FileUserDetailsService;
import com.gratchev.mizoine.users.UsersFileParser;

/** Step 22: the documented example repositories stay valid, together with the example users file. */
@ExtendWith(OutputCaptureExtension.class)
class RepositoriesExampleTest {

	private static final Path EXAMPLE = Path.of("repositories.example");

	private final FileUserDetailsService users =
			new FileUserDetailsService(Path.of("users.example.yaml"), new UsersFileParser());
	private final RepositoryRegistry registry = new RepositoryRegistry(EXAMPLE, new RepositoryFileParser(),
			this::userExists, Path.of("users.example.yaml"));

	private boolean userExists(final String username) {
		try {
			users.loadUserByUsername(username);
			return true;
		} catch (final UsernameNotFoundException e) {
			return false;
		}
	}

	@Test
	void exampleRepositoryIsValidAndWarnsAboutNothing(final CapturedOutput output) {
		final RepositoryConfig notes = registry.find("notes").orElseThrow();

		assertThat(notes.problems()).isEmpty();
		assertThat(registry.all()).extracting(RepositoryConfig::name).containsExactly("notes");
		assertThat(output.getOut()).doesNotContain("WARN").doesNotContain("ERROR");
	}

	@Test
	void exampleUserSeesTheWholeExampleTree() {
		final RepositoryConfig notes = registry.find("notes").orElseThrow();
		final RepositoryAccess alice = RepositoryAccess.of(notes, "alice");

		assertThat(alice.isWholeRepository()).isTrue();
		assertThat(RepositoryPaths.childFolders(notes.root(), List.of())).containsExactly("projects", "public");
		assertThat(RepositoryPaths.childFolders(notes.root(), List.of("projects"))).containsExactly("shared");
	}
}
