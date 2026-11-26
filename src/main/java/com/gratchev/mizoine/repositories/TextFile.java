package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.gratchev.mizoine.files.TextFormats;

/**
 * A file read as text (D21): valid UTF-8, no NUL byte, and not larger than 2 MB. For the rendered Markdown page and for agents
 * reading files; anything else is given only as bytes (file download).
 */
public final class TextFile {

	/** 2 MB, from the list of text formats, which the editor reads too (step 69). */
	public static final long MAX_BYTES = TextFormats.STANDARD.maxBytes();

	private TextFile() {
	}

	/** @return the content, if it is valid UTF-8 without a NUL byte and not larger than {@link #MAX_BYTES} */
	public static Optional<String> read(final Path file) {
		try {
			if (Files.size(file) > MAX_BYTES) {
				return Optional.empty();
			}
			final String text = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(Files.readAllBytes(file)))
					.toString();
			// valid UTF-8 may still be binary: text has no NUL byte (D21)
			return text.indexOf('\0') < 0 ? Optional.of(text) : Optional.empty();
		} catch (final CharacterCodingException e) {
			return Optional.empty();
		} catch (final IOException e) {
			// removed since it was resolved
			return Optional.empty();
		}
	}
}
