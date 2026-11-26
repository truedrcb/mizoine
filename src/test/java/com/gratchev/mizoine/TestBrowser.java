package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.servlet.http.Cookie;

/**
 * A browser for page tests: logs in with the login form, keeps the session and the CSRF cookie, and sends a page's
 * forms with the page's token, as a browser does. No test shortcut for CSRF, so the real protection is tested.
 */
public final class TestBrowser {

	private static final Pattern TOKEN = Pattern.compile(
			"name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"");

	private final MockMvc mockMvc;
	private MockHttpSession session;
	/** The CSRF cookie, as the last response set it. */
	private Cookie xsrf;

	private TestBrowser(final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/** Logs in as a browser does: login page, cookie and form token. */
	public static TestBrowser login(final MockMvc mockMvc, final String user) throws Exception {
		final TestBrowser browser = new TestBrowser(mockMvc);
		final MvcResult login = browser.remember(mockMvc.perform(get("/login")).andReturn());
		final MvcResult loggedIn = browser.remember(mockMvc.perform(post("/login").cookie(browser.xsrf)
						.param("username", user).param("password", password(user))
						.param("_csrf", token(login.getResponse().getContentAsString())))
				.andExpect(status().is3xxRedirection()).andReturn());
		browser.session = (MockHttpSession) loggedIn.getRequest().getSession();
		return browser;
	}

	/** @return the HTML of a page, which must answer 200 */
	public String page(final String url) throws Exception {
		return remember(mockMvc.perform(get(url).session(session).cookie(xsrf).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk()).andReturn()).getResponse().getContentAsString();
	}

	/** A form of the page as the browser sends it: its token and the cookie. */
	public MockHttpServletRequestBuilder form(final String action, final String page) {
		return post(action).session(session).cookie(xsrf).contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("_csrf", token(page));
	}

	/** A form another site sends with this browser's session and cookies: without the page's token. */
	public MockHttpServletRequestBuilder crossSiteForm(final String action) {
		return post(action).session(session).cookie(xsrf).contentType(MediaType.APPLICATION_FORM_URLENCODED);
	}

	private MvcResult remember(final MvcResult result) {
		final MockHttpServletResponse response = result.getResponse();
		if (response.getCookie("XSRF-TOKEN") != null && !response.getCookie("XSRF-TOKEN").getValue().isEmpty()) {
			xsrf = response.getCookie("XSRF-TOKEN");
		}
		return result;
	}

	private static String token(final String page) {
		final Matcher hidden = TOKEN.matcher(page);
		assertThat(hidden.find()).as("CSRF field in the page").isTrue();
		return hidden.group(1) != null ? hidden.group(1) : hidden.group(2);
	}
}
