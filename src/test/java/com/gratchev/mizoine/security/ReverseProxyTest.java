package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

/**
 * Step 12: running behind nginx, which terminates HTTPS and forwards plain HTTP with
 * {@code X-Forwarded-*} headers. Needs a real server: Tomcat evaluates these headers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = {"mizoine.users-file=src/test/resources/users-test.yaml",
		"mizoine.repositories-dir=src/test/resources/repositories"})
class ReverseProxyTest {

	private final ProxyHttp http;
	private final Environment environment;

	ReverseProxyTest(@LocalServerPort final int port, @Autowired final Environment environment) {
		this.http = new ProxyHttp(port);
		this.environment = environment;
	}

	@Test
	void redirectToLoginStaysOnHttps() throws Exception {
		final HttpResponse<String> response = http.getViaHttpsProxy("/");

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location")).hasValue("/login");
	}

	/**
	 * Redirects are relative, so the browser stays on the host it used, even if the proxy does not pass
	 * the public host name on (bug: Cloudflare → nginx → app redirected to https://localhost/login).
	 */
	@Test
	void redirectToLoginIsRelativeWithoutForwardedHost() throws Exception {
		final HttpResponse<String> response = http.getViaHttpsProxyWithoutHost("/", null);

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location")).hasValue("/login");
	}

	@Test
	void folderRedirectIsRelativeWithoutForwardedHost() throws Exception {
		final HttpResponse<String> response = http.getViaHttpsProxyWithoutHost("/notes", "artem:artem-pass");

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location")).hasValue("/notes/");
	}

	@Test
	void httpsRequestGetsHsts() throws Exception {
		final HttpResponse<String> response = http.getViaHttpsProxy("/login");

		assertThat(response.headers().firstValue("Strict-Transport-Security"))
				.hasValueSatisfying(hsts -> assertThat(hsts).contains("max-age="));
	}

	@Test
	void sessionCookieIsSecureHttpOnlyAndSameSiteLax() throws Exception {
		final String cookie = ProxyHttp.sessionCookie(http.getViaHttpsProxy("/login"));

		assertThat(cookie).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
	}

	@Test
	void listensOnLoopbackOnlyByDefault() {
		// only nginx on the same machine can reach the application
		assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
	}
}
