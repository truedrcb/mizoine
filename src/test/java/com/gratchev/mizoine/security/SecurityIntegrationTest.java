package com.gratchev.mizoine.security;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Users come from {@code src/test/resources/users-test.yaml}:
 * artem / artem-pass, guest / guest-pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

	private final MockMvc mockMvc;

	SecurityIntegrationTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void anonymousBrowserIsRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void anonymousNonBrowserGets401WithBasicChallenge() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", startsWith("Basic")));
	}

	@Test
	void errorPageRequestedDirectlyStillNeedsLogin() throws Exception {
		// only the internal rendering of an error (error dispatch) is open, not the URL /error
		mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML))
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void loginPageIsPublic() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk());
	}

	@Test
	void basicAuthWithValidUserShowsHello() throws Exception {
		mockMvc.perform(get("/").with(loggedInAs(ARTEM)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Hello")));
	}

	@Test
	void basicAuthWithSecondUserIsGreetedByName() throws Exception {
		mockMvc.perform(get("/").with(loggedInAs(GUEST)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Hello, guest")));
	}

	@Test
	void basicAuthWithWrongPasswordGets401() throws Exception {
		mockMvc.perform(get("/").with(httpBasic("artem", "wrong")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void basicAuthWithUnknownUserGets401() throws Exception {
		mockMvc.perform(get("/").with(httpBasic("nobody", "artem-pass")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void basicAuthUsernameIsCaseSensitive() throws Exception {
		mockMvc.perform(get("/").with(httpBasic("Artem", "artem-pass")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void formLoginWithValidUserRedirectsToRoot() throws Exception {
		mockMvc.perform(formLogin().user(ARTEM).password(password(ARTEM)))
				.andExpect(redirectedUrl("/"))
				.andExpect(authenticated().withUsername("artem").withRoles("USER"));
	}

	/**
	 * After login the browser goes back to the page it first asked for, relative to the host it used
	 * (bug: https://localhost/ behind Cloudflare → nginx, which did not pass the public host on).
	 */
	@Test
	void formLoginReturnsToSavedPageWithRelativeUrl() throws Exception {
		final MockHttpSession session = (MockHttpSession) mockMvc
				.perform(get("/notes/projects/").queryParam("q", "a b").accept(MediaType.TEXT_HTML))
				.andExpect(redirectedUrl("/login"))
				.andReturn().getRequest().getSession();

		mockMvc.perform(post("/login").param("username", ARTEM).param("password", password(ARTEM))
						.with(csrf()).session(session))
				.andExpect(authenticated())
				.andExpect(redirectedUrl("/notes/projects/?q=a%20b"));
	}

	@Test
	void formLoginWithWrongPasswordReturnsToLoginWithError() throws Exception {
		mockMvc.perform(formLogin().user("artem").password("wrong"))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());
	}

	@Test
	void sameUserCanLogInTwice() throws Exception {
		// guards against cached UserDetails whose password was erased after the first login
		mockMvc.perform(formLogin().user(ARTEM).password(password(ARTEM))).andExpect(authenticated());
		mockMvc.perform(formLogin().user(ARTEM).password(password(ARTEM))).andExpect(authenticated());
	}

	@Test
	void browserLogoutInvalidatesSessionAndRedirects() throws Exception {
		final MockHttpSession session = (MockHttpSession) mockMvc
				.perform(formLogin().user(ARTEM).password(password(ARTEM)))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession();

		mockMvc.perform(post("/logout").with(csrf()).session(session).accept(MediaType.TEXT_HTML))
				.andExpect(redirectedUrl("/login?logout"))
				.andExpect(unauthenticated());

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void nonBrowserLogoutGets204WithoutRedirect() throws Exception {
		mockMvc.perform(post("/logout").with(csrf()).with(loggedInAs(ARTEM)).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNoContent());
	}

	@Test
	void formPostWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/login").param("username", ARTEM).param("password", password(ARTEM)))
				.andExpect(status().isForbidden());
	}

	@Test
	void formPostWithCsrfTokenIsAccepted() throws Exception {
		mockMvc.perform(post("/login").param("username", ARTEM).param("password", password(ARTEM)).with(csrf()))
				.andExpect(redirectedUrl("/"));
	}
}
