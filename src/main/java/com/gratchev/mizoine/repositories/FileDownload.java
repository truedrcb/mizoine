package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import com.gratchev.mizoine.files.FileVersion;
import com.gratchev.mizoine.security.SecurityConfig;

/**
 * The answer to {@code GET} of a repository file (step 28): its bytes, safe in the browser (D10).
 * <ul>
 * <li>Type from the file name; unknown → {@code application/octet-stream}. Text types with
 * {@code charset=UTF-8}.</li>
 * <li>{@code Content-Security-Policy} with {@code sandbox}: a file never runs scripts in Mizoine's origin.</li>
 * <li>Shown inline only for types a browser does not execute (images except SVG, audio, video, PDF, plain text,
 * Markdown, JSON, …); everything else is downloaded ({@code attachment}).</li>
 * <li>{@code ETag} from size and modification time, {@code Last-Modified}: Spring answers {@code If-None-Match}
 * with 304, {@code Range} with 206 and {@code HEAD} without body.</li>
 * </ul>
 */
final class FileDownload {

	private static final Set<String> INLINE_MEDIA = Set.of("image", "audio", "video");
	private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");
	private static final MediaType YAML = MediaType.parseMediaType("application/yaml");
	private static final Set<MediaType> INLINE = Set.of(MediaType.APPLICATION_PDF, MediaType.TEXT_PLAIN,
			MediaType.TEXT_MARKDOWN, MediaType.APPLICATION_JSON, MediaType.parseMediaType("text/csv"),
			YAML);
	/** Text formats of a knowledge base that Spring's type table does not know. */
	private static final Map<String, MediaType> OWN_TYPES = Map.of(
			"md", MediaType.TEXT_MARKDOWN, "markdown", MediaType.TEXT_MARKDOWN, "yaml", YAML, "yml", YAML,
			// a folder's tags (step 70), later also the sidecar files of files (D72)
			"tags", MediaType.TEXT_PLAIN);

	private FileDownload() {
	}

	static ResponseEntity<Resource> of(final Path file) throws IOException {
		final BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
		final String name = file.getFileName().toString();
		final MediaType type = type(name);
		final ContentDisposition disposition = (isInline(type) ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(name, StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.contentType(withCharset(type))
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("Content-Security-Policy", SecurityConfig.FILE_CONTENT_SECURITY_POLICY)
				.eTag(new FileVersion(attributes.size(), attributes.lastModifiedTime()).etag())
				// the same URL is a rendered page for browsers (step 38): caches keep both apart
				.varyBy(HttpHeaders.ACCEPT)
				.lastModified(attributes.lastModifiedTime().toInstant())
				.body(new FileSystemResource(file));
	}

	static MediaType type(final String name) {
		final String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		return Optional.ofNullable(OWN_TYPES.get(extension))
				.or(() -> MediaTypeFactory.getMediaType(name))
				.orElse(MediaType.APPLICATION_OCTET_STREAM);
	}

	/**
	 * Text is declared as UTF-8, the encoding of Markdown and of the files Git tools write. Without it browsers read
	 * text as Windows-1252 ("мой дом" became "Ð¼Ð¾Ð¹ Ð´Ð¾Ð¼").
	 */
	static MediaType withCharset(final MediaType type) {
		return "text".equals(type.getType()) ? new MediaType(type, StandardCharsets.UTF_8) : type;
	}

	static boolean isInline(final MediaType type) {
		return INLINE.stream().anyMatch(inline -> inline.equalsTypeAndSubtype(type))
				|| INLINE_MEDIA.contains(type.getType()) && !SVG.equalsTypeAndSubtype(type);
	}

}
