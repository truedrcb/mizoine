package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A file read as text (D21): valid UTF-8 and not larger than 2 MB. For the rendered Markdown page and for agents
 * reading files; anything else is given only as bytes (file download).
 */
final class TextFile {

	static final long MAX_BYTES = 2L * 1024 * 1024;

	private TextFile() {
	}

	/** @return the content, if it is valid UTF-8 and not larger than {@link #MAX_BYTES} */
	static Optional<String> read(final Path file) {
		try {
			if (Files.size(file) > MAX_BYTES) {
				return Optional.empty();
			}
			return Optional.of(StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(Files.readAllBytes(file)))
					.toString());
		} catch (final CharacterCodingException e) {
			return Optional.empty();
		} catch (final IOException e) {
			// removed since it was resolved
			return Optional.empty();
		}
	}
}
