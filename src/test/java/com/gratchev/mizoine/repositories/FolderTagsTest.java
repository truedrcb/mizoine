package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gratchev.mizoine.TestRepositories;

/**
 * Step 62: a folder's tags in its listing and at {@code ?tags}. artem may read everything; guest only
 * {@code projects/shared}, so {@code projects} is only on guest's way to it (D4) and shows guest no tags.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FolderTagsTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("notes/.tags", "root-tag")
			.file("notes/projects/.tags", "project status:active")
			.file("notes/projects/shared/.tags", "shared")
			.file("notes/projects/shared/sub/.tags", "sub-tag")
			.file("notes/projects/shared/plain/note.md", "no tags here")
			.file("notes/projects/shared/plan.md", "# Plan")
			.file("notes/private/.tags", "secret")
			.repository("notes", "path: notes\naccess:\n  artem: [\"/\"]\n  guest: [projects/shared]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void deleteRepositories() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	FolderTagsTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private ResultActions json(final String user, final String url) throws Exception {
		return mockMvc.perform(get(url).with(loggedInAs(user)).accept(MediaType.APPLICATION_JSON));
	}

	@Test
	void theListingHasTheFolderAndSubfolderTags() throws Exception {
		json(GUEST, "/notes/projects/shared/")
				.andExpect(jsonPath("$.tags", contains("shared")))
				.andExpect(jsonPath("$.folderTags.sub", contains("sub-tag")))
				// a subfolder without tags is not named
				.andExpect(jsonPath("$.folderTags.plain").doesNotExist());
		json(ARTEM, "/notes/")
				.andExpect(jsonPath("$.tags", contains("root-tag")))
				.andExpect(jsonPath("$.folderTags.projects", contains("project", "status:active")))
				.andExpect(jsonPath("$.folderTags.private", contains("secret")));
	}

	@Test
	void foldersOnlyOnTheWayShowNoTags() throws Exception {
		json(GUEST, "/notes/projects/")
				.andExpect(jsonPath("$.tags", empty()))
				// shared is guest's own: its tags show
				.andExpect(jsonPath("$.folderTags.shared", contains("shared")));
		json(GUEST, "/notes/")
				.andExpect(jsonPath("$.tags", empty()))
				.andExpect(jsonPath("$.folderTags", anEmptyMap()));
	}

	@Test
	void theTagsOfOneFolder() throws Exception {
		json(GUEST, "/notes/projects/shared/?tags")
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.tags", contains("shared")))
				.andExpect(header().exists("ETag"));
		// without an Accept header too, as API clients send it
		mockMvc.perform(get("/notes/projects/shared/?tags").with(loggedInAs(GUEST)))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
		json(GUEST, "/notes/projects/shared/plain/?tags")
				.andExpect(jsonPath("$.tags", empty()))
				.andExpect(header().doesNotExist("ETag"));
		json(GUEST, "/notes/projects/?tags").andExpect(status().isOk()).andExpect(jsonPath("$.tags", empty()));
	}

	@Test
	void notVisibleFilesAndTheTagsFileItself() throws Exception {
		json(GUEST, "/notes/private/?tags").andExpect(status().isNotFound());
		json(GUEST, "/notes/missing/?tags").andExpect(status().isNotFound());
		json(GUEST, "/notes/projects/shared/plan.md?tags")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Tags are for folders: a folder's path ends with '/'."));
		json(ARTEM, "/notes/projects/shared/.tags").andExpect(status().isNotFound());
		mockMvc.perform(get("/notes/projects/shared/.tags").with(loggedInAs(ARTEM))).andExpect(status().isNotFound());
	}

	@Test
	void theFolderPageShowsTheTags() throws Exception {
		final String page = mockMvc.perform(get("/notes/projects/shared/").with(loggedInAs(GUEST)))
				.andReturn().getResponse().getContentAsString();

		assertThat(page).contains("<span class=\"tag\">shared</span>", "<span class=\"tag\">sub-tag</span>");
	}
}
