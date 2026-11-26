package com.gratchev.mizoine.search;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import com.gratchev.mizoine.TestBrowser;
import com.gratchev.mizoine.TestRepositories;
import com.gratchev.mizoine.git.TestGit;
import com.jayway.jsonpath.JsonPath;

/**
 * Step 60: the index state with its dates, and reindexing and updating on request. In {@code notes} (a Git
 * repository), artem may read everything and use Git; guest may read only {@code public}. Each test uses its own
 * repository folder of {@code notes}' kind, so their indexes do not meet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SearchIndexPageTest {

	private static final String ACCESS = "access:\n  artem: [\"/\"]\n  guest: [public]\n";
	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("notes/public/plan.md", "# Garden plan\n\nApples.")
			.file("notes/private/secret.md", "Private.")
			.file("states/public/plan.md", "Apples.")
			.file("dates/public/plan.md", "Apples.")
			.file("dates/private/later.md", "Later.")
			.file("forms/public/plan.md", "Apples.")
			.repository("notes", "path: notes\n" + ACCESS)
			.repository("states", "path: states\n" + ACCESS)
			.repository("dates", "path: dates\n" + ACCESS)
			.repository("forms", "path: forms\n" + ACCESS);

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
		registry.add("mizoine.search.index-dir", () -> REPOSITORIES.path("search-index").toString());
	}

	@BeforeAll
	static void git() throws Exception {
		new TestGit(REPOSITORIES.path("home")).repository(REPOSITORIES.path("notes"));
	}

	@AfterAll
	static void deleteRepositories() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	SearchIndexPageTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private ResultActions state(final String user, final String repository) throws Exception {
		return mockMvc.perform(get("/" + repository + "/.search/index").with(loggedInAs(user)).accept(MediaType.APPLICATION_JSON));
	}

	private ResultActions request(final String user, final String repository, final String what) throws Exception {
		return mockMvc.perform(post("/" + repository + "/.search/" + what).with(loggedInAs(user))
				.contentType(MediaType.APPLICATION_JSON));
	}

	/** Reindexing and updates run in the background: wait until the index is indexed and no update runs. */
	private String indexed(final String repository) throws Exception {
		for (int i = 0; i < 200; i++) {
			final String body = state(ARTEM, repository).andReturn().getResponse().getContentAsString();
			if ("indexed".equals(JsonPath.read(body, "$.status")) && !(Boolean) JsonPath.read(body, "$.updating")) {
				return body;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("not indexed: " + repository);
	}

	@Test
	void notIndexedThenReindexingThenIndexed(final CapturedOutput output) throws Exception {
		state(ARTEM, "states").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("not-indexed"));
		assertThat(output.getAll()).contains("Search index of states is not-indexed: reindex it (POST /states/.search/reindex)");
		request(ARTEM, "states", "update").andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("The index needs a reindexing first."));

		request(ARTEM, "states", "reindex").andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("reindexing"));
		final String body = indexed("states");

		// public/ and public/plan.md
		assertThat(JsonPath.<Integer>read(body, "$.files")).isEqualTo(2);
		assertThat(JsonPath.<String>read(body, "$.indexedAt")).isNotBlank();
		mockMvc.perform(get("/states/.search?q=apples").with(loggedInAs(GUEST)).accept(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.hits[*].path", contains("public/plan.md")));
	}

	@Test
	void theDatesTellWhetherChangesByHandAreInTheIndex() throws Exception {
		request(ARTEM, "dates", "reindex").andExpect(status().isAccepted());
		final String before = indexed("dates");
		final Path later = Files.writeString(REPOSITORIES.path("dates/public/new.md"), "cherries");
		Files.setLastModifiedTime(later, FileTime.from(Instant.now().plusSeconds(60)));

		final String changed = state(ARTEM, "dates").andReturn().getResponse().getContentAsString();
		assertThat(Instant.parse(JsonPath.read(changed, "$.newestFile")))
				.isAfter(Instant.parse(JsonPath.read(before, "$.updatedAt")));

		request(ARTEM, "dates", "update").andExpect(status().isAccepted());
		final String after = indexed("dates");
		assertThat(Instant.parse(JsonPath.<String>read(after, "$.updatedAt")))
				.isAfter(Instant.parse(JsonPath.read(before, "$.updatedAt")));
		assertThat(JsonPath.<String>read(after, "$.indexedAt")).isEqualTo(JsonPath.read(before, "$.indexedAt"));
		mockMvc.perform(get("/dates/.search?q=cherries").with(loggedInAs(GUEST)).accept(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.hits[*].path", contains("public/new.md")));
	}

	@Test
	void aUserWithFoldersOnlySeesTheStateButNotTheWholeRepository() throws Exception {
		request(ARTEM, "notes", "reindex").andExpect(status().isAccepted());
		indexed("notes");
		Files.setLastModifiedTime(REPOSITORIES.path("notes/private/secret.md"), FileTime.from(Instant.now().plusSeconds(3600)));

		state(GUEST, "notes").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("indexed"))
				.andExpect(jsonPath("$.files").value(nullValue()))
				.andExpect(jsonPath("$.unreadable").value(nullValue()))
				// no Git access
				.andExpect(jsonPath("$.lastCommit").value(nullValue()));
		final String guest = state(GUEST, "notes").andReturn().getResponse().getContentAsString();
		final String artem = state(ARTEM, "notes").andReturn().getResponse().getContentAsString();
		// the newest file of guest's folders, not the private one
		assertThat(Instant.parse(JsonPath.read(guest, "$.newestFile"))).isBefore(Instant.parse(JsonPath.read(artem, "$.newestFile")));
		state(ARTEM, "notes").andExpect(jsonPath("$.lastCommit").value(notNullValue()))
				.andExpect(jsonPath("$.files").value(notNullValue()));

		request(GUEST, "notes", "reindex").andExpect(status().isNotFound());
		request(GUEST, "notes", "update").andExpect(status().isNotFound());
		state(GUEST, "unknown").andExpect(status().isNotFound());
	}

	@Test
	void allRepositoriesAndThePage() throws Exception {
		mockMvc.perform(get("/.search/index").with(loggedInAs(GUEST)).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].repository", contains("dates", "forms", "notes", "states")));
		mockMvc.perform(get("/.search/index").with(loggedInAs(GUEST)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("<td>notes</td>")));
		mockMvc.perform(get("/notes/.search/index").with(loggedInAs(GUEST)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<td>notes</td>")));
	}

	@Test
	void theIndexPagesAreLinked() throws Exception {
		assertThat(TestBrowser.login(mockMvc, GUEST).page("/notes/.search")).contains("href=\"/notes/.search/index\"");
		final TestBrowser browser = TestBrowser.login(mockMvc, GUEST);
		assertThat(browser.page("/.search")).contains("href=\"/.search/index\"");
		assertThat(browser.page("/")).contains("href=\"/.search/index\"");
		assertThat(browser.page("/notes/.search/index")).contains("href=\"/notes/.search\"", "href=\"/.search/index\"");
	}

	@Test
	void reindexAndUpdateButtonsOnlyForTheWholeRepository() throws Exception {
		final String artem = TestBrowser.login(mockMvc, ARTEM).page("/notes/.search/index");
		assertThat(artem).contains("action=\"/notes/.search/reindex\"", "action=\"/notes/.search/update\"");
		final String guest = TestBrowser.login(mockMvc, GUEST).page("/notes/.search/index");
		assertThat(guest).doesNotContain(".search/reindex", ".search/update");

		state(ARTEM, "notes").andExpect(jsonPath("$.mayReindex").value(true));
		state(GUEST, "notes").andExpect(jsonPath("$.mayReindex").value(false));
	}

	@Test
	void reindexingFromThePage() throws Exception {
		final TestBrowser browser = TestBrowser.login(mockMvc, ARTEM);

		// before any index, an update is refused, and the page says why
		mockMvc.perform(browser.form("/forms/.search/update", browser.page("/forms/.search/index")))
				.andExpect(status().isSeeOther())
				.andExpect(header().string("Location", "/forms/.search/index"));
		assertThat(browser.page("/forms/.search/index")).contains("The index needs a reindexing first.");

		mockMvc.perform(browser.form("/forms/.search/reindex", browser.page("/forms/.search/index")))
				.andExpect(status().isSeeOther())
				.andExpect(header().string("Location", "/forms/.search/index"));
		assertThat(browser.page("/forms/.search/index")).contains("Reindexing started.");
		indexed("forms");

		// from the page of all repositories, back to it
		mockMvc.perform(browser.form("/forms/.search/update", browser.page("/.search/index")).param("back", "all"))
				.andExpect(status().isSeeOther())
				.andExpect(header().string("Location", "/.search/index"));
		assertThat(browser.page("/.search/index")).contains("forms: update started.");
		indexed("forms");
	}

	@Test
	void aFormWithoutTokenCannotReindex() throws Exception {
		// formLogin() of spring-security-test would replace the context's token store: a real browser instead
		mockMvc.perform(TestBrowser.login(mockMvc, ARTEM).crossSiteForm("/notes/.search/reindex"))
				.andExpect(status().isForbidden());
	}
}
