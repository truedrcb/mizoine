package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Step 9: edits to the users file take effect without a restart. */
@ExtendWith(OutputCaptureExtension.class)
class FileUserDetailsServiceReloadTest {

	private static final String ARTEM = """
			  - username: artem
			    password: "{bcrypt}$2a$04$0Lrt8DwJgJ44jVTKzCnmhu5LnOZEGAI2jdGylGLW/JgYQZ5r/oJ4m"
			""";
	private static final String GUEST = """
			  - username: guest
			    password: "{bcrypt}$2a$04$gErjw5GH5tGA5QFqNBbDS.cfGqRdaFw1fgnE1C.jBHcH063E.5YSW"
			""";

	/** Counts how often the file is parsed. */
	static class CountingParser extends UsersFileParser {
		final AtomicInteger calls = new AtomicInteger();

		@Override
		public List<UserEntry> parse(final Path file) {
			calls.incrementAndGet();
			return super.parse(file);
		}
	}

	private final Path file;
	private final CountingParser parser = new CountingParser();
	private FileUserDetailsService service;
	private long fakeTime = 1_700_000_000L;

	FileUserDetailsServiceReloadTest(@TempDir final Path dir) {
		this.file = dir.resolve("users.yaml");
	}

	/** Writes the file and moves its modification time forward, independent of file system time resolution. */
	private void writeUsers(final String content) throws IOException {
		Files.writeString(file, content);
		fakeTime += 10;
		Files.setLastModifiedTime(file, FileTime.from(Instant.ofEpochSecond(fakeTime)));
	}

	@BeforeEach
	void setUp() throws IOException {
		writeUsers("users:\n" + ARTEM);
		service = new FileUserDetailsService(file, parser);
	}

	@Test
	void addedUserCanLogIn() throws IOException {
		writeUsers("users:\n" + ARTEM + GUEST);

		assertThat(service.loadUserByUsername("guest").getUsername()).isEqualTo("guest");
	}

	@Test
	void removedUserIsNotFound() throws IOException {
		writeUsers("users:\n" + GUEST);

		assertThatThrownBy(() -> service.loadUserByUsername("artem")).isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void invalidChangeKeepsOldUsersAndLogsError(final CapturedOutput output) throws IOException {
		writeUsers("users:\n  - username: guest\n    password: plain-secret\n");

		assertThat(service.loadUserByUsername("artem").getUsername()).isEqualTo("artem");
		assertThatThrownBy(() -> service.loadUserByUsername("guest")).isInstanceOf(UsernameNotFoundException.class);
		assertThat(output.getOut())
				.contains("ERROR")
				.contains(file.toString())
				.contains("guest")
				.doesNotContain("plain-secret");
	}

	@Test
	void unsupportedHashInChangeKeepsOldUsers() throws IOException {
		writeUsers("users:\n" + GUEST.replace("{bcrypt}", "{bcrpyt}"));

		assertThat(service.loadUserByUsername("artem").getUsername()).isEqualTo("artem");
	}

	@Test
	void invalidChangeIsLoggedOnlyOnce(final CapturedOutput output) throws IOException {
		writeUsers("users: [broken\n");

		service.loadUserByUsername("artem");
		service.loadUserByUsername("artem");
		service.loadUserByUsername("artem");

		assertThat(output.getOut().lines().filter(line -> line.contains("ERROR"))).hasSize(1);
	}

	@Test
	void fixingTheFileAfterInvalidChangeTakesEffect() throws IOException {
		writeUsers("users: [broken\n");
		service.loadUserByUsername("artem");

		writeUsers("users:\n" + GUEST);

		assertThat(service.loadUserByUsername("guest").getUsername()).isEqualTo("guest");
		assertThatThrownBy(() -> service.loadUserByUsername("artem")).isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void deletedFileKeepsOldUsersAndLogsError(final CapturedOutput output) throws IOException {
		Files.delete(file);

		assertThat(service.loadUserByUsername("artem").getUsername()).isEqualTo("artem");
		assertThat(output.getOut()).contains("ERROR").contains(file.toString());
	}

	@Test
	void unchangedFileIsNotReadAgain() {
		final int afterStartup = parser.calls.get();

		service.loadUserByUsername("artem");
		service.loadUserByUsername("artem");
		assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class);

		assertThat(afterStartup).isEqualTo(1);
		assertThat(parser.calls.get()).isEqualTo(1);
	}
}
