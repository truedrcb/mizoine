package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Step 10: the documented example stays a valid users file. */
@ExtendWith(OutputCaptureExtension.class)
class UsersExampleFileTest {

	private static final Path EXAMPLE = Path.of("users.example.yaml");

	@Test
	void exampleFileIsValid(final CapturedOutput output) {
		final FileUserDetailsService service = new FileUserDetailsService(EXAMPLE, new UsersFileParser());

		assertThat(service.loadUserByUsername("alice").getUsername()).isEqualTo("alice");
		assertThat(output.getOut()).doesNotContain("WARN");
	}
}
