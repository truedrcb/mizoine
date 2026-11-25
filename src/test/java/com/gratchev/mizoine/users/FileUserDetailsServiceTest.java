package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class FileUserDetailsServiceTest {

	private static final String ARTEM_HASH = "{bcrypt}$2a$04$0Lrt8DwJgJ44jVTKzCnmhu5LnOZEGAI2jdGylGLW/JgYQZ5r/oJ4m";

	private final Path dir;
	private FileUserDetailsService service;

	FileUserDetailsServiceTest(@TempDir final Path dir) {
		this.dir = dir;
	}

	@BeforeEach
	void setUp() throws IOException {
		final Path file = Files.writeString(dir.resolve("users.yaml"), """
				users:
				  - username: artem
				    password: "%s"
				    email: artem@example.org
				""".formatted(ARTEM_HASH));
		service = new FileUserDetailsService(file, new UsersFileParser());
	}

	@Test
	void theEmailOfAUser() {
		assertThat(service.email("artem")).contains("artem@example.org");
		assertThat(service.email("nobody")).isEmpty();
	}

	@Test
	void knownUserHasHashAndRoleUser() {
		final UserDetails user = service.loadUserByUsername("artem");

		assertThat(user.getUsername()).isEqualTo("artem");
		assertThat(user.getPassword()).isEqualTo(ARTEM_HASH);
		assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
		assertThat(user.isEnabled()).isTrue();
	}

	@Test
	void unknownUserIsNotFound() {
		assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void usernameIsCaseSensitive() {
		assertThatThrownBy(() -> service.loadUserByUsername("Artem"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void unsupportedHashIsRejectedOnLoadWithUsernameButWithoutHash() throws IOException {
		final String misspelled = "{bcrpyt}$2a$04$0Lrt8DwJgJ44jVTKzCnmhu5LnOZEGAI2jdGylGLW/JgYQZ5r/oJ4m";
		final Path file = Files.writeString(dir.resolve("bad.yaml"), """
				users:
				  - username: guest
				    password: "%s"
				""".formatted(misspelled));

		assertThatThrownBy(() -> new FileUserDetailsService(file, new UsersFileParser()))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("guest")
				.hasMessageContaining("{bcrypt}")
				.hasMessageNotContaining(misspelled);
	}

	@Test
	void erasingCredentialsOfOneResultDoesNotAffectTheNext() {
		// Spring Security erases the password after a successful login.
		// A cached UserDetails instance would make every later login fail.
		final UserDetails first = service.loadUserByUsername("artem");
		((CredentialsContainer) first).eraseCredentials();

		assertThat(service.loadUserByUsername("artem").getPassword()).isEqualTo(ARTEM_HASH);
	}
}
