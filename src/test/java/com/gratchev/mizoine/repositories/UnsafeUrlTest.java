package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionTrackingMode;

/**
 * Step 20: URLs that try to leave a repository, reach another one or a hidden folder never succeed —
 * in-process (MockMvc) and on a real server (Tomcat normalizes some paths before Spring sees them).
 * As guest, who may see only notes/public. Stays as a guard for the folder listing (step 21).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"mizoine.users-file=src/test/resources/users-test.yaml",
		"mizoine.repositories-dir=" + RepositoryControllerTest.REPOSITORIES})
@AutoConfigureMockMvc
class UnsafeUrlTest {

	private final MockMvc mockMvc;
	private final int port;
	private final ServletContext servletContext;
	private final HttpClient client = HttpClient.newHttpClient();

	UnsafeUrlTest(@Autowired final MockMvc mockMvc, @LocalServerPort final int port,
			@Autowired final ServletContext servletContext) {
		this.mockMvc = mockMvc;
		this.port = port;
		this.servletContext = servletContext;
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/notes/../private/", "/notes/public/../../private/", "/notes/../../", "/notes/%2e%2e/private/",
			"/notes/%2E%2E/%2E%2E/", "/notes/..%2fprivate/", "/notes/public%2f..%2f..%2fprivate/",
			"/notes/..%5cprivate/", "/notes/public%5c..%5c..%5c/", "/notes/%00/", "/notes/public%00/",
			"/notes/.git/", "/notes/%2egit/", "/notes/;x=1/../private/",
			// double-encoded attacks: an encoded '%' is rejected by the firewall
			"/notes/%252e%252e/private/", "/notes/public/%252e%252e/%252e%252e/", "/notes/%252e%252e%252fprivate/", "/notes/%252egit/"})
	void rejectedInProcess(final String path) throws Exception {
		final int status = mockMvc.perform(get(URI.create(path)).with(httpBasic("guest", "guest-pass")))
				.andReturn().getResponse().getStatus();

		assertThat(status).as(path).isIn(400, 404);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/notes/../private/", "/notes/public/../../private/", "/notes/../../", "/notes/%2e%2e/private/",
			"/notes/%2E%2E/%2E%2E/", "/notes/..%2fprivate/", "/notes/public%2f..%2f..%2fprivate/",
			"/notes/..%5cprivate/", "/notes/public%5c..%5c..%5c/", "/notes/%00/", "/notes/public%00/",
			"/notes/.git/", "/notes/%2egit/", "/notes/;x=1/../private/",
			// double-encoded attacks: an encoded '%' is rejected by the firewall
			"/notes/%252e%252e/private/", "/notes/public/%252e%252e/%252e%252e/", "/notes/%252e%252e%252fprivate/", "/notes/%252egit/"})
	void rejectedOnARealServer(final String path) throws Exception {
		final String credentials = Base64.getEncoder().encodeToString("guest:guest-pass".getBytes(StandardCharsets.UTF_8));
		final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Authorization", "Basic " + credentials)
				.build();

		final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		// not a redirect to the login page either: the error page itself needs no login
		assertThat(response.statusCode()).as(path).isIn(400, 404);
	}

	@Test
	void sessionIdIsTrackedByCookieOnly() {
		// never ;jsessionid=... in URLs, where it leaks via logs, history and referrers
		assertThat(servletContext.getEffectiveSessionTrackingModes()).containsExactly(SessionTrackingMode.COOKIE);
	}

	@Test
	void sessionIdIsNeverPutIntoUrls() throws Exception {
		// first request of an anonymous browser: no session cookie yet; Tomcat would add ;jsessionid= to the URL
		final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/notes/"))
				.header("Accept", "text/html")
				.build();

		final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location")).hasValueSatisfying(location -> assertThat(location)
				.doesNotContainIgnoringCase("jsessionid"));
	}
}
