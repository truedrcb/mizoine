package com.gratchev.mizoine.search;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Step 65: searching folders by their tags, and the tags in use, over HTTP. In {@code notes}, artem may read
 * everything, guest only {@code public}; {@code other} has no index.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TagsSearchPageTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("notes/public/garden/.tags", "project status:open")
			.file("notes/public/kitchen/.tags", "project status:done")
			.file("notes/public/kitchen/plan.md", "Garden kitchen plan.")
			.file("notes/private/secret/.tags", "secret")
			.folder("notes/public/later")
			.folder("notes/private/written")
			.folder("other/x")
			.repository("notes", "path: notes\naccess:\n  artem: [\"/\"]\n  guest: [public]\n")
			.repository("other", "path: other\naccess:\n  artem: [\"/\"]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
		registry.add("mizoine.search.index-dir", () -> REPOSITORIES.path("search-index").toString());
	}

	@AfterAll
	static void deleteRepositories() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;
	private final SearchIndexes indexes;

	TagsSearchPageTest(@Autowired final MockMvc mockMvc, @Autowired final SearchIndexes indexes) {
		this.mockMvc = mockMvc;
		this.indexes = indexes;
	}

	@BeforeEach
	void reindex() {
		indexes.index("notes").reindex(REPOSITORIES.path("notes"));
	}

	private ResultActions json(final String user, final String url) throws Exception {
		return mockMvc.perform(get(url).with(loggedInAs(user)).accept(MediaType.APPLICATION_JSON));
	}

	private ResultActions search(final String user, final String query, final String... tags) throws Exception {
		return mockMvc.perform(get("/notes/.search").param("q", query).param("tag", tags).with(loggedInAs(user))
				.accept(MediaType.APPLICATION_JSON));
	}

	@Test
	void byTagInTheQueryAndAsAFilter() throws Exception {
		search(GUEST, "#status:open")
				.andExpect(jsonPath("$.hits[*].path", contains("public/garden/")))
				.andExpect(jsonPath("$.hits[0].tags", contains("project", "status:open")));
		// tags are the folders' own: the filter finds folders, not the files in them
		search(GUEST, "kitchen", "status:done").andExpect(jsonPath("$.hits[*].path", contains("public/kitchen/")));
		search(GUEST, "kitchen", "project", "status:done").andExpect(jsonPath("$.hits[*].path", contains("public/kitchen/")));
		search(GUEST, "kitchen", "status:open").andExpect(jsonPath("$.hits", org.hamcrest.Matchers.empty()));
		search(GUEST, "garden", "two words")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Not a tag: \"two words\" (letters, digits, -, _, / and one : only)."));
	}

	@Test
	void aTagSetThroughMizoineIsFoundAtOnce() throws Exception {
		mockMvc.perform(put("/notes/public/later/?tags").with(loggedInAs(GUEST)).contentType(MediaType.APPLICATION_JSON)
				.content("[\"status:waiting\"]")).andExpect(status().isOk());

		search(GUEST, "#status:waiting").andExpect(jsonPath("$.hits[*].path", contains("public/later/")));
		json(GUEST, "/notes/.tags/").andExpect(jsonPath("$.tags[?(@.tag == 'status:waiting')].count", contains(1)));
	}

	/** Step 70: the tags file written as a file is found at once, and is no search result of its own. */
	@Test
	void aTagsFileWrittenAsAFileIsFoundAtOnce() throws Exception {
		mockMvc.perform(put("/notes/private/written/.tags").with(loggedInAs(ARTEM)).content("status:later"))
				.andExpect(status().is2xxSuccessful());

		search(ARTEM, "#status:later").andExpect(jsonPath("$.hits[*].path", contains("private/written/")));
		search(ARTEM, "tags").andExpect(jsonPath("$.hits[*].path", org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.endsWith(".tags")))));

		// deleted as a file: the folder has no tags at once (and the other tests see the tags in use as before)
		mockMvc.perform(delete("/notes/private/written/.tags").with(loggedInAs(ARTEM))).andExpect(status().isNoContent());
		search(ARTEM, "#status:later").andExpect(jsonPath("$.hits", org.hamcrest.Matchers.empty()));
	}

	@Test
	void theTagsInUse() throws Exception {
		json(GUEST, "/notes/.tags/")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tags[*].tag", contains("project", "status:done", "status:open")))
				.andExpect(jsonPath("$.tags[0].count").value(2))
				.andExpect(jsonPath("$.index.status").value("indexed"));
		json(ARTEM, "/notes/.tags/").andExpect(jsonPath("$.tags[*].tag", contains("project", "secret", "status:done",
				"status:open")));
	}

	@Test
	void theTagsPageGroupsStatusesAndLinksToTheSearch() throws Exception {
		final String page = mockMvc.perform(get("/notes/.tags/").with(loggedInAs(GUEST)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(page).contains("<h2>status</h2>", "href=\"/notes/.search?q=%23project\"",
				"href=\"/notes/.search?q=%23status:open\"");
		assertThat(page).doesNotContain("secret");
	}

	@Test
	void withoutAnIndexOrAGrant() throws Exception {
		json(ARTEM, "/other/.tags/").andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.indexes[0].status").value("not-indexed"));
		json(GUEST, "/other/.tags/").andExpect(status().isNotFound());
		// step 70 (D81): the tags file is a file of its own address, not the tags in use
		mockMvc.perform(get("/notes/public/garden/.tags").with(loggedInAs(ARTEM))).andExpect(status().isOk());
	}
}
