package com.gratchev.mizoine.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.gratchev.mizoine.TestHttp;
import com.gratchev.mizoine.TestUsers;

/** Requests to the test server as a reverse proxy (nginx, behind it maybe Cloudflare) would send them. */
final class ProxyHttp {

	private final TestHttp http;

	ProxyHttp(final int port) {
		this.http = new TestHttp(port);
	}

	/** GET as a browser behind nginx serving {@code https://example.org}. */
	HttpResponse<String> getViaHttpsProxy(final String path) throws IOException, InterruptedException {
		return http.send(viaHttpsProxy(path, null).header("X-Forwarded-Host", "example.org"),
				HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * GET through a proxy that does not pass the public host name on (no {@code X-Forwarded-Host}, and
	 * {@code Host} is the application's own address), such as {@code proxy_pass http://localhost:12013}
	 * without {@code proxy_set_header Host}, or a tunnel to localhost.
	 *
	 * @param user a {@link TestUsers} name, or {@code null} for no login
	 */
	HttpResponse<String> getViaHttpsProxyWithoutHost(final String path, final String user)
			throws IOException, InterruptedException {
		return http.send(viaHttpsProxy(path, user), HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * GET as nginx sends it with a minimal config ({@code proxy_pass http://localhost:12013;}): HTTP/1.0,
	 * {@code Host} from {@code proxy_pass}, and the {@code X-Forwarded-Proto} that Cloudflare added passed through.
	 * {@link java.net.http.HttpClient} cannot speak HTTP/1.0, hence a plain socket.
	 *
	 * @param user a {@link TestUsers} name, or {@code null} for no login
	 * @return the {@code Location} header, if any
	 */
	Optional<String> locationViaHttp10Proxy(final String path, final String user) throws IOException {
		try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), http.port())) {
			final StringBuilder request = new StringBuilder()
					.append("GET ").append(path).append(" HTTP/1.0\r\n")
					.append("Host: localhost:12013\r\n")
					.append("Connection: close\r\n")
					.append("Accept: text/html\r\n")
					.append("X-Forwarded-Proto: https\r\n")
					.append("X-Forwarded-For: 203.0.113.7\r\n");
			if (user != null) {
				request.append("Authorization: ").append(TestUsers.basicAuthHeader(user)).append("\r\n");
			}
			request.append("\r\n");
			socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.US_ASCII));
			final String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
			final String headers = response.substring(0, response.indexOf("\r\n\r\n"));
			return headers.lines()
					.filter(line -> line.regionMatches(true, 0, "Location:", 0, "Location:".length()))
					.map(line -> line.substring("Location:".length()).strip())
					.findFirst();
		}
	}

	private HttpRequest.Builder viaHttpsProxy(final String path, final String user) {
		return http.request(path, user)
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
