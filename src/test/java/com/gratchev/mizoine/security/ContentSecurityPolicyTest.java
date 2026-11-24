package com.gratchev.mizoine.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Step 35: the login form may lead on to the configured chat apps' callbacks, and nowhere else. */
class ContentSecurityPolicyTest {

	@Test
	void withoutOAuthFormsGoToThisServerOnly() {
		assertThat(SecurityConfig.contentSecurityPolicy(List.of())).isEqualTo(SecurityHeadersTest.CSP);
	}

	@Test
	void eachCallbackOriginIsAllowedOnce() {
		assertThat(SecurityConfig.contentSecurityPolicy(List.of("https://claude.ai/api/mcp/auth_callback",
				"https://claude.ai/other/callback", "http://127.0.0.1:8080/cb?x=1")))
				.contains("; form-action 'self' http://127.0.0.1:8080 https://claude.ai; ");
	}
}
