package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 8: error responses do not leak internals. Needs a real server:
 * MockMvc does not render Spring Boot's error responses.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = "mizoine.users-file=src/test/resources/users-test.yaml")
@Import(ErrorResponseTest.FailingController.class)
class ErrorResponseTest {

	static final String SECRET = "internal-secret-detail";

	@RestController
	static class FailingController {
		@GetMapping("/test/fail")
		String fail() {
			throw new IllegalStateException(SECRET);
		}
	}

	private final int port;
	private final HttpClient client = HttpClient.newHttpClient();

	ErrorResponseTest(@LocalServerPort final int port) {
		this.port = port;
	}

	private HttpResponse<String> get(final String path, final String accept) throws IOException, InterruptedException {
		final String credentials = Base64.getEncoder().encodeToString("artem:artem-pass".getBytes(StandardCharsets.UTF_8));
		final HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Authorization", "Basic " + credentials)
				.header("Accept", accept)
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"application/json", "text/html"})
	void serverErrorDoesNotLeakDetails(final String accept) throws Exception {
		final HttpResponse<String> response = get("/test/fail", accept);

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body())
				.doesNotContain(SECRET)
				.doesNotContain("IllegalStateException")
				.doesNotContain("at com.gratchev")
				.doesNotContain("trace");
	}

	@ParameterizedTest
	@ValueSource(strings = {"application/json", "text/html"})
	void notFoundDoesNotLeakDetails(final String accept) throws Exception {
		final HttpResponse<String> response = get("/no/such/page", accept);

		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.body())
				.doesNotContain("Exception")
				.doesNotContain("trace");
	}
}
