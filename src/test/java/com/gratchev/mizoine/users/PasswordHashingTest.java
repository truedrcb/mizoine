package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashingTest {

	private static final String ARTEM_HASH = "{bcrypt}$2a$04$0Lrt8DwJgJ44jVTKzCnmhu5LnOZEGAI2jdGylGLW/JgYQZ5r/oJ4m";

	@Test
	void encoderAcceptsRightPasswordOnly() {
		final PasswordEncoder encoder = PasswordHashing.encoder();

		assertThat(encoder.matches("artem-pass", ARTEM_HASH)).isTrue();
		assertThat(encoder.matches("wrong", ARTEM_HASH)).isFalse();
	}

	@Test
	void encoderCreatesBcryptHashes() {
		final String hash = PasswordHashing.encoder().encode("secret");

		// cost 12: also used by Spring Security for the dummy check of unknown users,
		// so that check takes as long as a real one
		assertThat(hash).startsWith("{bcrypt}$2a$12$");
		assertThat(PasswordHashing.isSupported(hash)).isTrue();
	}

	@Test
	void bcryptHashIsSupported() {
		assertThat(PasswordHashing.isSupported(ARTEM_HASH)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{bcrpyt}$2a$04$0Lrt8DwJgJ44jVTKzCnmhu5LnOZEGAI2jdGylGLW/JgYQZ5r/oJ4m", // typo in id
			"{noop}secret", // plain text
			"{pbkdf2}5d923b44a6d129f3ddf3e3c8d29412723dcbde72445e8ef6bf3b508fbf17fa4ed4d6b99ca763d8dc", // not enabled
			"{bcrypt}not-a-bcrypt-hash", // broken hash
			"{bcrypt}$2a$04$0Lrt8DwJgJ44jVTKzCnmhu5LnOZEGAI2jdGylGLW", // truncated by copy-paste
			"$2a$04$0Lrt8DwJgJ44jVTKzCnmhu5LnOZEGAI2jdGylGLW/JgYQZ5r/oJ4m" // no id
	})
	void otherHashesAreNotSupported(final String hash) {
		assertThat(PasswordHashing.isSupported(hash)).isFalse();
	}
}
