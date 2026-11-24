package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gratchev.mizoine.TestRepositories;

/**
 * Step 41: {@code POST /{repository}/{path}} with a {@code Destination} header moves or renames a file or folder
 * (D29: WebDAV MOVE semantics on a standard method). guest may write in {@code public} and {@code archive}.
 * Each test uses its own names: the tree is shared.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FileMoveTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("tree/public/note.md", "note")
			.file("tree/public/stale.md", "stale")
			.file("tree/public/folder/inside.md", "inside")
			.file("tree/public/taken.md", "taken")
			.file("tree/archive/taken.md", "taken")
			.file("tree/private/secret.md", "secret")
			.repository("tree", "path: tree\naccess:\n  artem: [\"/\"]\n  guest: [public, archive]\n")
			.repository("other", "path: tree\naccess:\n  guest: [public]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void delete() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	FileMoveTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/** Raw URLs, not re-encoded by MockMvc. HTTP Basic: an API client, as agents and curl are. */
	private static MockHttpServletRequestBuilder move(final String from, final String destination) {
		return post(URI.create(from)).with(loggedInAs(GUEST)).header("Destination", destination);
	}

	// moved

	@Test
	void fileIsMovedToAnotherGrantedFolder() throws Exception {
		mockMvc.perform(move("/tree/public/note.md", "/tree/archive/note.md"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, "/tree/archive/note.md"));

		mockMvc.perform(get("/tree/archive/note.md").with(loggedInAs(GUEST))).andExpect(content().string("note"));
		mockMvc.perform(get("/tree/public/note.md").with(loggedInAs(GUEST))).andExpect(status().isNotFound());
	}

	@Test
	void folderIsMovedWithItsContent() throws Exception {
		mockMvc.perform(move("/tree/public/folder/", "/tree/archive/folder/"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, "/tree/archive/folder/"));

		assertThat(REPOSITORIES.path("tree/archive/folder/inside.md")).exists();
	}

	@Test
	void renameWithSpecialCharacters() throws Exception {
		mockMvc.perform(post(URI.create("/tree/archive/taken.md")).with(loggedInAs(ARTEM))
						.header("Destination", "/tree/archive/a%20%C3%BC.md"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, "/tree/archive/a%20%C3%BC.md"));

		assertThat(REPOSITORIES.path("tree/archive/a ü.md")).exists();
	}

	// refused (D2, D14, D30)

	@Test
	void anExistingDestinationIsNeverOverwritten() throws Exception {
		Files.writeString(REPOSITORIES.path("tree/archive/occupied.md"), "occupied");

		mockMvc.perform(move("/tree/public/taken.md", "/tree/archive/occupied.md")).andExpect(status().isPreconditionFailed());
		assertThat(Files.readString(REPOSITORIES.path("tree/archive/occupied.md"))).isEqualTo("occupied");
	}

	@Test
	void anOldVersionIsNotMoved() throws Exception {
		mockMvc.perform(move("/tree/public/stale.md", "/tree/archive/stale.md").header(HttpHeaders.IF_MATCH, "\"outdated\""))
				.andExpect(status().isPreconditionFailed());
	}

	@Test
	void conflicts() throws Exception {
		mockMvc.perform(move("/tree/public/taken.md", "/tree/archive/none/x.md")).andExpect(status().isConflict());
		mockMvc.perform(move("/tree/public/", "/tree/archive/public/")).andExpect(status().isConflict());
		mockMvc.perform(move("/tree/archive/", "/tree/archive/sub/archive/")).andExpect(status().isConflict());
		Files.createDirectories(REPOSITORIES.path("tree/public/nest/inner"));
		mockMvc.perform(move("/tree/public/nest/", "/tree/public/nest/inner/nest/")).andExpect(status().isConflict());
		assertThat(REPOSITORIES.path("tree/public/nest/inner")).isDirectory();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/tree/private/secret.md>/tree/public/secret.md", "/tree/public/taken.md>/tree/private/x.md",
			"/tree/public/missing.md>/tree/archive/missing.md", "/tree/public/taken.md>/tree/public/.hidden.md"})
	void notVisibleIsNotFound(final String fromAndTo) throws Exception {
		final String[] parts = fromAndTo.split(">");

		mockMvc.perform(move(parts[0], parts[1])).andExpect(status().isNotFound());
		assertThat(REPOSITORIES.path("tree/private/secret.md")).exists();
	}

	// the Destination header (D29, D31)

	@ParameterizedTest
	@ValueSource(strings = {"/other/public/x.md", "https://evil.example/tree/archive/x.md", "//evil.example/tree/x.md",
			"tree/archive/x.md", "/tree/archive/x/"})
	void badDestinationIsABadRequest(final String destination) throws Exception {
		mockMvc.perform(move("/tree/public/taken.md", destination)).andExpect(status().isBadRequest());
		assertThat(REPOSITORIES.path("tree/public/taken.md")).exists();
	}

	@Test
	void postWithoutDestinationIsCsrfProtectedEvenWithHttpBasic() throws Exception {
		// browsers also send a cached Basic login with a cross-site form post (D11)
		mockMvc.perform(post("/tree/public/taken.md").with(loggedInAs(GUEST))).andExpect(status().isForbidden());
	}

	// CSRF (D11): a cross-site form cannot send a Destination header

	@Test
	void browserSessionMovesWithTheDestinationHeader() throws Exception {
		final MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin().user(ARTEM).password(password(ARTEM)))
				.andReturn().getRequest().getSession();
		Files.writeString(REPOSITORIES.path("tree/public/by-browser.md"), "b");

		mockMvc.perform(post("/tree/public/by-browser.md").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(post("/tree/public/by-browser.md").session(session).header("Destination", "/tree/archive/by-browser.md"))
				.andExpect(status().isCreated());
	}
}
