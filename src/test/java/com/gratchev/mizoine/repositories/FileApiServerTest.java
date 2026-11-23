package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gratchev.mizoine.TestHttp;

/**
 * Steps 28–31 on a real server, for what MockMvc cannot show: uploads without {@code Content-Length} (chunked,
 * as agents and browsers stream them) and the empty body of {@code HEAD}, which is Tomcat's part.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FileApiServerTest {

	private static final Path BASE = createTree();
	private static final Path TREE = BASE.resolve("tree");

	private static Path createTree() {
		try {
			final Path base = Files.createTempDirectory("mizoine-server");
			Files.createDirectories(base.resolve("tree/public"));
			Files.writeString(base.resolve("tree/public/note.md"), "# Note\n");
			Files.writeString(base.resolve("tree.yaml"), "path: tree\naccess:\n  artem: [\"/\"]\n");
			return base;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		registry.add("mizoine.repositories-dir", BASE::toString);
		registry.add("mizoine.max-file-size", () -> "1KB");
	}

	@AfterAll
	static void deleteTree() throws IOException {
		try (Stream<Path> paths = Files.walk(BASE)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
	}

	private final TestHttp http;

	FileApiServerTest(@LocalServerPort final int port) {
		this.http = new TestHttp(port);
	}

	/** {@code ofInputStream}: no length known in advance, so the body goes chunked. */
	private int putChunked(final String path, final String content) throws IOException, InterruptedException {
		final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
		final HttpRequest.Builder request = http.request(path, ARTEM)
				.PUT(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)));
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	@Test
	void chunkedUploadIsWritten() throws Exception {
		assertThat(putChunked("/tree/public/chunked.md", "streamed")).isEqualTo(201);

		assertThat(Files.readString(TREE.resolve("public/chunked.md"))).isEqualTo("streamed");
	}

	@Test
	void chunkedUploadOverTheLimitIsRejectedWhileStreaming() throws Exception {
		assertThat(putChunked("/tree/public/huge.md", "x".repeat(2048))).isEqualTo(413);

		assertThat(TREE.resolve("public/huge.md")).doesNotExist();
		try (Stream<Path> entries = Files.list(TREE.resolve("public"))) {
			assertThat(entries.map(entry -> entry.getFileName().toString())).noneMatch(name -> name.startsWith("."));
		}
	}

	@Test
	void chunkedBodyForAFolderIsRejected() throws Exception {
		assertThat(putChunked("/tree/public/with-body/", "x")).isEqualTo(415);

		assertThat(TREE.resolve("public/with-body")).doesNotExist();
	}

	@Test
	void headHasNoBody() throws Exception {
		final HttpResponse<String> response = http.send(
				http.request("/tree/public/note.md", ARTEM).method("HEAD", HttpRequest.BodyPublishers.noBody()),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Length")).hasValue("7");
		assertThat(response.body()).isEmpty();
	}
}
