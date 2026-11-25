package com.gratchev.mizoine.git;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.gratchev.mizoine.TestRepositories;

/**
 * Step 45: the Git page shows the status, as HTML (D35) and as JSON (D6). In {@code real}, artem has {@code /} and
 * reads every file; guest has only {@code .git}: the same status, but no links to files.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GitStatusPageTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("real")
			.folder("clean")
			.file("plain/README.md", "no Git here")
			.repository("real", "path: real\naccess:\n  artem: [\"/\"]\n  guest: [.git]\n")
			.repository("clean", "path: clean\naccess:\n  artem: [\"/\"]\n")
			.repository("plain", "path: plain\naccess:\n  artem: [\"/\"]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	/** In {@code real}: staged a change and a rename; not staged a change, a deletion and a new file. */
	@BeforeAll
	static void changes() throws Exception {
		final TestGit git = new TestGit(REPOSITORIES.path("home"));
		final Path real = git.repository(REPOSITORIES.path("real"));
		Files.createDirectories(real.resolve("notes"));
		Files.writeString(real.resolve("notes/a b.md"), "a");
		Files.writeString(real.resolve("old.md"), "old");
		Files.writeString(real.resolve("gone.md"), "gone");
		git.git(real, "add", ".");
		git.git(real, "commit", "-q", "-m", "files");
		Files.writeString(real.resolve("notes/a b.md"), "staged");
		git.git(real, "add", "notes/a b.md");
		git.git(real, "mv", "old.md", "new.md");
		Files.writeString(real.resolve("README.md"), "changed");
		Files.delete(real.resolve("gone.md"));
		Files.writeString(real.resolve("notes/new ü.md"), "new");
		git.repository(REPOSITORIES.path("clean"));
	}

	@AfterAll
	static void deleteRepositories() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	GitStatusPageTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void thePageShowsBranchStagedAndNotStaged() throws Exception {
		mockMvc.perform(get("/real/.git/").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("main")))
				.andExpect(content().string(containsString("no upstream")))
				// staged
				.andExpect(content().string(containsString("<a href=\"/real/notes/a%20b.md\">notes/a b.md</a>")))
				.andExpect(content().string(containsString("new.md")))
				.andExpect(content().string(containsString("from old.md")))
				// not staged; a deleted file has no page to link to
				.andExpect(content().string(containsString("<a href=\"/real/README.md\">README.md</a>")))
				.andExpect(content().string(containsString("<a href=\"/real/notes/new%20%C3%BC.md\">notes/new ü.md</a>")))
				.andExpect(content().string(containsString("gone.md")))
				.andExpect(content().string(not(containsString("href=\"/real/gone.md\""))))
				.andExpect(content().string(containsString("untracked")))
				.andExpect(content().string(containsString("deleted")))
				.andExpect(content().string(containsString("renamed")));
	}

	@Test
	void withGitAccessAloneTheFilesAreNotLinked() throws Exception {
		// D33: the status names every file; reading them still needs a folder grant
		mockMvc.perform(get("/real/.git/").with(loggedInAs(GUEST)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("README.md")))
				.andExpect(content().string(not(containsString("href=\"/real/"))));
	}

	@Test
	void theStatusAsJson() throws Exception {
		mockMvc.perform(get("/real/.git/").with(loggedInAs(ARTEM)).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.branch").value("main"))
				.andExpect(jsonPath("$.hasCommits").value(true))
				.andExpect(jsonPath("$.changes", hasSize(5)))
				.andExpect(jsonPath("$.changes[?(@.path == 'new.md')].renamedFrom", contains("old.md")))
				.andExpect(jsonPath("$.changes[?(@.path == 'gone.md')].unstaged", contains("DELETED")));
	}

	@Test
	void aCleanRepository() throws Exception {
		mockMvc.perform(get("/clean/.git/").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Nothing to commit")));
	}

	@Test
	void aFolderWithoutGitSaysSo() throws Exception {
		// D34: the page says it; there is no status to give as JSON
		mockMvc.perform(get("/plain/.git/").with(loggedInAs(ARTEM)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("not the top of a Git working tree")));
		mockMvc.perform(get("/plain/.git/").with(loggedInAs(ARTEM)).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict());
	}

	@Test
	void withoutGitAccessTheJsonIsNotFoundToo() throws Exception {
		mockMvc.perform(get("/clean/.git/").with(loggedInAs(GUEST)).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}
}
