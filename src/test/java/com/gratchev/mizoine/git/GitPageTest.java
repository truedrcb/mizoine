package com.gratchev.mizoine.git;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.gratchev.mizoine.TestRepositories;

/**
 * Step 43: {@code /{repository}/.git/} is the reserved address of the Git page, never the {@code .git} folder.
 * artem has grant {@code /} (Git included), guest only a folder in {@code tree}, and only {@code .git} in {@code gitonly}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GitPageTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("tree/public/note.md", "note")
			.file("tree/.git/config", "[core]")
			.file("tree/.git/HEAD", "ref: refs/heads/main")
			.file("tree/README.md", "readme")
			.repository("tree", "path: tree\naccess:\n  artem: [\"/\"]\n  guest: [public]\n")
			.repository("gitonly", "path: tree\naccess:\n  guest: [.git]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void deleteRepositories() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	GitPageTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void withGitAccessThePageIsShown() throws Exception {
		mockMvc.perform(get("/tree/.git/").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Git")))
				.andExpect(content().string(containsString("href=\"../\"")));
		mockMvc.perform(get("/gitonly/.git/").with(loggedInAs(GUEST)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk());
	}

	@Test
	void withoutGitAccessItIsNotFound() throws Exception {
		// D2: the same as anything else the user may not see
		mockMvc.perform(get("/tree/.git/").with(loggedInAs(GUEST)).accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
		mockMvc.perform(get("/tree/.git").with(loggedInAs(GUEST)).accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
		mockMvc.perform(get("/nothing/.git/").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
	}

	@Test
	void withoutTrailingSlashItRedirects() throws Exception {
		mockMvc.perform(get("/tree/.git").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/tree/.git/"));
	}

	/** The real .git folder stays unreachable as files (D3), for every user and method. */
	@ParameterizedTest
	@ValueSource(strings = {"/tree/.git/config", "/tree/.git/HEAD", "/tree/.git/objects/", "/tree/.git/x/../config"})
	void theGitFolderIsNeverReadOrWritten(final String path) throws Exception {
		mockMvc.perform(get(path).with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML)).andExpect(status().is4xxClientError());
		mockMvc.perform(get(path).with(loggedInAs(ARTEM))).andExpect(status().is4xxClientError());
		mockMvc.perform(put(path).with(loggedInAs(ARTEM)).content("x")).andExpect(status().is4xxClientError());
		mockMvc.perform(delete(path).with(loggedInAs(ARTEM))).andExpect(status().is4xxClientError());
	}

	@Test
	void theRootLinksToGitForUsersWithGitAccess() throws Exception {
		mockMvc.perform(get("/tree/").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("href=\".git/\"")));
		mockMvc.perform(get("/tree/").with(loggedInAs(GUEST)).accept(MediaType.TEXT_HTML))
				.andExpect(content().string(not(containsString(".git"))));
		mockMvc.perform(get("/tree/public/").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML))
				.andExpect(content().string(not(containsString(".git"))));
	}

	@Test
	void withGitAccessAloneTheRootShowsOnlyTheWayToGit() throws Exception {
		mockMvc.perform(get("/gitonly/").with(loggedInAs(GUEST)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\".git/\"")))
				.andExpect(content().string(not(containsString("README.md"))))
				.andExpect(content().string(not(containsString("public"))));
	}
}
