package com.gratchev.mizoine.search;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gratchev.mizoine.TestRepositories;

/**
 * Step 58: the search page and its JSON, as a browser and an API client use them. In {@code notes}, artem may read
 * everything and guest only {@code public}; {@code other} has no index yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SearchPageTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("notes/public/plan.md", "# Garden plan\n\nApples and pears, <b>not bold</b>.")
			.file("notes/private/apples.md", "Apples for artem only.")
			.file("other/apples.md", "Other apples.")
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

	SearchPageTest(@Autowired final MockMvc mockMvc, @Autowired final SearchIndexes indexes) {
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

	private ResultActions page(final String user, final String url) throws Exception {
		return mockMvc.perform(get(url).with(loggedInAs(user)));
	}

	@Test
	void resultsAsJson() throws Exception {
		json(GUEST, "/notes/.search?q=apples")
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.strictness").value("all-words"))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.hits[*].path", contains("public/plan.md")))
				.andExpect(jsonPath("$.hits[0].url").value("/notes/public/plan.md"))
				.andExpect(jsonPath("$.hits[0].title").value("Garden plan"))
				.andExpect(jsonPath("$.hits[0].snippets[0].parts[*].text", hasItem("Apples")))
				.andExpect(jsonPath("$.indexes[0].status").value("indexed"))
				.andExpect(jsonPath("$.indexes[0]", hasKey("reason")))
				.andExpect(jsonPath("$.indexes[0].reason").value(nullValue()))
				.andExpect(jsonPath("$.indexes[0].searchable").value(true));
	}

	@Test
	void resultsAsAPage() throws Exception {
		page(GUEST, "/notes/.search?q=apples")
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("<a href=\"/notes/public/plan.md\">Garden plan</a>")))
				.andExpect(content().string(containsString("<mark>Apples</mark>")))
				// the file's text is text, never markup
				.andExpect(content().string(containsString("&lt;b&gt;not bold&lt;/b&gt;")))
				.andExpect(content().string(containsString("value=\"apples\"")))
				.andExpect(content().string(not(containsString("artem only"))));
	}

	@Test
	void aPageWithoutAQueryIsTheSearchBox() throws Exception {
		page(GUEST, "/notes/.search")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"q\"")));
	}

	@Test
	void allRepositoriesSayWhichHaveNoIndex() throws Exception {
		json(ARTEM, "/.search?q=apples")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hits[*].path", containsInAnyOrder("public/plan.md", "private/apples.md")))
				.andExpect(jsonPath("$.indexes[?(@.repository == 'other')].status", contains("not-indexed")));
		page(ARTEM, "/.search?q=apples")
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("other: not-indexed")));
	}

	@Test
	void withoutAnIndexSearchCannotAnswer() throws Exception {
		json(ARTEM, "/other/.search?q=apples")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.indexes[0].status").value("not-indexed"));
		page(ARTEM, "/other/.search?q=apples")
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().string(containsString("other: not-indexed")));
	}

	@Test
	void aQueryThatAsksNothingIs400() throws Exception {
		json(GUEST, "/notes/.search?q=")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Write what to search for."));
		json(GUEST, "/notes/.search").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Write what to search for."));
		json(GUEST, "/notes/.search?q=" + "x".repeat(201)).andExpect(status().isBadRequest());
		json(GUEST, "/notes/.search?q=apples&limit=101").andExpect(status().isBadRequest());
		page(GUEST, "/notes/.search?q=-pears")
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("Write what to search for, not only what to leave out.")));
	}

	@Test
	void aRepositoryWithoutAGrantIsNotFound() throws Exception {
		json(GUEST, "/other/.search?q=apples").andExpect(status().isNotFound());
		page(GUEST, "/other/.search?q=apples").andExpect(status().isNotFound());
	}

	@Test
	void mizoinesOwnWritesAreFoundAtOnce() throws Exception {
		mockMvc.perform(put("/notes/public/new.md").with(loggedInAs(GUEST)).content("cherries"))
				.andExpect(status().is2xxSuccessful());
		json(GUEST, "/notes/.search?q=cherries").andExpect(jsonPath("$.hits[*].path", contains("public/new.md")));

		mockMvc.perform(put("/notes/public/later/").with(loggedInAs(GUEST))).andExpect(status().is2xxSuccessful());
		mockMvc.perform(post("/notes/public/new.md").with(loggedInAs(GUEST)).header("Destination", "/notes/public/later/new.md"))
				.andExpect(status().is2xxSuccessful());
		json(GUEST, "/notes/.search?q=cherries").andExpect(jsonPath("$.hits[*].path", contains("public/later/new.md")));

		mockMvc.perform(delete("/notes/public/later/new.md").with(loggedInAs(GUEST))).andExpect(status().is2xxSuccessful());
		json(GUEST, "/notes/.search?q=cherries").andExpect(jsonPath("$.hits", empty()));
	}

	/** The startup only looks at each index (D61), and says which are not ready. */
	@Test
	void theStartupSaysWhichIndexesAreNotReady(final CapturedOutput output) {
		assertThat(output.getAll()).contains("Search index of other is not-indexed: reindex it (POST /other/.search/reindex)");
	}

	@Test
	void searchBoxesOnTheStartAndFolderPages() throws Exception {
		page(GUEST, "/").andExpect(content().string(containsString("action=\"/.search\"")));
		page(GUEST, "/notes/public/").andExpect(content().string(containsString("action=\"/notes/.search\"")));
	}
}
