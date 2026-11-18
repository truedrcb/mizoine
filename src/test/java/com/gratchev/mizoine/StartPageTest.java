package com.gratchev.mizoine;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Step 19: the start page greets the user and lists their repositories.
 * Test repositories ({@code src/test/resources/repositories/}): notes and moved (artem: all, guest: public),
 * private (artem only), broken (unreadable). moved has a missing root folder.
 */
@SpringBootTest(properties = {
		"mizoine.users-file=src/test/resources/users-test.yaml",
		"mizoine.repositories-dir=src/test/resources/repositories"})
@AutoConfigureMockMvc
class StartPageTest {

	private final MockMvc mockMvc;

	StartPageTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private static MockHttpServletRequestBuilder startPage(final String user) {
		return get("/").with(httpBasic(user, user + "-pass"));
	}

	// greeting (from steps 2 and 7)

	@Test
	void greetsTheUser() throws Exception {
		mockMvc.perform(startPage("artem"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("Hello, artem")));
	}

	@Test
	@WithMockUser("<script>alert(1)</script>")
	void usernameIsHtmlEscaped() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Hello, &lt;script&gt;alert(1)&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>"))));
	}

	// 1. the user's repositories as links, sorted

	@Test
	void listsOnlyTheUsersRepositoriesSortedAsLinks() throws Exception {
		mockMvc.perform(startPage("guest"))
				.andExpect(content().string(containsString("<a href=\"moved/\">moved</a>")))
				.andExpect(content().string(containsString("<a href=\"notes/\">notes</a>")))
				.andExpect(content().string(not(containsString("private"))))
				.andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(">moved<", ">notes<")));
	}

	@Test
	void userWithMoreGrantsSeesMore() throws Exception {
		mockMvc.perform(startPage("artem"))
				.andExpect(content().string(org.hamcrest.Matchers.stringContainsInOrder(">moved<", ">notes<", ">private<")));
	}

	// 2. no grant

	@Test
	@WithMockUser("nobody")
	void userWithoutGrantsGetsAMessage() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("No accessible repositories configured for you.")))
				.andExpect(content().string(not(containsString("<a href=\"notes/\">"))));
	}

	// 4. readable with problems (D8)

	@Test
	void repositoryWithProblemsIsMarkedAndExplainedToWholeRepositoryUsers() throws Exception {
		mockMvc.perform(startPage("artem"))
				.andExpect(content().string(containsString("not available")))
				.andExpect(content().string(containsString("missing-root-folder")));
	}

	@Test
	void repositoryWithProblemsIsMarkedWithoutDetailsForPartialUsers() throws Exception {
		mockMvc.perform(startPage("guest"))
				.andExpect(content().string(containsString("not available")))
				.andExpect(content().string(not(containsString("missing-root-folder"))));
	}

	// 5. unreadable

	@Test
	void unreadableRepositoryFilesAreNotListed() throws Exception {
		mockMvc.perform(startPage("artem"))
				.andExpect(content().string(not(containsString("broken"))));
	}

	// 6. JSON (D6)

	@Test
	void jsonListsTheUsersRepositories() throws Exception {
		mockMvc.perform(startPage("guest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.user").value("guest"))
				.andExpect(jsonPath("$.repositories", hasSize(2)))
				.andExpect(jsonPath("$.repositories[0].name").value("moved"))
				.andExpect(jsonPath("$.repositories[0].available").value(false))
				.andExpect(jsonPath("$.repositories[0].problems").doesNotExist())
				.andExpect(jsonPath("$.repositories[1].name").value("notes"))
				.andExpect(jsonPath("$.repositories[1].available").value(true));
	}

	@Test
	void jsonShowsProblemsToWholeRepositoryUsers() throws Exception {
		mockMvc.perform(startPage("artem").accept(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.repositories[0].name").value("moved"))
				.andExpect(jsonPath("$.repositories[0].problems[0]").value(containsString("missing-root-folder")))
				.andExpect(jsonPath("$.repositories[1].problems").doesNotExist());
	}

	@Test
	@WithMockUser("nobody")
	void jsonWithoutRepositoriesIsAnEmptyList() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.repositories", hasSize(0)));
	}

	// content negotiation (D6): HTML unless JSON is asked for

	@Test
	void withoutAcceptHeaderItIsHtml() throws Exception {
		mockMvc.perform(startPage("artem"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}

	@Test
	void acceptAnythingGivesHtml() throws Exception {
		mockMvc.perform(startPage("artem").accept(MediaType.ALL))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}

	@Test
	void browserAcceptHeaderGivesHtml() throws Exception {
		mockMvc.perform(startPage("artem")
						.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}

	@Test
	void otherFormatsAreNotAcceptable() throws Exception {
		mockMvc.perform(startPage("artem").accept(MediaType.APPLICATION_XML))
				.andExpect(status().isNotAcceptable());
	}

	// 7. anonymous

	@Test
	void anonymousBrowserIsSentToLogin() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
				.andExpect(redirectedUrl("/login"));
	}
}
