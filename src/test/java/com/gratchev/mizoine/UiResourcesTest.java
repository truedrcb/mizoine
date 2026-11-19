package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * {@code /ui/**}: public files for the user interface (logos, later scripts and styles), from
 * {@code src/main/resources/ui/}, without login. A real server, so that Tomcat's URL handling is part of it.
 * {@code ui/test/hello.txt} exists only in the test resources.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UiResourcesTest {

	private final TestHttp http;

	UiResourcesTest(@LocalServerPort final int port) {
		this.http = new TestHttp(port);
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
		assertThat(get("/ui/test/hello.txt", ARTEM).statusCode()).isEqualTo(200);
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
		final HttpRequest.Builder post = http.request("/ui/test/hello.txt", null).POST(HttpRequest.BodyPublishers.noBody());

		assertThat(http.send(post, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
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
		assertThat(get("/notes/", ARTEM).statusCode()).isEqualTo(200);
		assertThat(get("/notes/", null).statusCode()).isEqualTo(302);
	}

	private HttpResponse<byte[]> get(final String path, final String user) throws IOException, InterruptedException {
		return http.send(http.request(path, user).header("Accept", "text/html,*/*;q=0.8"),
				HttpResponse.BodyHandlers.ofByteArray());
	}
}
