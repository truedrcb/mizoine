package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Step 16: repository files are looked up and read on request, not at startup. */
@ExtendWith(OutputCaptureExtension.class)
class RepositoryRegistryTest {

	private static final Set<String> KNOWN_USERS = Set.of("artem", "guest");

	/** Records which files were parsed. */
	static class CountingParser extends RepositoryFileParser {
		final List<String> parsed = new CopyOnWriteArrayList<>();

		@Override
		public RepositoryConfig parse(final Path file) {
			parsed.add(file.getFileName().toString());
			return super.parse(file);
		}
	}

	private final Path repositories;
	private final CountingParser parser = new CountingParser();
	private final RepositoryRegistry registry;
	private long fakeTime = 1_700_000_000L;

	RepositoryRegistryTest(@TempDir final Path dir) throws IOException {
		this.repositories = Files.createDirectory(dir.resolve("repositories"));
		Files.createDirectory(repositories.resolve("data"));
		this.registry = new RepositoryRegistry(repositories, parser, KNOWN_USERS::contains, null);
	}

	/** Writes a file and moves its modification time forward (independent of file system time resolution). */
	private Path write(final String fileName, final String yaml) throws IOException {
		final Path file = Files.writeString(repositories.resolve(fileName), yaml);
		fakeTime += 10;
		Files.setLastModifiedTime(file, FileTime.from(Instant.ofEpochSecond(fakeTime)));
		return file;
	}

	private Path repository(final String name, final String grants) throws IOException {
		return write(name + ".yaml", "path: data\naccess:\n" + grants);
	}

	private static long errorLines(final CapturedOutput output) {
		return output.getOut().lines().filter(line -> line.contains("ERROR")).count();
	}

	// --- find

	@Test
	void firstRequestReadsOnlyTheMatchingFile() throws IOException {
		repository("notes", "  artem: [\"/\"]\n");
		repository("photos", "  artem: [\"/\"]\n");

		final RepositoryConfig notes = registry.find("notes").orElseThrow();

		assertThat(notes.name()).isEqualTo("notes");
		assertThat(notes.grants()).containsOnlyKeys("artem");
		assertThat(parser.parsed).containsExactly("notes.yaml");
	}

	@Test
	void unchangedFileIsNotReadAgain() throws IOException {
		repository("notes", "  artem: [\"/\"]\n");

		registry.find("notes");
		registry.find("notes");
		registry.find("notes");

		assertThat(parser.parsed).containsExactly("notes.yaml");
	}

	@Test
	void changedFileIsReadAgain() throws IOException {
		repository("notes", "  artem: [\"/\"]\n");
		registry.find("notes");

		repository("notes", "  artem: [\"/\"]\n  guest: [public]\n");

		assertThat(registry.find("notes").orElseThrow().grants()).containsOnlyKeys("artem", "guest");
	}

	@Test
	void removedFileMakesTheRepositoryUnknown() throws IOException {
		final Path file = repository("notes", "  artem: [\"/\"]\n");
		registry.find("notes");

		Files.delete(file);

		assertThat(registry.find("notes")).isEmpty();
	}

	@Test
	void fileAddedWhileRunningIsFound() throws IOException {
		assertThat(registry.find("notes")).isEmpty();

		repository("notes", "  artem: [\"/\"]\n");

		assertThat(registry.find("notes")).isPresent();
	}

	@Test
	void unreadableFileIsUnknownAndLoggedOncePerVersion(final CapturedOutput output) throws IOException {
		write("notes.yaml", "access: [broken\n");

		assertThat(registry.find("notes")).isEmpty();
		assertThat(registry.find("notes")).isEmpty();

		assertThat(errorLines(output)).isEqualTo(1);
		assertThat(output.getOut()).contains("notes.yaml");
		assertThat(parser.parsed).hasSize(1);
	}

	@Test
	void unreadableChangeDoesNotKeepTheOldVersion() throws IOException {
		repository("notes", "  guest: [\"/\"]\n");
		assertThat(registry.find("notes")).isPresent();

		write("notes.yaml", "access: [broken\n");

		// keeping the old version could keep access the editor wanted to remove (D7)
		assertThat(registry.find("notes")).isEmpty();
	}

	@Test
	void readableFileWithProblemsIsReturnedAndLoggedOnce(final CapturedOutput output) throws IOException {
		write("notes.yaml", "path: missing\naccess:\n  artem: [\"/\"]\n");

		final RepositoryConfig notes = registry.find("notes").orElseThrow();
		registry.find("notes");

		assertThat(notes.problems()).isNotEmpty();
		assertThat(errorLines(output)).isEqualTo(1);
		assertThat(output.getOut()).contains("notes.yaml").contains("does not exist");
	}

	@ParameterizedTest
	@ValueSource(strings = {"..", ".git", "a/b", "a%2fb", "../repositories/notes", "login", ""})
	void invalidNamesAreUnknownWithoutReadingAnything(final String name) throws IOException {
		repository("notes", "  artem: [\"/\"]\n");

		assertThat(registry.find(name)).isEmpty();
		assertThat(parser.parsed).isEmpty();
	}

	@Test
	void lookupsWithoutFileDoNotGrowTheCache() {
		for (int i = 0; i < 100; i++) {
			registry.find("random" + i);
		}

		assertThat(registry.cachedRepositories()).isZero();
	}

	@Test
	void unknownUserInAccessGivesWarning(final CapturedOutput output) throws IOException {
		repository("notes", "  artem: [\"/\"]\n  nobody: [public]\n");

		assertThat(registry.find("notes")).isPresent();
		assertThat(output.getOut()).contains("WARN").contains("'nobody'").contains("notes");
	}

	@Test
	void withoutRepositoriesFolderNothingIsFound() {
		final RepositoryRegistry none = new RepositoryRegistry(null, parser, KNOWN_USERS::contains, null);

		assertThat(none.find("notes")).isEmpty();
		assertThat(none.all()).isEmpty();
	}

	@Test
	void missingFolderFindsNothingUntilItExists() throws IOException {
		final Path later = repositories.resolveSibling("later");
		final RepositoryRegistry registryForLater = new RepositoryRegistry(later, parser, KNOWN_USERS::contains, null);
		assertThat(registryForLater.find("notes")).isEmpty();
		assertThat(registryForLater.all()).isEmpty();

		Files.createDirectories(later.resolve("data"));
		Files.writeString(later.resolve("notes.yaml"), "path: data\naccess:\n  artem: [\"/\"]\n");

		assertThat(registryForLater.find("notes")).isPresent();
	}

	@Test
	void usersFileIsNeverARepository() throws IOException {
		final Path usersFile = write("users.yaml", "users: []\n");
		final RepositoryRegistry withUsersFile = new RepositoryRegistry(repositories, parser, KNOWN_USERS::contains,
				// spelled differently on purpose: the same file must be recognised
				repositories.resolve("data/../users.yaml"));
		repository("notes", "  artem: [\"/\"]\n");

		assertThat(withUsersFile.find("users")).isEmpty();
		assertThat(withUsersFile.all()).extracting(RepositoryConfig::name).containsExactly("notes");
		assertThat(parser.parsed).doesNotContain(usersFile.getFileName().toString());
	}

	// --- all

	@Test
	void allListsValidRepositoriesSortedByName() throws IOException {
		repository("photos", "  artem: [\"/\"]\n");
		repository("notes", "  artem: [\"/\"]\n");
		write("README.md", "not a repository");
		write("notes.yaml~", "editor backup");
		write(".hidden.yaml", "path: data\n");
		write("login.yaml", "path: data\n");

		assertThat(registry.all()).extracting(RepositoryConfig::name).containsExactly("notes", "photos");
	}

	@Test
	void allLeavesOutUnreadableFilesButKeepsFilesWithProblems() throws IOException {
		repository("notes", "  artem: [\"/\"]\n");
		write("broken.yaml", "access: [broken\n");
		write("moved.yaml", "path: missing\naccess:\n  artem: [\"/\"]\n");

		assertThat(registry.all()).extracting(RepositoryConfig::name).containsExactly("moved", "notes");
	}

	@Test
	void allReadsOnlyNewOrChangedFiles() throws IOException {
		repository("notes", "  artem: [\"/\"]\n");
		repository("photos", "  artem: [\"/\"]\n");
		registry.all();
		parser.parsed.clear();

		repository("photos", "  guest: [\"/\"]\n");
		registry.all();

		assertThat(parser.parsed).containsExactly("photos.yaml");
	}
}
