package com.gratchev.mizoine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * {@code /ui/**}: public files for the user interface (logos, later scripts and styles), from
 * {@code src/main/resources/ui/}, without login. A real server, so that Tomcat's URL handling is part of it.
 * {@code ui/test/hello.txt} exists only in the test resources.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"mizoine.users-file=src/test/resources/users-test.yaml",
		"mizoine.repositories-dir=src/test/resources/repositories"})
class UiResourcesTest {

	private final HttpClient client = HttpClient.newHttpClient(); // follows no redirects
	private final int port;

	UiResourcesTest(@LocalServerPort final int port) {
		this.port = port;
	}

	@Test
	void fileIsServedWithoutLogin() throws Exception {
		final HttpResponse<byte[]> response = get("/ui/favicon.ico", null);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(Files.readAllBytes(Path.of("src/main/resources/ui/favicon.ico")));
	}

	@Test
	void fileInSubfolderIsServedWithItsContentType() throws Exception {
		final HttpResponse<byte[]> response = get("/ui/test/hello.txt", null);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type)
				.startsWith("text/plain"));
		assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hello from /ui/\n");
	}

	@Test
	void fileIsServedWhenLoggedIn() throws Exception {
		assertThat(get("/ui/test/hello.txt", "artem:artem-pass").statusCode()).isEqualTo(200);
	}

	@Test
	void securityHeadersStay() throws Exception {
		assertThat(get("/ui/test/hello.txt", null).headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
	}

	@ParameterizedTest
	@ValueSource(strings = {"/ui/missing.png", "/ui/", "/ui/test/", "/ui"})
	void missingFileOrFolderIsNotFound(final String path) throws Exception {
		assertThat(get(path, null).statusCode()).isEqualTo(404);
	}

	@Test
	void onlyReadingIsOpen() throws Exception {
		final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ui/test/hello.txt"))
				.POST(HttpRequest.BodyPublishers.noBody()).build();

		assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
	}

	/** Nothing outside {@code ui/} on the classpath (configuration, classes) is reachable. */
	@ParameterizedTest
	@ValueSource(strings = {"/ui/../application.yaml", "/ui/%2e%2e/application.yaml", "/ui/..%2fapplication.yaml",
			"/ui/..%5capplication.yaml", "/ui/%252e%252e/application.yaml", "/ui/test/../../application.yaml",
			"/ui/../com/gratchev/mizoine/MizoineApplication.class", "/ui/%2e%2e/users-test.yaml",
			"/ui//application.yaml", "/ui/;x=1/../application.yaml"})
	void nothingOutsideUiIsServed(final String path) throws Exception {
		final HttpResponse<byte[]> response = get(path, null);

		assertThat(response.statusCode()).as(path).isNotEqualTo(200);
		assertThat(new String(response.body(), StandardCharsets.ISO_8859_1)).as(path)
				.doesNotContain("users-file").doesNotContain("bcrypt").doesNotContain("spring:");
	}

	@Test
	void repositoriesStillWork() throws Exception {
		assertThat(get("/notes/", "artem:artem-pass").statusCode()).isEqualTo(200);
		assertThat(get("/notes/", null).statusCode()).isEqualTo(302);
	}

	private HttpResponse<byte[]> get(final String path, final String basicAuth) throws IOException, InterruptedException {
		final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Accept", "text/html,*/*;q=0.8");
		if (basicAuth != null) {
			request.header("Authorization",
					"Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8)));
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
	}
}
