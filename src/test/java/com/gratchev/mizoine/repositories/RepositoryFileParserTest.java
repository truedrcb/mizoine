package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Step 14: one repository file, {@code <name>.yaml}. */
@ExtendWith(OutputCaptureExtension.class)
class RepositoryFileParserTest {

	private final Path dir;
	private final Path data;
	private final RepositoryFileParser parser = new RepositoryFileParser();

	RepositoryFileParserTest(@TempDir final Path dir) throws IOException {
		this.dir = dir;
		this.data = Files.createDirectory(dir.resolve("data"));
	}

	private Path write(final String fileName, final String yaml) throws IOException {
		return Files.writeString(dir.resolve(fileName), yaml);
	}

	private Path notes(final String yaml) throws IOException {
		return write("notes.yaml", yaml);
	}

	// --- valid files

	@Test
	void validFileGivesNameRootAndGrants() throws IOException {
		final RepositoryConfig config = parser.parse(notes("""
				path: %s
				access:
				  artem: ["/"]
				  guest: [public, projects/shared]
				""".formatted(data)));

		assertThat(config.name()).isEqualTo("notes");
		assertThat(config.root()).isEqualTo(data);
		assertThat(config.problems()).isEmpty();
		assertThat(config.grants()).containsOnlyKeys("artem", "guest");
		assertThat(config.grants().get("artem")).containsExactly(FolderGrant.WHOLE_REPOSITORY);
		assertThat(config.grants().get("guest")).containsExactly(
				FolderGrant.of("public"), FolderGrant.of("projects/shared"));
	}

	@Test
	void relativePathIsResolvedAgainstFolderOfTheFile() throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: data\naccess: {}\n"));

		assertThat(config.root()).isEqualTo(data);
		assertThat(config.problems()).isEmpty();
	}

	@Test
	void usernamesAreCaseSensitive() throws IOException {
		final RepositoryConfig config = parser.parse(notes("""
				path: data
				access:
				  artem: [a]
				  Artem: [b]
				"""));

		assertThat(config.grants()).containsOnlyKeys("artem", "Artem");
	}

	// --- folder grants

	@ParameterizedTest
	@ValueSource(strings = {"public", "public/", "/public", "//public//"})
	void folderSpellingsMeanTheSame(final String folder) throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: data\naccess:\n  artem: [\"" + folder + "\"]\n"));

		assertThat(config.grants().get("artem")).containsExactly(FolderGrant.of("public"));
	}

	@Test
	void slashIsTheWholeRepository() throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: data\naccess:\n  artem: [\"/\"]\n"));

		assertThat(config.grants().get("artem")).containsExactly(FolderGrant.WHOLE_REPOSITORY);
		assertThat(FolderGrant.WHOLE_REPOSITORY.isWholeRepository()).isTrue();
		assertThat(FolderGrant.of("public").isWholeRepository()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"..", "a/../../b", "a/..", "./a", ".git", "a/.hidden", "a\\\\b", ""})
	void unsafeOrHiddenFoldersMakeTheFileUnreadable(final String folder) throws IOException {
		final Path file = notes("path: data\naccess:\n  guest: [\"" + folder + "\"]\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class)
				.hasMessageContaining(file.toString())
				.hasMessageContaining("guest");
	}

	@Test
	void foldersMustBeAList() throws IOException {
		final Path file = notes("path: data\naccess:\n  guest: public\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class)
				.hasMessageContaining("guest");
	}

	@Test
	void accessMustBeAMapping() throws IOException {
		final Path file = notes("path: data\naccess: [artem]\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class)
				.hasMessageContaining("access");
	}

	@Test
	void duplicateUsernameMakesTheFileUnreadable() throws IOException {
		final Path file = notes("path: data\naccess:\n  guest: [a]\n  guest: [\"/\"]\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class)
				.hasMessageContaining(file.toString());
	}

	@Test
	void blankUsernameMakesTheFileUnreadable() throws IOException {
		final Path file = notes("path: data\naccess:\n  \" \": [a]\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class);
	}

	@Test
	void emptyAccessIsValidButWarned(final CapturedOutput output) throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: data\naccess: {}\n"));

		assertThat(config.grants()).isEmpty();
		assertThat(output.getOut()).contains("WARN").contains("nobody");
	}

	@Test
	void missingAccessIsLikeEmptyAccess(final CapturedOutput output) throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: data\n"));

		assertThat(config.grants()).isEmpty();
		assertThat(output.getOut()).contains("WARN").contains("nobody");
	}

	// --- root folder problems: readable, permissions known (D8)

	@Test
	void missingPathIsAProblemNotAnError() throws IOException {
		final RepositoryConfig config = parser.parse(notes("access:\n  artem: [\"/\"]\n"));

		assertThat(config.grants()).containsOnlyKeys("artem");
		assertThat(config.root()).isNull();
		assertThat(config.problems()).singleElement().asString().contains("path");
	}

	@Test
	void notExistingRootFolderIsAProblem() throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: missing\naccess:\n  artem: [\"/\"]\n"));

		assertThat(config.grants()).containsOnlyKeys("artem");
		assertThat(config.problems()).singleElement().asString()
				.contains(dir.resolve("missing").toString())
				.contains("does not exist");
	}

	@Test
	void rootThatIsAFileIsAProblem() throws IOException {
		Files.writeString(dir.resolve("plain.txt"), "x");

		final RepositoryConfig config = parser.parse(notes("path: plain.txt\naccess:\n  artem: [\"/\"]\n"));

		assertThat(config.problems()).singleElement().asString().contains("not a folder");
	}

	@Test
	void pathThatIsNotTextIsAProblem() throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: [a, b]\naccess:\n  artem: [\"/\"]\n"));

		assertThat(config.grants()).containsOnlyKeys("artem");
		assertThat(config.problems()).singleElement().asString().contains("path");
	}

	// --- unreadable files

	@ParameterizedTest
	@ValueSource(strings = {".hidden.yaml", "my repo.yaml", "login.yaml", "logout.yaml", "error.yaml", "notes.yml"})
	void invalidRepositoryNamesAreRejected(final String fileName) throws IOException {
		final Path file = write(fileName, "path: data\naccess: {}\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class)
				.hasMessageContaining(fileName);
	}

	@Test
	void malformedYamlGivesPositionButNotContent() throws IOException {
		final Path file = notes("path: data\naccess:\n  artem: [s3cret-folder\n");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class)
				.hasMessageContaining(file.toString())
				.hasMessageContaining("line")
				.hasMessageNotContaining("s3cret-folder");
	}

	@Test
	void emptyFileIsUnreadable() throws IOException {
		final Path file = notes("");

		assertThatThrownBy(() -> parser.parse(file)).isInstanceOf(RepositoryFileException.class);
	}

	@Test
	void missingFileIsUnreadable() {
		final Path file = dir.resolve("gone.yaml");

		assertThatThrownBy(() -> parser.parse(file))
				.isInstanceOf(RepositoryFileException.class)
				.hasMessageContaining(file.toString());
	}

	// --- unknown fields

	@Test
	void unknownFieldsAreIgnoredWithWarning(final CapturedOutput output) throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: data\ndescription: My notes\naccess:\n  artem: [\"/\"]\n"));

		assertThat(config.problems()).isEmpty();
		assertThat(output.getOut()).contains("WARN").contains("'description'");
	}

	@Test
	void validFileLogsNoWarning(final CapturedOutput output) throws IOException {
		parser.parse(notes("path: data\naccess:\n  artem: [\"/\"]\n"));

		assertThat(output.getOut()).doesNotContain("WARN");
	}

	// --- names (also used for lookups in step 16)

	@ParameterizedTest
	@ValueSource(strings = {"notes", "Notes", "my-notes", "my_notes", "notes.v2", "2026"})
	void validNames(final String name) {
		assertThat(RepositoryNames.isValid(name)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", ".", "..", ".git", "a/b", "a\\\\b", "a b", "a%2fb", "login", "logout", "error",
			"default-ui.css", "favicon.ico"})
	void invalidNames(final String name) {
		assertThat(RepositoryNames.isValid(name)).isFalse();
	}

	@Test
	void problemsAreNotModifiable() throws IOException {
		final RepositoryConfig config = parser.parse(notes("path: missing\naccess: {}\n"));

		final List<String> problems = config.problems();
		assertThatThrownBy(() -> problems.add("x")).isInstanceOf(UnsupportedOperationException.class);
	}
}
