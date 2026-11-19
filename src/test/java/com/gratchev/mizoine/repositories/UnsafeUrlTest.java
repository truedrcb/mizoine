package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.gratchev.mizoine.TestHttp;

import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionTrackingMode;

/**
 * Step 20: URLs that try to leave a repository, reach another one or a hidden folder never succeed —
 * in-process (MockMvc) and on a real server (Tomcat normalizes some paths before Spring sees them).
 * As guest, who may see only notes/public. Stays as a guard for the folder listing (step 21).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UnsafeUrlTest {

	private final MockMvc mockMvc;
	private final ServletContext servletContext;
	private final TestHttp http;

	UnsafeUrlTest(@Autowired final MockMvc mockMvc, @LocalServerPort final int port,
			@Autowired final ServletContext servletContext) {
		this.mockMvc = mockMvc;
		this.http = new TestHttp(port);
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
		final int status = mockMvc.perform(get(URI.create(path)).with(loggedInAs(GUEST)))
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
		final HttpResponse<String> response = http.send(http.request(path, GUEST), HttpResponse.BodyHandlers.ofString());

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
		final HttpResponse<String> response = http.get("/notes/", null);

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location")).hasValueSatisfying(location -> assertThat(location)
				.doesNotContainIgnoringCase("jsessionid"));
	}
}
