package com.gratchev.mizoine.search;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.gratchev.mizoine.TestRepositories;

/** Step 56: with {@code mizoine.search.enabled=false} there is no index, search is "not found", writes work as ever. */
@SpringBootTest(properties = "mizoine.search.enabled=false")
@AutoConfigureMockMvc
class SearchDisabledTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("notes/plan.md", "apples")
			.repository("notes", "path: notes\n" + TestRepositories.ARTEM_EVERYTHING);

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
	private final ApplicationContext context;

	SearchDisabledTest(@Autowired final MockMvc mockMvc, @Autowired final ApplicationContext context) {
		this.mockMvc = mockMvc;
		this.context = context;
	}

	@Test
	void searchIsNotFoundAndNothingIsIndexed() throws Exception {
		mockMvc.perform(get("/notes/.search?q=apples").with(loggedInAs(ARTEM)).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/.search?q=apples").with(loggedInAs(ARTEM))).andExpect(status().isNotFound());
		mockMvc.perform(put("/notes/new.md").with(loggedInAs(ARTEM)).content("cherries"))
				.andExpect(status().is2xxSuccessful());

		assertThat(context.getBeansOfType(SearchIndexes.class)).isEmpty();
		assertThat(REPOSITORIES.path("search-index")).doesNotExist();
	}
}
