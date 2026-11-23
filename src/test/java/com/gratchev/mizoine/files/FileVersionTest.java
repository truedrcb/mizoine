package com.gratchev.mizoine.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The version of a file on disk: one rule for the users file, repository files and the file API (ETag). */
class FileVersionTest {

	private final Path dir;

	FileVersionTest(@TempDir final Path dir) {
		this.dir = dir;
	}

	@Test
	void sameFileSameVersion() throws IOException {
		final Path file = Files.writeString(dir.resolve("a.md"), "x");

		assertThat(FileVersion.of(file)).isEqualTo(FileVersion.of(file)).isPresent();
	}

	@Test
	void everyWriteMakesANewVersion() throws IOException {
		final Path file = Files.writeString(dir.resolve("a.md"), "x");
		final FileVersion before = FileVersion.of(file).orElseThrow();

		Files.writeString(file, "y");
		Files.setLastModifiedTime(file, FileTime.fromMillis(before.modified().toMillis() + 1));

		assertThat(FileVersion.of(file)).hasValueSatisfying(after -> assertThat(after).isNotEqualTo(before));
		Files.writeString(file, "longer");
		assertThat(FileVersion.of(file).orElseThrow().size()).isEqualTo(6);
	}

	@Test
	void missingFileOrFolderHasNoVersion() throws IOException {
		assertThat(FileVersion.of(dir.resolve("missing.md"))).isEmpty();
		assertThat(FileVersion.of(Files.createDirectories(dir.resolve("folder")))).isEmpty();
	}

	// ETag (HTTP): strong, quoted, from size and modification time

	@Test
	void etagIsAQuotedStrongTag() throws IOException {
		final String etag = FileVersion.of(Files.writeString(dir.resolve("a.md"), "x")).orElseThrow().etag();

		assertThat(etag).startsWith("\"").endsWith("\"").doesNotStartWith("W/");
	}

	@Test
	void ifMatchNamesTheVersionAlsoWhenAProxyMadeItWeak() throws IOException {
		final FileVersion version = FileVersion.of(Files.writeString(dir.resolve("a.md"), "x")).orElseThrow();

		assertThat(version.isNamedBy(version.etag())).isTrue();
		assertThat(version.isNamedBy("W/" + version.etag())).isTrue();
		assertThat(version.isNamedBy("\"other\"")).isFalse();
		assertThat(version.isNamedBy("W/\"other\"")).isFalse();
	}
}
