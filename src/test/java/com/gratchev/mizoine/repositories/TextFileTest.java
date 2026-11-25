package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D21: a file as text only if it is valid UTF-8, has no NUL byte and is not larger than 2 MB (Markdown page, agents' read_file, search). */
class TextFileTest {

	private final Path dir;

	TextFileTest(@TempDir final Path dir) {
		this.dir = dir;
	}

	@Test
	void utf8TextIsRead() throws IOException {
		final Path file = Files.writeString(dir.resolve("ru.md"), "мой дом");

		assertThat(TextFile.read(file)).hasValue("мой дом");
	}

	@Test
	void notUtf8IsNotText() throws IOException {
		final Path file = Files.write(dir.resolve("latin1.md"), "Gr\u00fc\u00dfe".getBytes(StandardCharsets.ISO_8859_1));

		assertThat(TextFile.read(file)).isEmpty();
	}

	@Test
	void aNulByteIsNotText() throws IOException {
		// valid UTF-8, but binary: e.g. a file format with text inside
		final Path file = Files.writeString(dir.resolve("data.bin"), "header\u0000body");
		final Path first = Files.writeString(dir.resolve("first.bin"), "\u0000body");

		assertThat(TextFile.read(file)).isEmpty();
		assertThat(TextFile.read(first)).isEmpty();
	}

	@Test
	void upToTheLimitIsTextLargerIsNot() throws IOException {
		final Path limit = Files.writeString(dir.resolve("limit.md"), "x".repeat((int) TextFile.MAX_BYTES));
		final Path larger = Files.writeString(dir.resolve("larger.md"), "x".repeat((int) TextFile.MAX_BYTES + 1));

		assertThat(TextFile.read(limit)).isPresent();
		assertThat(TextFile.read(larger)).isEmpty();
		assertThat(TextFile.MAX_BYTES).isEqualTo(2 * 1024 * 1024);
	}
}
