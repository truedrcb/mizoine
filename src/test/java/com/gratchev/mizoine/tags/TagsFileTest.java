package com.gratchev.mizoine.tags;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Step 61, D64: a folder's own tags in its {@code .tags} file; a file that cannot be read counts as no tags. */
@ExtendWith(OutputCaptureExtension.class)
class TagsFileTest {

	private final Path folder;

	TagsFileTest(@TempDir final Path folder) {
		this.folder = folder;
	}

	@Test
	void aFolderWithoutTheFileHasNoTags() {
		assertThat(TagsFile.read(folder).list()).isEmpty();
	}

	@Test
	void theFolderOwnTags() throws IOException {
		Files.writeString(folder.resolve(TagsFile.NAME), "project status:active\n");

		assertThat(TagsFile.read(folder)).isEqualTo(Tags.of(List.of("project", "status:active")));
	}

	@Test
	void anUnreadableFileCountsAsNoTagsAndIsLogged(final CapturedOutput output) throws IOException {
		final Path file = Files.writeString(folder.resolve(TagsFile.NAME), "project");
		Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
		try {
			assertThat(TagsFile.read(folder).list()).isEmpty();
			assertThat(output.getOut()).contains("cannot be read");
		} finally {
			Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
		}
	}

	@Test
	void notTextCountsAsNoTags(final CapturedOutput output) throws IOException {
		Files.write(folder.resolve(TagsFile.NAME), new byte[] {(byte) 0xff, 'x'});

		assertThat(TagsFile.read(folder).list()).isEmpty();
		assertThat(output.getOut()).contains("cannot be read");
	}

	@Test
	void badTagsInTheFileAreLoggedTheRestCounts(final CapturedOutput output) throws IOException {
		Files.writeString(folder.resolve(TagsFile.NAME), "project a:b:c");

		assertThat(TagsFile.read(folder)).isEqualTo(Tags.of(List.of("project")));
		assertThat(output.getOut()).contains("a:b:c");
	}
}
