package com.gratchev.mizoine.security;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Redirects with path and query only, without scheme and host.
 * <p>
 * After login, Spring Security returns to the saved page with its full URL, built from the {@code Host}
 * header. Behind a proxy that does not pass the public host on, that is the wrong host
 * (bug: https://localhost/?continue). The saved page is always on this server, so dropping the host is
 * correct, and no redirect can lead to another host.
 */
final class RelativeRedirectStrategy implements RedirectStrategy {

	private final RedirectStrategy delegate = new DefaultRedirectStrategy();

	@Override
	public void sendRedirect(final HttpServletRequest request, final HttpServletResponse response, final String url)
			throws IOException {
		delegate.sendRedirect(request, response, relative(url));
	}

	/** @return path and query of {@code url} (still encoded), starting with exactly one {@code /} */
	static String relative(final String url) {
		final URI uri;
		try {
			uri = new URI(url);
		} catch (final URISyntaxException e) {
			return "/";
		}
		// one leading slash: "//host/x" would be protocol-relative, i.e. another host
		final String path = "/" + (uri.getRawPath() == null ? "" : uri.getRawPath().replaceFirst("^/+", ""));
		return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
	}
}
