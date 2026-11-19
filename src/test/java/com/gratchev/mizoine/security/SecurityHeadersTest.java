package com.gratchev.mizoine.security;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/** Step 8: security headers, and no hint whether a username exists. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

	// spelled out on purpose: a change of the policy must be a conscious change of this test too
	static final String CSP = "default-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

	private final MockMvc mockMvc;

	SecurityHeadersTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private static ResultMatcher[] securityHeaders() {
		return new ResultMatcher[] {
				header().string("X-Content-Type-Options", "nosniff"),
				header().string("X-Frame-Options", "DENY"),
				header().string("Content-Security-Policy", CSP),
				header().string("Referrer-Policy", "same-origin")};
	}

	@Test
	void pageForLoggedInUserHasSecurityHeaders() throws Exception {
		mockMvc.perform(get("/").with(loggedInAs(ARTEM)))
				.andExpect(status().isOk())
				.andExpectAll(securityHeaders());
	}

	@Test
	void loginPageHasSecurityHeaders() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpectAll(securityHeaders());
	}

	@Test
	void rejectedRequestHasSecurityHeaders() throws Exception {
		mockMvc.perform(get("/").with(httpBasic("artem", "wrong")))
				.andExpect(status().isUnauthorized())
				.andExpectAll(securityHeaders());
	}

	@Test
	void loginPageStylesheetIsPublicSoCspDoesNotBreakLoginPage() throws Exception {
		// the default login page uses this stylesheet from 'self', no inline styles
		mockMvc.perform(get("/default-ui.css"))
				.andExpect(status().isOk());
	}

	@Test
	void basicAuthUnknownUserAndWrongPasswordLookTheSame() throws Exception {
		final MockHttpServletResponse unknownUser = body(mockMvc.perform(get("/").with(httpBasic("nobody", "artem-pass"))));
		final MockHttpServletResponse wrongPassword = body(mockMvc.perform(get("/").with(httpBasic("artem", "wrong"))));

		assertThat(unknownUser.getStatus()).isEqualTo(401).isEqualTo(wrongPassword.getStatus());
		assertThat(unknownUser.getHeader("WWW-Authenticate")).isEqualTo(wrongPassword.getHeader("WWW-Authenticate"));
		assertThat(unknownUser.getContentAsString()).isEqualTo(wrongPassword.getContentAsString());
	}

	@Test
	void formLoginUnknownUserAndWrongPasswordLookTheSame() throws Exception {
		final MockHttpServletResponse unknownUser = body(mockMvc.perform(formLogin().user("nobody").password("artem-pass")));
		final MockHttpServletResponse wrongPassword = body(mockMvc.perform(formLogin().user("artem").password("wrong")));

		assertThat(unknownUser.getStatus()).isEqualTo(wrongPassword.getStatus());
		assertThat(unknownUser.getRedirectedUrl()).isEqualTo("/login?error").isEqualTo(wrongPassword.getRedirectedUrl());
	}

	private static MockHttpServletResponse body(final ResultActions actions) {
		return actions.andReturn().getResponse();
	}
}
