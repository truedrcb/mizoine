package com.gratchev.mizoine.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Plain HTTP requests to the test server, as nginx would send them. */
final class ProxyHttp {

	private final HttpClient client = HttpClient.newHttpClient(); // follows no redirects, keeps no cookies
	private final int port;

	ProxyHttp(final int port) {
		this.port = port;
	}

	/** GET as a browser behind nginx serving {@code https://example.org}. */
	HttpResponse<String> getViaHttpsProxy(final String path) throws IOException, InterruptedException {
		final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Accept", "text/html")
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-Host", "example.org")
				.header("X-Forwarded-For", "203.0.113.7")
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	static String sessionCookie(final HttpResponse<String> response) {
		return response.headers().allValues("Set-Cookie").stream()
				.filter(cookie -> cookie.startsWith("JSESSIONID="))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no session cookie in " + response.headers().map()));
	}
}
