package com.gratchev.mizoine.repositories;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.util.UriComponentsBuilder;

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

	/** Fully encoded, so names with '{' cannot be taken for URI template variables. */
	static String url(final String repository, final List<String> segments, final boolean folder) {
		final UriComponentsBuilder url = UriComponentsBuilder.newInstance()
				.pathSegment(repository)
				.pathSegment(segments.toArray(String[]::new));
		return (folder ? url.path("/") : url).encode().build().toUriString();
	}
}
