package com.gratchev.mizoine.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Plain HTTP requests to the test server, as nginx would send them. */
final class ProxyHttp {

	private final HttpClient client = HttpClient.newHttpClient(); // follows no redirects, keeps no cookies
	private final int port;

	ProxyHttp(final int port) {
		this.port = port;
	}

	/** GET as a browser behind nginx serving {@code https://example.org}. */
	HttpResponse<String> getViaHttpsProxy(final String path) throws IOException, InterruptedException {
		return client.send(viaHttpsProxy(path).header("X-Forwarded-Host", "example.org").build(),
				HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * GET through a proxy that does not pass the public host name on (no {@code X-Forwarded-Host}, and
	 * {@code Host} is the application's own address), such as {@code proxy_pass http://localhost:12013}
	 * without {@code proxy_set_header Host}, or a tunnel to localhost.
	 *
	 * @param basicAuth {@code user:password}, or {@code null} for no login
	 */
	HttpResponse<String> getViaHttpsProxyWithoutHost(final String path, final String basicAuth)
			throws IOException, InterruptedException {
		final HttpRequest.Builder request = viaHttpsProxy(path);
		if (basicAuth != null) {
			request.header("Authorization",
					"Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8)));
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder viaHttpsProxy(final String path) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Accept", "text/html")
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-For", "203.0.113.7");
	}

	static String sessionCookie(final HttpResponse<String> response) {
		return response.headers().allValues("Set-Cookie").stream()
				.filter(cookie -> cookie.startsWith("JSESSIONID="))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no session cookie in " + response.headers().map()));
	}
}
