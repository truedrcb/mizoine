package com.gratchev.mizoine.security;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Step 29, D11: API clients (agents, curl) write with HTTP Basic and cannot send a CSRF token; the browser UI
 * with its session cookie must send one. Cross-site forms cannot send PUT or DELETE, so only those are exempt,
 * and only with an Authorization header. "Not rejected by CSRF" = not 403 (the file API answers the rest).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiClientCsrfTest {

	/** In a folder that does not exist: requests that pass CSRF get 409 and never write into the source tree. */
	private static final String TARGET = "/notes/no-such-folder/new.md";

	private final MockMvc mockMvc;

	ApiClientCsrfTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private int statusOf(final MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andReturn().getResponse().getStatus();
	}

	@Test
	void putAndDeleteWithHttpBasicNeedNoToken() throws Exception {
		assertThat(statusOf(put(TARGET).with(loggedInAs(ARTEM)).content("x"))).isNotEqualTo(403);
		assertThat(statusOf(delete(TARGET).with(loggedInAs(ARTEM)))).isNotEqualTo(403);
	}

	@Test
	void postWithHttpBasicStillNeedsAToken() throws Exception {
		// browsers send a cached Basic login on cross-site form posts too
		mockMvc.perform(post("/logout").with(loggedInAs(ARTEM))).andExpect(status().isForbidden());
	}

	@Test
	void browserSessionNeedsTheTokenForWrites() throws Exception {
		final MockHttpSession session = (MockHttpSession) mockMvc
				.perform(formLogin().user(ARTEM).password(password(ARTEM)))
				.andReturn().getRequest().getSession();

		assertThat(statusOf(put(TARGET).session(session).content("x"))).isEqualTo(403);
		assertThat(statusOf(delete(TARGET).session(session))).isEqualTo(403);
		// that the browser's token (cookie XSRF-TOKEN, header X-XSRF-TOKEN, D27) passes: EditorPageTest, in a context
		// of its own. spring-security-test's csrf() replaces the token repository of the whole test context.
	}
}
