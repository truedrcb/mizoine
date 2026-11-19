package com.gratchev.mizoine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Plain HTTP requests to the test server ({@code webEnvironment = RANDOM_PORT}), for what MockMvc does not
 * cover: Tomcat's URL handling, forwarded headers, error pages. Follows no redirects, keeps no cookies.
 */
public final class TestHttp {

	private final HttpClient client = HttpClient.newHttpClient();
	private final int port;

	public TestHttp(final int port) {
		this.port = port;
	}

	public int port() {
		return port;
	}

	/** @param user a {@link TestUsers} name, logged in with HTTP Basic; {@code null} = anonymous */
	public HttpRequest.Builder request(final String path, final String user) {
		final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
		if (user != null) {
			request.header("Authorization", TestUsers.basicAuthHeader(user));
		}
		return request;
	}

	public <T> HttpResponse<T> send(final HttpRequest.Builder request, final HttpResponse.BodyHandler<T> body)
			throws IOException, InterruptedException {
		return client.send(request.build(), body);
	}

	/** GET as a browser would send it ({@code Accept: text/html}, anything else accepted too). */
	public HttpResponse<String> get(final String path, final String user) throws IOException, InterruptedException {
		return send(request(path, user).header("Accept", "text/html,*/*;q=0.8"), HttpResponse.BodyHandlers.ofString());
	}
}
