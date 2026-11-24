package com.gratchev.mizoine.repositories;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

/**
 * When a Markdown file is shown as a rendered page (step 38) instead of its raw bytes.
 * <ul>
 * <li>D22: only for a person's browser, which asks explicitly for {@code text/html}. {@code *}{@code /*} or no
 * {@code Accept} (curl, downloads), {@code ?raw}, and agents with an OAuth token ({@code Bearer}, also through the
 * MCP API) always get the raw file.</li>
 * <li>D21: only files {@link TextFile} can read (valid UTF-8 up to 2 MB); anything else is returned raw.</li>
 * </ul>
 */
final class MarkdownPage {

	private static final String BEARER = "bearer ";

	private MarkdownPage() {
	}

	static boolean isWanted(final String accept, final String authorization, final boolean raw) {
		return !raw && !isBearer(authorization) && asksForHtml(accept);
	}

	static boolean isMarkdown(final Path file) {
		return MediaType.TEXT_MARKDOWN.equalsTypeAndSubtype(FileDownload.type(file.getFileName().toString()));
	}

	private static boolean isBearer(final String authorization) {
		return authorization != null && authorization.toLowerCase(Locale.ROOT).startsWith(BEARER);
	}

	/** Named explicitly, not only through a wildcard, and not refused with {@code q=0}. */
	private static boolean asksForHtml(final String accept) {
		if (accept == null) {
			return false;
		}
		final List<MediaType> types;
		try {
			types = MediaType.parseMediaTypes(accept);
		} catch (final InvalidMediaTypeException e) {
			return false;
		}
		return types.stream().anyMatch(type -> MediaType.TEXT_HTML.equalsTypeAndSubtype(type) && type.getQualityValue() > 0);
	}
}
