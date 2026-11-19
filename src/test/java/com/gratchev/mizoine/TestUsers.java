package com.gratchev.mizoine;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The users of {@code src/test/resources/users-test.yaml}, the users file of all Spring Boot tests
 * ({@code src/test/resources/config/application.yaml}). Each password is {@code <name>-pass}.
 */
public final class TestUsers {

	/** Access to the whole of every test repository. */
	public static final String ARTEM = "artem";
	/** Access to parts only. */
	public static final String GUEST = "guest";

	private TestUsers() {
	}

	public static String password(final String user) {
		return user + "-pass";
	}

	/** MockMvc: log in with HTTP Basic. */
	public static RequestPostProcessor loggedInAs(final String user) {
		return httpBasic(user, password(user));
	}

	/** Real HTTP: value of the {@code Authorization} header. */
	public static String basicAuthHeader(final String user) {
		final String credentials = user + ":" + password(user);
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}
}
