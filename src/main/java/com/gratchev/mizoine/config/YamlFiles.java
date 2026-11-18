package com.gratchev.mizoine.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Function;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads hand-edited YAML configuration files. */
public final class YamlFiles {

	// a key twice in one mapping is ambiguous: reject instead of silently keeping the last one
	private static final YAMLMapper MAPPER = YAMLMapper.builder()
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.build();

	private YamlFiles() {
	}

	/**
	 * @param kind for messages, e.g. "Users file"
	 * @param error creates the exception to throw from a message
	 * @return the tree; {@code null} or a missing node for an empty file
	 */
	public static JsonNode readTree(final Path file, final String kind,
			final Function<String, ? extends RuntimeException> error) {
		try {
			return MAPPER.readTree(Files.readString(file));
		} catch (final NoSuchFileException e) {
			throw error.apply(kind + " not found: " + file);
		} catch (final IOException e) {
			throw error.apply("Cannot read " + kind.toLowerCase() + " " + file + ": " + e.getMessage());
		} catch (final JacksonException e) {
			// Jackson's message quotes the offending line, which may hold a password: report the position only.
			// The cause is not attached for the same reason.
			final var location = e.getLocation();
			final String where = location == null ? "" : " at line " + location.getLineNr() + ", column " + location.getColumnNr();
			throw error.apply(kind + " " + file + " is not valid YAML" + where);
		}
	}
}
