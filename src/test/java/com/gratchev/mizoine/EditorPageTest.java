package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gratchev.mizoine.security.SecurityConfig;

import jakarta.servlet.http.Cookie;

/**
 * Step 37: the editor page {@code /ui/editor.html?file=…} is a static file; it uses only the file API. What the
 * server must give it: the page without login, a CSRF token it can read (D27), and a 401 it can handle itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EditorPageTest {

	private static final String XHR = "X-Requested-With";

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("tree/notes/plan.md", "# Plan")
			.repository("tree", "path: tree\n" + TestRepositories.ARTEM_EVERYTHING);

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void deleteTree() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	EditorPageTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/**
	 * Logs in as a browser does: the login page gives the cookie and the form token. Not spring-security-test's
	 * formLogin(): its csrf() replaces the token repository of the test context with a session-based one.
	 */
	private MockHttpSession browserSession() throws Exception {
		final MvcResult page = mockMvc.perform(get("/login")).andReturn();
		final Matcher hidden = Pattern.compile("name=\"_csrf\" type=\"hidden\" value=\"([^\"]+)\"")
				.matcher(page.getResponse().getContentAsString());
		assertThat(hidden.find()).as("CSRF field in the login page").isTrue();
		return (MockHttpSession) mockMvc.perform(post("/login")
						.cookie(page.getResponse().getCookie("XSRF-TOKEN"))
						.param("username", ARTEM).param("password", password(ARTEM)).param("_csrf", hidden.group(1)))
				.andExpect(status().is3xxRedirection())
				.andReturn().getRequest().getSession();
	}

	// the page

	@Test
	void pageAndScriptsLoadWithoutLoginUnderTheStrictCsp() throws Exception {
		mockMvc.perform(get("/ui/editor.html").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("<textarea")))
				.andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY));
		mockMvc.perform(get("/ui/editor/editor.js")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
		mockMvc.perform(get("/ui/editor/page.js")).andExpect(status().isOk());
		// step 39: the CodeMirror bundle and its license notice
		mockMvc.perform(get("/ui/vendor/codemirror.js")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
		mockMvc.perform(get("/ui/vendor/codemirror.LICENSE.txt")).andExpect(status().isOk())
				.andExpect(content().string(containsString("MIT License")));
	}

	@Test
	void pageHasNoInlineScriptsOrStyles() throws Exception {
		// the CSP would block them: everything comes from files
		final String html = mockMvc.perform(get("/ui/editor.html")).andReturn().getResponse().getContentAsString();

		assertThat(html).doesNotContainPattern("<script(?![^>]*\\ssrc=)").doesNotContain("style=").doesNotContain("<style");
	}

	// D27: a CSRF token the page can read, sent back in a header

	@Test
	void loadingAFileGivesTheCsrfCookieReadableByJavaScript() throws Exception {
		final Cookie cookie = mockMvc.perform(get("/tree/notes/plan.md").session(browserSession()).header(XHR, "XMLHttpRequest"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");

		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isFalse();
	}

	@Test
	void theCookiesTokenInTheHeaderAllowsASave() throws Exception {
		final MockHttpSession session = browserSession();
		final MvcResult loaded = mockMvc.perform(get("/tree/notes/plan.md").session(session)).andReturn();
		final Cookie token = loaded.getResponse().getCookie("XSRF-TOKEN");

		mockMvc.perform(put("/tree/notes/plan.md").session(session).cookie(token).content("# Saved")
						.header("X-XSRF-TOKEN", token.getValue()))
				.andExpect(status().isNoContent());
		assertThat(Files.readString(REPOSITORIES.path("tree/notes/plan.md"))).isEqualTo("# Saved");
	}

	@Test
	void withoutTheHeaderTheSaveIsForbidden() throws Exception {
		final MockHttpSession session = browserSession();
		final Cookie token = mockMvc.perform(get("/tree/notes/plan.md").session(session)).andReturn().getResponse()
				.getCookie("XSRF-TOKEN");

		mockMvc.perform(put("/tree/notes/plan.md").session(session).cookie(token).content("x"))
				.andExpect(status().isForbidden());
	}

	// a 401 the page handles itself, without the browser's login dialog

	@Test
	void notLoggedInScriptGets401WithoutBasicChallenge() throws Exception {
		mockMvc.perform(get("/tree/notes/plan.md").header(XHR, "XMLHttpRequest").header(HttpHeaders.ACCEPT, "*/*"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
	}

	@Test
	void aScriptsRequestIsNotThePageToReturnToAfterLogin() throws Exception {
		// the editor opens the login in a new tab; that tab must land on the start page, not on the raw file
		final MockHttpSession session = (MockHttpSession) mockMvc
				.perform(get("/tree/notes/plan.md").header(XHR, "XMLHttpRequest"))
				.andExpect(status().isUnauthorized())
				.andReturn().getRequest().getSession();

		assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
	}
}
