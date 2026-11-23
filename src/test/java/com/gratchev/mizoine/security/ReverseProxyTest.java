package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import com.gratchev.mizoine.TestUsers;

/**
 * Step 12: running behind nginx, which terminates HTTPS and forwards plain HTTP with
 * {@code X-Forwarded-*} headers. Needs a real server: Tomcat evaluates these headers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
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
		final HttpResponse<String> response = http.getViaHttpsProxyWithoutHost("/notes", TestUsers.ARTEM);

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location")).hasValue("/notes/");
	}

	/**
	 * nginx talks HTTP/1.0 to the application unless {@code proxy_http_version 1.1} is set, and Tomcat makes
	 * redirects absolute for HTTP/1.0 whatever {@code server.tomcat.use-relative-redirects} says
	 * (bug: still https://localhost/login behind Cloudflare → nginx).
	 */
	@Test
	void redirectToLoginIsRelativeForHttp10Proxy() throws Exception {
		assertThat(http.locationViaHttp10Proxy("/", null)).hasValue("/login");
	}

	@Test
	void folderRedirectIsRelativeForHttp10Proxy() throws Exception {
		assertThat(http.locationViaHttp10Proxy("/notes", TestUsers.ARTEM)).hasValue("/notes/");
	}

	@Test
	void httpsRequestGetsHsts() throws Exception {
		final HttpResponse<String> response = http.getViaHttpsProxy("/login");

		assertThat(response.headers().firstValue("Strict-Transport-Security"))
				.hasValueSatisfying(hsts -> assertThat(hsts).contains("max-age="));
	}

	@Test
	void sessionCookieIsSecureHttpOnlyAndSameSiteLax() throws Exception {
		final String cookie = ProxyHttp.sessionCookie(http.formLoginViaHttpsProxy(TestUsers.ARTEM));

		assertThat(cookie).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
	}

	/** D27: readable by the pages' JavaScript (not HttpOnly), otherwise as safe as the session cookie. */
	@Test
	void csrfCookieIsSecureAndSameSiteLaxButReadableByScripts() throws Exception {
		final String cookie = ProxyHttp.cookie(http.getViaHttpsProxy("/login"), "XSRF-TOKEN");

		assertThat(cookie).contains("Secure").contains("SameSite=Lax").doesNotContain("HttpOnly");
	}

	@Test
	void listensOnLoopbackOnlyByDefault() {
		// only nginx on the same machine can reach the application
		assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
	}
}
