package com.gratchev.mizoine.tags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A folder's own tags in the folder itself (D64): the hidden file {@code .tags}, so the tags move with the folder,
 * also when it is moved by hand. Missing, unreadable or not text: no tags, logged, never an error page.
 */
public final class TagsFile {

	public static final String NAME = ".tags";
	/** Tags are for folders for now (D72). */
	public static final String FOLDERS_ONLY = "Tags are for folders: a folder's path ends with '/'.";

	private static final Logger log = LoggerFactory.getLogger(TagsFile.class);

	private TagsFile() {
	}

	/** @param folder the real path of the folder */
	public static Tags read(final Path folder) {
		final Path file = folder.resolve(NAME);
		try {
			final Tags tags = Tags.read(StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString());
			tags.problems().forEach(problem -> log.warn("Tags file {}: {}", file, problem));
			return tags;
		} catch (final NoSuchFileException e) {
			return Tags.NONE;
		} catch (final CharacterCodingException e) {
			log.warn("Tags file {} cannot be read: not UTF-8 text", file);
			return Tags.NONE;
		} catch (final IOException e) {
			log.warn("Tags file {} cannot be read: {}", file, e.toString());
			return Tags.NONE;
		}
	}
}
