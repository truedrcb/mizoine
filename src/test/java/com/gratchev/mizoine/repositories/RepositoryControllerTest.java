package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Step 18: {@code /{repository}/}. Test repositories in {@code src/test/resources/repositories/}:
 * notes (artem: all, guest: public), private (artem only), moved (root folder missing), broken (unreadable).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RepositoryControllerTest {

	static final String REPOSITORIES = "src/test/resources/repositories";

	private final MockMvc mockMvc;

	RepositoryControllerTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private static MockHttpServletRequestBuilder page(final String path, final String user) {
		return get(path).with(loggedInAs(user));
	}

	private MockHttpServletResponse response(final String path, final String user) throws Exception {
		return mockMvc.perform(page(path, user)).andReturn().getResponse();
	}

	// 1.

	@Test
	void userWithAccessGetsTheRepositoryPage() throws Exception {
		// since step 21 the page lists the root folder (step 18: "ok")
		mockMvc.perform(page("/notes/", "artem"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("notes")));
	}

	@Test
	void userWithPartialAccessGetsOkPageToo() throws Exception {
		mockMvc.perform(page("/notes/", "guest"))
				.andExpect(status().isOk());
	}

	// 2., 3., 9., 10. — all the same 404 (D2)

	@Test
	void unknownRepositoryIsNotFound() throws Exception {
		mockMvc.perform(page("/nothing/", "artem")).andExpect(status().isNotFound());
	}

	@Test
	void repositoryWithoutGrantLooksExactlyLikeAnUnknownOne() throws Exception {
		final MockHttpServletResponse noGrant = response("/private/", "guest");
		final MockHttpServletResponse unknown = response("/nothing/", "guest");

		assertThat(noGrant.getStatus()).isEqualTo(404).isEqualTo(unknown.getStatus());
		assertThat(noGrant.getContentAsString()).isEqualTo(unknown.getContentAsString());
		assertThat(noGrant.getHeaderNames()).isEqualTo(unknown.getHeaderNames());
	}

	@Test
	void unreadableRepositoryFileIsNotFoundForEveryone() throws Exception {
		mockMvc.perform(page("/broken/", "artem")).andExpect(status().isNotFound());
		mockMvc.perform(page("/broken/", "guest")).andExpect(status().isNotFound());
	}

	@Test
	void invalidNamesAreNotFound() throws Exception {
		mockMvc.perform(page("/.git/", "artem")).andExpect(status().isNotFound());
		mockMvc.perform(page("/users-test/", "artem")).andExpect(status().isNotFound());
	}

	// 4.

	@Test
	void anonymousBrowserIsSentToLogin() throws Exception {
		mockMvc.perform(get("/notes/").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void anonymousClientGets401() throws Exception {
		mockMvc.perform(get("/notes/").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized());
	}

	// 5. (D5)

	@Test
	void withoutTrailingSlashRedirects() throws Exception {
		mockMvc.perform(page("/notes", "artem"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/notes/"));
	}

	@Test
	void withoutTrailingSlashRedirectsAlsoForUnknownNamesSoNothingIsRevealed() throws Exception {
		mockMvc.perform(page("/nothing", "artem")).andExpect(redirectedUrl("/nothing/"));
	}

	@Test
	void invalidNameWithoutTrailingSlashIsNotFoundWithoutRedirect() throws Exception {
		mockMvc.perform(page("/.hidden", "artem")).andExpect(status().isNotFound());
	}

	// 6.

	@Test
	void applicationPagesStillWork() throws Exception {
		mockMvc.perform(page("/", "artem")).andExpect(status().isOk());
		mockMvc.perform(get("/login")).andExpect(status().isOk());
		mockMvc.perform(get("/default-ui.css")).andExpect(status().isOk());
	}

	// 7., 8. — readable file with problems (D8)

	@Test
	void problemDetailsForUserWithWholeRepository() throws Exception {
		mockMvc.perform(page("/moved/", "artem"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("moved.yaml")))
				.andExpect(content().string(containsString("missing-root-folder")))
				.andExpect(content().string(containsString("does not exist")));
	}

	@Test
	void noProblemDetailsForUserWithPartialAccess() throws Exception {
		mockMvc.perform(page("/moved/", "guest"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(containsString("not available")))
				.andExpect(content().string(not(containsString("moved.yaml"))))
				.andExpect(content().string(not(containsString("missing-root-folder"))))
				.andExpect(content().string(not(containsString(Path.of(REPOSITORIES).toAbsolutePath().toString()))));
	}

	// JSON (since step 21)

	@Test
	void repositoryAsJson() throws Exception {
		mockMvc.perform(page("/notes/", "artem").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repository").value("notes"))
				.andExpect(jsonPath("$.path").value("/"));
	}

	@Test
	void problemDetailsAsJsonForUserWithWholeRepository() throws Exception {
		mockMvc.perform(page("/moved/", "artem").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.repository").value("moved"))
				.andExpect(jsonPath("$.file").value("moved.yaml"))
				.andExpect(jsonPath("$.problems[0]").value(containsString("missing-root-folder")));
	}

	@Test
	void noProblemDetailsAsJsonForUserWithPartialAccess() throws Exception {
		mockMvc.perform(page("/moved/", "guest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.repository").value("moved"))
				.andExpect(jsonPath("$.file").doesNotExist())
				.andExpect(jsonPath("$.problems").doesNotExist());
	}

	@Test
	void problemPageAlsoForFoldersBelowTheRoot() throws Exception {
		mockMvc.perform(page("/moved/public/", "guest")).andExpect(status().isInternalServerError());
	}
}
