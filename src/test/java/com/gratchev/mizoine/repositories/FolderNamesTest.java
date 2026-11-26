package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FolderNamesTest {

	@ParameterizedTest
	@ValueSource(strings = {"notes", "Projekte alt", "ä", "a.b", "a-b_c", "{x}"})
	void plainNames(final String name) {
		assertThat(FolderNames.isPlain(name)).isTrue();
		assertThat(FolderNames.isHidden(name)).isFalse();
	}

	/** Step 70, D81: the one exception: a folder's tags file is a text file like any other. */
	@Test
	void theTagsFileIsNotHidden() {
		assertThat(FolderNames.isHidden(".tags")).isFalse();
		assertThat(FolderNames.isPlain(".tags")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {".git", ".", "..", ".mizoine", "100%", "a%20b", ".Tags", ".TAGS", ".tags2", ".tagsx", ".env",
			"..tags"})
	void hiddenNames(final String name) {
		assertThat(FolderNames.isHidden(name)).isTrue();
		assertThat(FolderNames.isPlain(name)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "a/b", "a\\b", "a\0b"})
	void notANameAtAll(final String name) {
		assertThat(FolderNames.isPlain(name)).isFalse();
	}
}
