package com.gratchev.mizoine.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Step 68, D73: the text formats that can be edited in the browser, by name only: a known extension (in any case)
 * or a known whole name. One list, {@code ui/editor/text-formats.json}, which the editor reads too.
 */
public final class TextFormats {

	/** The list the editor serves under {@code /ui/editor/text-formats.json}. */
	public static final TextFormats STANDARD = load("/ui/editor/text-formats.json");

	/**
	 * @param maxBytes the largest text file (D21)
	 * @param extensions lower-case extension → highlighting
	 * @param names whole file name → highlighting
	 */
	private record Formats(long maxBytes, Map<String, String> extensions, Map<String, String> names) {
	}

	private final Formats formats;

	private TextFormats(final Formats formats) {
		this.formats = formats;
	}

	private static TextFormats load(final String resource) {
		try (InputStream json = TextFormats.class.getResourceAsStream(resource)) {
			return new TextFormats(JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
					.readValue(json, Formats.class));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** @return the editor's highlighting for this file name ({@code text} for none); empty if it is no text format */
	public Optional<String> language(final String name) {
		final String whole = formats.names().get(name);
		if (whole != null) {
			return Optional.of(whole);
		}
		final int dot = name.lastIndexOf('.');
		return dot > 0 ? Optional.ofNullable(formats.extensions().get(name.substring(dot + 1).toLowerCase(Locale.ROOT)))
				: Optional.empty();
	}

	/** D21: the largest file that is read as text, by the editor and by the server. */
	public long maxBytes() {
		return formats.maxBytes();
	}

	public boolean isEditable(final String name) {
		return language(name).isPresent();
	}
}
