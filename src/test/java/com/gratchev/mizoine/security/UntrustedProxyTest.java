package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.gratchev.mizoine.TestUsers;

/**
 * Step 12: forwarded headers count only when they come from a trusted proxy.
 * Here only 192.0.2.1 (a documentation address) is trusted, so the test client on 127.0.0.1 is not.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"server.tomcat.remoteip.internal-proxies=192.0.2.1/32"})
class UntrustedProxyTest {

	private final ProxyHttp http;

	UntrustedProxyTest(@LocalServerPort final int port) {
		this.http = new ProxyHttp(port);
	}

	@Test
	void forwardedHttpsFromUntrustedAddressIsIgnored() throws Exception {
		final HttpResponse<String> response = http.getViaHttpsProxy("/login");

		assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
		assertThat(ProxyHttp.cookie(response, "XSRF-TOKEN")).doesNotContain("Secure");
		assertThat(ProxyHttp.sessionCookie(http.formLoginViaHttpsProxy(TestUsers.ARTEM))).doesNotContain("Secure");
	}
}
