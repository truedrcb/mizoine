package com.gratchev.mizoine.repositories;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/** URL paths below a repository ↔ path segments. Folder URLs end with '/', file URLs do not (D5). */
final class RepositoryUrls {

	private RepositoryUrls() {
	}

	/**
	 * The {@code {*path}} part of a URL, already decoded: "/" → [], "/a/b/" and "/a/b" → [a, b]. Empty segments
	 * ("a//b") stay and are rejected later by {@link FolderNames#isPlain}.
	 */
	static List<String> segments(final String path) {
		final String inner = path.replaceFirst("^/", "").replaceFirst("/$", "");
		return inner.isEmpty() ? List.of() : Arrays.asList(inner.split("/", -1));
	}

	static boolean isFolder(final String path) {
		return path.endsWith("/");
	}

	/**
	 * The {@code Destination} header of a move (step 41, D29, D31): an encoded path on this server, in the same
	 * repository, naming the same kind as the source (folder URLs end with '/'). No host: Mizoine cannot know its
	 * own public name behind a proxy, and a move to another server is not possible anyway.
	 *
	 * @return the destination's segments below the repository root, or empty if the header is not such a path
	 */
	static Optional<List<String>> destination(final String header, final String repository, final boolean folder) {
		if (header == null || !header.startsWith("/") || header.startsWith("//") || isFolder(header) != folder) {
			return Optional.empty();
		}
		final String path = UriUtils.decode(header, StandardCharsets.UTF_8);
		final String prefix = "/" + repository + "/";
		return path.startsWith(prefix) ? Optional.of(segments(path.substring(prefix.length() - 1))) : Optional.empty();
	}

	/** Fully encoded, so names with '{' cannot be taken for URI template variables. */
	static String url(final String repository, final List<String> segments, final boolean folder) {
		final UriComponentsBuilder url = UriComponentsBuilder.newInstance()
				.pathSegment(repository)
				.pathSegment(segments.toArray(String[]::new));
		return (folder ? url.path("/") : url).encode().build().toUriString();
	}
}
