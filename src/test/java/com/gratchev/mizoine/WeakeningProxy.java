package com.gratchev.mizoine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A reverse proxy for tests that does to responses what compressing proxies and CDNs (Cloudflare, nginx with gzip)
 * do: a strong {@code ETag} becomes weak ({@code "x"} → {@code W/"x"}), because the compressed bytes differ.
 * Everything else passes unchanged.
 */
public final class WeakeningProxy implements AutoCloseable {

	/** Set by the HTTP client or the server itself. */
	private static final Set<String> NOT_FORWARDED = Set.of("host", "connection", "content-length", "transfer-encoding",
			"expect", "upgrade", "keep-alive");

	private final HttpClient client = HttpClient.newHttpClient(); // follows no redirects
	private final HttpServer server;
	private final String target;

	public WeakeningProxy(final int targetPort) {
		this.target = "http://127.0.0.1:" + targetPort;
		try {
			this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		server.createContext("/", this::forward);
		server.start();
	}

	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private void forward(final HttpExchange exchange) throws IOException {
		try (exchange) {
			final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target + exchange.getRequestURI()))
					.method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(
							exchange.getRequestBody().readAllBytes()));
			exchange.getRequestHeaders().forEach((name, values) -> {
				if (!NOT_FORWARDED.contains(name.toLowerCase())) {
					values.forEach(value -> request.header(name, value));
				}
			});
			final HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
			for (final Map.Entry<String, List<String>> header : response.headers().map().entrySet()) {
				if (!NOT_FORWARDED.contains(header.getKey().toLowerCase()) && !header.getKey().startsWith(":")) {
					header.getValue().forEach(value -> exchange.getResponseHeaders().add(header.getKey(), weaken(header.getKey(), value)));
				}
			}
			final byte[] body = response.body().readAllBytes();
			final boolean noBody = body.length == 0 || "HEAD".equals(exchange.getRequestMethod());
			exchange.sendResponseHeaders(response.statusCode(), noBody ? -1 : body.length);
			if (!noBody) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
	}

	private static String weaken(final String header, final String value) {
		return "etag".equalsIgnoreCase(header) && value.startsWith("\"") ? "W/" + value : value;
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
