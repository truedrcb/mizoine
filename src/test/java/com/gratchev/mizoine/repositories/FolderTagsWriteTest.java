package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * Step 63: changing a folder's tags with {@code PUT ?tags}, and what deleting and moving do with them (D70). artem
 * may change everything; guest only inside {@code projects/shared}. Each test uses folders of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FolderTagsWriteTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("notes/replace")
			.folder("notes/statuses")
			.folder("notes/remove")
			.folder("notes/match")
			.folder("notes/bad")
			.folder("notes/moving/inner")
			.folder("notes/moved")
			.folder("notes/odd/.tags")
			.file("notes/outside.txt", "not to be written")
			.link("notes/linked/.tags", "notes/outside.txt")
			.file("notes/only-tags/.tags", "old")
			.file("notes/tags-and-file/.tags", "old")
			.file("notes/tags-and-file/note.md", "x")
			.file("notes/projects/shared/plan.md", "# Plan")
			.file("notes/edit/.tags", "project\n")
			.file("notes/projects/.tags", "above-guests-grant")
			.folder("notes/movable")
			.folder("notes/no-tags-folder")
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

	FolderTagsWriteTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private ResultActions setTags(final String user, final String folder, final String json) throws Exception {
		return mockMvc.perform(put(folder + "?tags").with(loggedInAs(user)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions tags(final String user, final String folder) throws Exception {
		return mockMvc.perform(get(folder + "?tags").with(loggedInAs(user)));
	}

	private static Path file(final String folder) {
		return REPOSITORIES.path("notes/" + folder + "/.tags");
	}

	@Test
	void replaceTheTags() throws Exception {
		setTags(ARTEM, "/notes/replace/", "[\"project\", \"#status:open\"]")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tags", contains("project", "status:open")))
				.andExpect(header().exists("ETag"));

		tags(ARTEM, "/notes/replace/").andExpect(jsonPath("$.tags", contains("project", "status:open")));
		assertThat(Files.readString(file("replace"))).isEqualTo("project\nstatus:open\n");
	}

	@Test
	void aStatusReplacesTheOldOne() throws Exception {
		setTags(ARTEM, "/notes/statuses/", "[\"status:open\", \"status:done\"]")
				.andExpect(jsonPath("$.tags", contains("status:done")));
	}

	@Test
	void anEmptyListRemovesTheTagsFile() throws Exception {
		setTags(ARTEM, "/notes/remove/", "[\"project\"]").andExpect(status().isOk());

		setTags(ARTEM, "/notes/remove/", "[]")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tags", empty()))
				.andExpect(header().doesNotExist("ETag"));
		assertThat(file("remove")).doesNotExist();
		// nothing to remove: the same answer
		setTags(ARTEM, "/notes/remove/", "[]").andExpect(status().isOk());
	}

	@Test
	void ifMatchProtectsAgainstAnotherChange() throws Exception {
		// no tags file yet: no version can match
		mockMvc.perform(put("/notes/match/?tags").with(loggedInAs(ARTEM)).header("If-Match", "\"1-2\"")
				.contentType(MediaType.APPLICATION_JSON).content("[\"a\"]")).andExpect(status().isPreconditionFailed());
		final String etag = setTags(ARTEM, "/notes/match/", "[\"a\"]").andReturn().getResponse().getHeader("ETag");
		Files.setLastModifiedTime(file("match"), java.nio.file.attribute.FileTime.fromMillis(1));

		// two edits from the same version: the second finds it changed
		mockMvc.perform(put("/notes/match/?tags").with(loggedInAs(ARTEM)).header("If-Match", etag)
				.contentType(MediaType.APPLICATION_JSON).content("[\"b\"]")).andExpect(status().isPreconditionFailed());
		final String current = tags(ARTEM, "/notes/match/").andReturn().getResponse().getHeader("ETag");
		mockMvc.perform(put("/notes/match/?tags").with(loggedInAs(ARTEM)).header("If-Match", current)
				.contentType(MediaType.APPLICATION_JSON).content("[\"b\"]")).andExpect(status().isOk());
		mockMvc.perform(put("/notes/match/?tags").with(loggedInAs(ARTEM)).header("If-Match", current)
				.contentType(MediaType.APPLICATION_JSON).content("[\"c\"]")).andExpect(status().isPreconditionFailed());
		tags(ARTEM, "/notes/match/").andExpect(jsonPath("$.tags", contains("b")));
	}

	@Test
	void wrongTagsAreSaid() throws Exception {
		setTags(ARTEM, "/notes/bad/", "[\"two words\"]")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Not a tag: \"two words\" (letters, digits, -, _, / and one : only)."));
		setTags(ARTEM, "/notes/bad/", "{\"tags\": [\"a\"]}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The body must be a JSON list of tags, e.g. [\"project\", \"status:open\"]."));
		setTags(ARTEM, "/notes/projects/shared/plan.md", "[\"a\"]").andExpect(status().isBadRequest());
		assertThat(file("bad")).doesNotExist();
	}

	@Test
	void onlyWhereTheUserMayWrite() throws Exception {
		// above guest's grant, the root, and outside it: as for a file write there
		setTags(GUEST, "/notes/projects/", "[\"a\"]").andExpect(status().isNotFound());
		setTags(GUEST, "/notes/", "[\"a\"]").andExpect(status().isNotFound());
		setTags(GUEST, "/notes/replace/", "[\"a\"]").andExpect(status().isNotFound());
		setTags(GUEST, "/notes/projects/shared/", "[\"shared\"]").andExpect(status().isOk());
		setTags(ARTEM, "/notes/missing/", "[\"a\"]").andExpect(status().isNotFound());
	}

	@Test
	void aTagsFileThatIsAFolderOrALinkIsNotWritten() throws Exception {
		setTags(ARTEM, "/notes/odd/", "[\"a\"]").andExpect(status().isNotFound());
		assertThat(REPOSITORIES.path("notes/odd/.tags")).isDirectory();
		// a link put there by hand: never written through
		setTags(ARTEM, "/notes/linked/", "[\"a\"]").andExpect(status().isNotFound());
		assertThat(Files.readString(REPOSITORIES.path("notes/outside.txt"))).isEqualTo("not to be written");
	}

	@Test
	void aFolderWithOnlyItsTagsIsEmpty() throws Exception {
		mockMvc.perform(delete("/notes/only-tags/").with(loggedInAs(ARTEM))).andExpect(status().is2xxSuccessful());
		assertThat(REPOSITORIES.path("notes/only-tags")).doesNotExist();

		mockMvc.perform(delete("/notes/tags-and-file/").with(loggedInAs(ARTEM))).andExpect(status().isConflict());
		assertThat(file("tags-and-file")).exists();
	}

	/** Step 70, D81: {@code .tags} is a text file like any other: listed, read, edited, deleted. */
	@Test
	void theTagsFileIsEditedLikeAnyTextFile() throws Exception {
		mockMvc.perform(get("/notes/edit/").with(loggedInAs(ARTEM)).accept(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.files", contains(".tags")))
				.andExpect(jsonPath("$.editable", contains(".tags")));
		final String etag = mockMvc.perform(get("/notes/edit/.tags").with(loggedInAs(ARTEM)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
				.andExpect(content().string("project\n"))
				.andReturn().getResponse().getHeader("ETag");

		// edited by hand in the editor: stored as written; a word that is no tag is left out when read (D65)
		mockMvc.perform(put("/notes/edit/.tags").with(loggedInAs(ARTEM)).header("If-Match", etag)
				.content("project status:open two%words\n")).andExpect(status().isNoContent());
		assertThat(Files.readString(file("edit"))).isEqualTo("project status:open two%words\n");
		tags(ARTEM, "/notes/edit/").andExpect(jsonPath("$.tags", contains("project", "status:open")));
		// an old version is refused, as for any file
		mockMvc.perform(put("/notes/edit/.tags").with(loggedInAs(ARTEM)).header("If-Match", etag).content("x"))
				.andExpect(status().isPreconditionFailed());

		mockMvc.perform(delete("/notes/edit/.tags").with(loggedInAs(ARTEM))).andExpect(status().isNoContent());
		tags(ARTEM, "/notes/edit/").andExpect(jsonPath("$.tags", empty()));
	}

	@Test
	void theTagsFileIsReadAndWrittenOnlyWhereAnyFileIs() throws Exception {
		// above guest's grant no files are shown (D4), so no tags file either
		mockMvc.perform(get("/notes/projects/").with(loggedInAs(GUEST)).accept(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.files", empty()));
		mockMvc.perform(get("/notes/projects/.tags").with(loggedInAs(GUEST))).andExpect(status().isNotFound());
		mockMvc.perform(put("/notes/projects/.tags").with(loggedInAs(GUEST)).content("mine"))
				.andExpect(status().isNotFound());
		assertThat(Files.readString(REPOSITORIES.path("notes/projects/.tags"))).isEqualTo("above-guests-grant");
		// other names starting with "." stay hidden (D3)
		mockMvc.perform(put("/notes/edit/.Tags").with(loggedInAs(ARTEM)).content("x")).andExpect(status().isNotFound());
	}

	@Test
	void aFolderMayNotBeNamedLikeTheTagsFile() throws Exception {
		mockMvc.perform(put("/notes/no-tags-folder/.tags/").with(loggedInAs(ARTEM))).andExpect(status().isNotFound());
		mockMvc.perform(post("/notes/movable/").with(loggedInAs(ARTEM)).header("Destination", "/notes/no-tags-folder/.tags/"))
				.andExpect(status().isNotFound());
		assertThat(REPOSITORIES.path("notes/no-tags-folder/.tags")).doesNotExist();
		assertThat(REPOSITORIES.path("notes/movable")).isDirectory();
	}

	@Test
	void aMovedFolderKeepsItsTags() throws Exception {
		setTags(ARTEM, "/notes/moving/inner/", "[\"travels\"]").andExpect(status().isOk());

		mockMvc.perform(post("/notes/moving/inner/").with(loggedInAs(ARTEM)).header("Destination", "/notes/moved/inner/"))
				.andExpect(status().is2xxSuccessful());

		tags(ARTEM, "/notes/moved/inner/").andExpect(jsonPath("$.tags", contains("travels")));
	}
}
