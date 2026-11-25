package com.gratchev.mizoine.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class UsersFileParserTest {

	private static final String HASH_1 = "{bcrypt}$2a$12$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234";
	private static final String HASH_2 = "{bcrypt}$2a$12$zyxwvutsrqponmlkjihgfeuzyxwvutsrqponmlkjihgfedcba98765";

	private final Path dir;
	private final UsersFileParser parser = new UsersFileParser();

	UsersFileParserTest(@TempDir final Path dir) {
		this.dir = dir;
	}

	private Path write(final String yaml) throws IOException {
		return Files.writeString(dir.resolve("users.yaml"), yaml);
	}

	@Test
	void parsesValidFileWithTwoUsers() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "%s"
				  - username: guest
				    password: "%s"
				""".formatted(HASH_1, HASH_2));

		final List<UserEntry> users = parser.parse(file);

		assertThat(users).containsExactly(
				new UserEntry("artem", HASH_1),
				new UserEntry("guest", HASH_2));
	}

	/** D38: the optional email, the author of the user's Git commits. */
	@Test
	void usersAreCountedFromOneInMessages() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "%s"
				  - password: "%s"
				""".formatted(HASH_1, HASH_2));

		assertThatThrownBy(() -> parser.parse(file)).hasMessageContaining("User #2 ");
	}

	@Test
	void anEmailIsOptional() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "%s"
				    email: artem@example.org
				  - username: guest
				    password: "%s"
				""".formatted(HASH_1, HASH_2));

		assertThat(parser.parse(file)).containsExactly(
				new UserEntry("artem", HASH_1, Optional.of("artem@example.org")),
				new UserEntry("guest", HASH_2, Optional.empty()));
	}

	@Test
	void anEmailMustBeText() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "%s"
				    email: [a, b]
				""".formatted(HASH_1));

		assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(UsersFileException.class).hasMessageContaining("email");
	}

	@Test
	void emptyUsersListGivesEmptyResult() throws IOException {
		assertThat(parser.parse(write("users: []\n"))).isEmpty();
	}

	@Test
	void usersKeyWithoutValueGivesEmptyResult() throws IOException {
		assertThat(parser.parse(write("users:\n"))).isEmpty();
	}

	@Test
	void missingFileIsRejectedWithPath() {
		final Path missing = dir.resolve("nope.yaml");

		assertThatThrownBy(() -> parser.parse(missing))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining(missing.toString());
	}

	@Test
	void malformedYamlIsRejected() throws IOException {
		final Path file = write("users:\n  - username: [unclosed\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining(file.toString());
	}

	@Test
	void malformedYamlErrorGivesLineButDoesNotQuoteContent() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: [s3cretPlain
				""");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("line 3")
				.hasMessageNotContaining("s3cretPlain");
	}

	@Test
	void duplicateKeysAreRejectedInsteadOfSilentlyUsingTheLastOne() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "%s"
				users:
				  - username: guest
				    password: "%s"
				""".formatted(HASH_1, HASH_2));

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining(file.toString());
	}

	@Test
	void emptyFileIsRejected() throws IOException {
		assertThatThrownBy(() -> parser.parse(write("")))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("users");
	}

	@Test
	void fileWithoutUsersKeyIsRejected() throws IOException {
		assertThatThrownBy(() -> parser.parse(write("people: []\n")))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("users");
	}

	@Test
	void duplicateUsernameIsRejectedWithName() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "%s"
				  - username: artem
				    password: "%s"
				""".formatted(HASH_1, HASH_2));

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("duplicate")
				.hasMessageContaining("artem");
	}

	@Test
	void usernamesDifferingOnlyInCaseAreDifferentUsers() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "%s"
				  - username: Artem
				    password: "%s"
				""".formatted(HASH_1, HASH_2));

		assertThat(parser.parse(file)).extracting(UserEntry::username).containsExactly("artem", "Artem");
	}

	@Test
	void blankUsernameIsRejected() throws IOException {
		final Path file = write("""
				users:
				  - username: "  "
				    password: "%s"
				""".formatted(HASH_1));

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("username");
	}

	@Test
	void missingUsernameIsRejected() throws IOException {
		final Path file = write("""
				users:
				  - password: "%s"
				""".formatted(HASH_1));

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("username");
	}

	@Test
	void missingPasswordIsRejectedWithName() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				""");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("password")
				.hasMessageContaining("artem");
	}

	@Test
	void plainTextPasswordIsRejectedWithoutRevealingIt() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: secret123
				""");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("artem")
				.hasMessageNotContaining("secret123");
	}

	@Test
	void noopPasswordIsRejectedBecauseItIsPlainText() throws IOException {
		final Path file = write("""
				users:
				  - username: artem
				    password: "{noop}secret123"
				""");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(UsersFileException.class)
				.hasMessageContaining("artem")
				.hasMessageNotContaining("secret123");
	}

	@Test
	void unknownFieldsAreIgnoredWithWarning(final CapturedOutput output) throws IOException {
		final Path file = write("""
				version: 2
				users:
				  - username: artem
				    password: "%s"
				    role: admin
				""".formatted(HASH_1));

		final List<UserEntry> users = parser.parse(file);

		assertThat(users).containsExactly(new UserEntry("artem", HASH_1));
		assertThat(output.getOut())
				.contains("WARN")
				.contains("'version'")
				.contains("'role'")
				.contains("artem");
	}

	@Test
	void validFileWithoutUnknownFieldsLogsNoWarning(final CapturedOutput output) throws IOException {
		parser.parse(write("""
				users:
				  - username: artem
				    password: "%s"
				""".formatted(HASH_1)));

		assertThat(output.getOut()).doesNotContain("WARN");
	}
}
