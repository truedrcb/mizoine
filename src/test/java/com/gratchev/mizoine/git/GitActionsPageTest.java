package com.gratchev.mizoine.git;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.FileSystemUtils;

import com.gratchev.mizoine.TestRepositories;

import jakarta.servlet.http.Cookie;

/**
 * Steps 46–48 over HTTP: the page's forms (browser session, CSRF token, 303 back to the page with the result) and
 * the JSON API (no CSRF token: a cross-site form cannot send JSON). artem has {@code /}; guest only a folder.
 * Git runs cut off from this machine's settings ({@link TestGit}), with the test's identity.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GitActionsPageTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("real")
			.repository("real", "path: real\naccess:\n  artem: [\"/\"]\n  guest: [notes]\n");
	private static final TestGit GIT = testGit();

	private static TestGit testGit() {
		try {
			return new TestGit(REPOSITORIES.path("home"));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@TestConfiguration
	static class IsolatedGit {

		@Bean
		@Primary
		GitCommands isolatedGitCommands() {
			// short: one test waits for a remote that does not answer
			return new GitCommands("git", Duration.ofSeconds(3), 1024 * 1024, GIT.environment());
		}
	}

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void deleteRepositories() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;
	private final Path real;
	/** The browser's CSRF cookie, as the last response set it. */
	private Cookie xsrf;

	GitActionsPageTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
		this.real = REPOSITORIES.path("real");
	}

	/** Each test starts from one commit (README.md), a changed README.md and a new file; no remote. */
	@BeforeEach
	void freshRepository() throws Exception {
		FileSystemUtils.deleteRecursively(real);
		FileSystemUtils.deleteRecursively(REPOSITORIES.path("remote.git"));
		GIT.repository(real);
		Files.writeString(real.resolve("README.md"), "changed\n");
		Files.writeString(real.resolve("new ü.md"), "new\n");
	}

	/** A bare remote as upstream of {@code real}. */
	private Path remote() throws Exception {
		final Path remote = Files.createDirectories(REPOSITORIES.path("remote.git"));
		GIT.git(remote, "init", "-q", "--bare");
		GIT.git(real, "remote", "add", "origin", remote.toString());
		GIT.git(real, "push", "-q", "-u", "origin", "main");
		return remote;
	}

	private MockHttpServletRequestBuilder json(final String path, final String body) {
		return post(path).with(loggedInAs(ARTEM)).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
				.content(body);
	}

	// the JSON API

	@Test
	void stageAndUnstageAsJson() throws Exception {
		mockMvc.perform(json("/real/.git/stage", "{\"paths\": [\"new ü.md\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.changes[?(@.path == 'new ü.md')].staged", contains("ADDED")))
				.andExpect(jsonPath("$.changes[?(@.path == 'README.md')].staged", contains("UNCHANGED")));
		mockMvc.perform(json("/real/.git/stage", "{\"all\": true}"))
				.andExpect(jsonPath("$.changes[?(@.path == 'README.md')].staged", contains("MODIFIED")));
		mockMvc.perform(json("/real/.git/unstage", "{\"all\": true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.changes[?(@.path == 'new ü.md')].unstaged", contains("UNTRACKED")));
	}

	@Test
	void aPathOutsideTheChangesIsAConflictWithTheReason() throws Exception {
		mockMvc.perform(json("/real/.git/stage", "{\"paths\": [\"../outside.md\"]}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Not in the changes to stage: ../outside.md"));
	}

	@Test
	void aSelectionOfNothingIsABadRequest() throws Exception {
		mockMvc.perform(json("/real/.git/stage", "{}")).andExpect(status().isBadRequest());
		mockMvc.perform(json("/real/.git/stage", "{\"paths\": []}")).andExpect(status().isBadRequest());
	}

	@Test
	void withoutGitAccessNothingIsStaged() throws Exception {
		mockMvc.perform(post("/real/.git/stage").with(loggedInAs(GUEST)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"all\": true}"))
				.andExpect(status().isNotFound());
		assertThat(GIT.git(real, "-c", "core.quotepath=false", "diff", "--cached", "--name-only")).isEmpty();
	}

	@Test
	void commitAsJsonWithTheUserAsAuthor() throws Exception {
		mockMvc.perform(json("/real/.git/stage", "{\"all\": true}"));

		mockMvc.perform(json("/real/.git/commit", "{\"message\": \"From the API\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clean").value(true));
		assertThat(GIT.git(real, "log", "-1", "--format=%an <%ae>: %s")).isEqualTo("artem <artem@example.org>: From the API\n");
	}

	@Test
	void aCommitWithoutMessageIsABadRequestAndNothingStagedAConflict() throws Exception {
		mockMvc.perform(json("/real/.git/commit", "{\"message\": \"  \"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Write a commit message."));
		mockMvc.perform(json("/real/.git/commit", "{\"message\": \"x\"}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Nothing staged to commit."));
	}

	@Test
	void pushAndPullAsJson() throws Exception {
		final Path remote = remote();
		mockMvc.perform(json("/real/.git/stage", "{\"all\": true}"));
		mockMvc.perform(json("/real/.git/commit", "{\"message\": \"Mine\"}"));

		// no body needed
		mockMvc.perform(post("/real/.git/push").with(loggedInAs(ARTEM)).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upstream.ahead").value(0));
		assertThat(GIT.git(remote, "log", "--format=%s", "main")).isEqualTo("Mine\nfirst\n");

		mockMvc.perform(json("/real/.git/pull", "{}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.upstream.name").value("origin/main"));
	}

	@Test
	void withoutUpstreamPushIsAConflict() throws Exception {
		mockMvc.perform(json("/real/.git/push", "{}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("No upstream branch: set one on the server."));
	}

	@Test
	void aRemoteThatDoesNotAnswerIsAGatewayTimeout() throws Exception {
		remote();
		GIT.git(real, "config", "remote.origin.uploadpack", "sleep 30; true");

		mockMvc.perform(json("/real/.git/pull", "{}")).andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Git did not finish within 3 s")));
	}

	// the page's forms

	@Test
	void theFormsStageAndUnstageAndThePageShowsTheResult() throws Exception {
		final MockHttpSession session = browserSession();

		final String page = page(session);
		assertThat(page).contains("name=\"path\"").contains("value=\"new ü.md\"");
		mockMvc.perform(form("/real/.git/stage", session, page).param("path", "new ü.md"))
				.andExpect(status().isSeeOther())
				.andExpect(header().string(HttpHeaders.LOCATION, "/real/.git/"));
		assertThat(page(session)).contains("Staged 1 file.");
		assertThat(GIT.git(real, "-c", "core.quotepath=false", "diff", "--cached", "--name-only")).isEqualTo("new ü.md\n");

		mockMvc.perform(form("/real/.git/unstage", session, page(session)).param("all", "true"))
				.andExpect(status().isSeeOther());
		assertThat(page(session)).contains("Unstaged 1 file.");
	}

	@Test
	void theCommitForm() throws Exception {
		final MockHttpSession session = browserSession();
		mockMvc.perform(form("/real/.git/stage", session, page(session)).param("all", "true"));

		final String page = page(session);
		assertThat(page).contains("name=\"message\"");
		mockMvc.perform(form("/real/.git/commit", session, page).param("message", "From the page"))
				.andExpect(status().isSeeOther());

		assertThat(page(session)).contains("Committed 2 files.").contains("Nothing to commit");
		assertThat(GIT.git(real, "log", "-1", "--format=%an: %s")).isEqualTo("artem: From the page\n");
	}

	@Test
	void thePushAndPullButtons() throws Exception {
		remote();
		final MockHttpSession session = browserSession();
		final String page = page(session);
		assertThat(page).contains("/real/.git/pull").contains("/real/.git/push").contains("origin/main: up to date");

		mockMvc.perform(form("/real/.git/push", session, page)).andExpect(status().isSeeOther());
		assertThat(page(session)).contains("Pushed to origin/main.");

		GIT.git(real, "config", "remote.origin.uploadpack", "sleep 30; true");
		mockMvc.perform(form("/real/.git/pull", session, page(session))).andExpect(status().isSeeOther());
		assertThat(page(session)).contains("Git did not finish within 3 s");
	}

	@Test
	void aFormThatSelectsNothingSaysSoOnThePage() throws Exception {
		final MockHttpSession session = browserSession();

		mockMvc.perform(form("/real/.git/stage", session, page(session))).andExpect(status().isSeeOther());

		assertThat(page(session)).contains("Choose files, or all.");
	}

	@Test
	void aFormWithoutTheTokenIsRefused() throws Exception {
		// D11: a cross-site form can post, but has no token
		final MockHttpSession session = browserSession();

		mockMvc.perform(post("/real/.git/stage").session(session).param("all", "true")).andExpect(status().isForbidden());
		mockMvc.perform(post("/real/.git/stage").with(loggedInAs(ARTEM)).param("all", "true"))
				.andExpect(status().isForbidden());
		assertThat(GIT.git(real, "-c", "core.quotepath=false", "diff", "--cached", "--name-only")).isEmpty();
	}

	// a browser

	/** Logs in as a browser does (see EditorPageTest): login page, cookie and form token. */
	private MockHttpSession browserSession() throws Exception {
		final MvcResult login = remember(mockMvc.perform(get("/login")).andReturn());
		final MvcResult loggedIn = remember(mockMvc.perform(post("/login").cookie(xsrf)
						.param("username", ARTEM).param("password", password(ARTEM))
						.param("_csrf", token(login.getResponse().getContentAsString())))
				.andExpect(status().is3xxRedirection()).andReturn());
		return (MockHttpSession) loggedIn.getRequest().getSession();
	}

	private String page(final MockHttpSession session) throws Exception {
		return remember(mockMvc.perform(get("/real/.git/").session(session).cookie(xsrf).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk()).andReturn()).getResponse().getContentAsString();
	}

	/** A form of the page as the browser sends it: its token and the cookie. */
	private MockHttpServletRequestBuilder form(final String action, final MockHttpSession session, final String page) {
		return post(action).session(session).cookie(xsrf).contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("_csrf", token(page));
	}

	private MvcResult remember(final MvcResult result) {
		final MockHttpServletResponse response = result.getResponse();
		if (response.getCookie("XSRF-TOKEN") != null && !response.getCookie("XSRF-TOKEN").getValue().isEmpty()) {
			xsrf = response.getCookie("XSRF-TOKEN");
		}
		return result;
	}

	private static String token(final String page) {
		final Matcher hidden = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"")
				.matcher(page);
		assertThat(hidden.find()).as("CSRF field in the page").isTrue();
		return hidden.group(1) != null ? hidden.group(1) : hidden.group(2);
	}
}
