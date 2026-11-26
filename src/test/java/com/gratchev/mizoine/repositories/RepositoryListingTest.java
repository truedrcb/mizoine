package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gratchev.mizoine.TestRepositories;

/**
 * Step 21: folders list their subfolders, as HTML (default) or JSON. The repository "tree" is created
 * in a temporary folder (names with '#', '%', '<' or non-ASCII letters do not belong in Git):
 * artem may access everything, guest only {@code public} and {@code projects/shared}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RepositoryListingTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("tree/public/sub")
			.folder("tree/projects/shared/deep")
			.folder("tree/projects/other")
			.folder("tree/private")
			.folder("tree/with space")
			.folder("tree/hash#")
			.folder("tree/50%")
			.folder("tree/Ünïcode")
			.folder("tree/a <b>")
			.folder("tree/.hidden/inside")
			.file("tree/file.txt", "not a folder")
			.file("tree/.secret", "hidden file")
			.file("tree/projects/plan.md", "above the guest's grant")
			.file("tree/projects/shared/notes.md", "inside the guest's grant")
			.file("tree/projects/shared/a <b>.md", "special name")
			// "ü" decomposed (NFD, as macOS writes names): u + combining diaeresis
			.file("tree/public/Ru\u0308cksendeportal.pdf", "non-ASCII name")
			.repository("tree", "path: tree\n" + "access:\n  artem: [\"/\"]\n  guest: [public, projects/shared]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void deleteTree() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	RepositoryListingTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/** Raw URL, not re-encoded by MockMvc. */
	private static MockHttpServletRequestBuilder folder(final String url, final String user) {
		return get(URI.create(url)).with(loggedInAs(user));
	}

	private static MockHttpServletRequestBuilder json(final String url, final String user) {
		return folder(url, user).accept(MediaType.APPLICATION_JSON);
	}

	// 1. JSON

	@Test
	void rootAsJsonListsSubfoldersAndFilesSortedWithoutHiddenOnes() throws Exception {
		mockMvc.perform(json("/tree/", "artem"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.repository").value("tree"))
				.andExpect(jsonPath("$.path").value("/"))
				// "50%" is hidden: an encoded '%' is rejected by Spring Security's firewall
				.andExpect(jsonPath("$.folders", contains(
						"a <b>", "hash#", "private", "projects", "public", "with space", "Ünïcode")))
				.andExpect(jsonPath("$.files", contains("file.txt")));
	}

	// 3. deeper

	@Test
	void deeperFolderAsJson() throws Exception {
		mockMvc.perform(json("/tree/projects/shared/", "artem"))
				.andExpect(jsonPath("$.path").value("/projects/shared/"))
				.andExpect(jsonPath("$.folders", contains("deep")));
	}

	@Test
	void emptyFolderHasEmptyLists() throws Exception {
		mockMvc.perform(json("/tree/projects/shared/deep/", "artem"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.folders", empty()))
				.andExpect(jsonPath("$.files", empty()));
		mockMvc.perform(folder("/tree/projects/shared/deep/", "artem"))
				.andExpect(content().string(containsString("Empty folder.")));
	}

	// 2. HTML

	@Test
	void htmlLinksEverySubfolder() throws Exception {
		mockMvc.perform(folder("/tree/projects/", "artem"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("<a href=\"other/\">other</a>")))
				.andExpect(content().string(containsString("<a href=\"shared/\">shared</a>")))
				.andExpect(content().string(containsString("<a href=\"../\">")));
	}

	@Test
	void htmlLinksFilesToo() throws Exception {
		// files can be opened since step 28
		mockMvc.perform(folder("/tree/projects/shared/", "artem"))
				.andExpect(content().string(containsString("<a href=\"notes.md\">notes.md</a>")))
				.andExpect(content().string(containsString("<a href=\"a%20%3Cb%3E.md\">a &lt;b&gt;.md</a>")))
				.andExpect(content().string(not(containsString("Empty folder."))));
	}

	/** Step 71: an Edit link for each editable file where the user may write (D74), to the editor (D28). */
	@Test
	void htmlHasAnEditLinkForTextFilesOnly() throws Exception {
		mockMvc.perform(folder("/tree/projects/shared/", "guest"))
				.andExpect(content().string(containsString(
						"<a class=\"edit\" href=\"/ui/editor.html?file=/tree/projects/shared/notes.md\">Edit</a>")))
				// the file's URL is encoded once as a path, then as the parameter (as the document page's Edit)
				.andExpect(content().string(containsString(
						"href=\"/ui/editor.html?file=/tree/projects/shared/a%2520%253Cb%253E.md\"")));
		// not a text format: no Edit
		mockMvc.perform(folder("/tree/public/", "guest"))
				.andExpect(content().string(not(containsString("class=\"edit\""))));
	}

	/** Step 71 (D76): a new file only by an explicit action, where the user may write; done by the folder's script. */
	@Test
	void htmlHasNewFileWhereTheUserMayWrite() throws Exception {
		mockMvc.perform(folder("/tree/projects/shared/", "guest"))
				.andExpect(content().string(containsString("<form id=\"new-file\" hidden data-folder=\"/tree/projects/shared/\">")))
				.andExpect(content().string(containsString("<script type=\"module\" src=\"/ui/folder/page.js\"></script>")));
		// above guest's grants (D4): nothing to write
		mockMvc.perform(folder("/tree/projects/", "guest"))
				.andExpect(content().string(not(containsString("id=\"new-file\""))))
				.andExpect(content().string(not(containsString("id=\"upload\""))))
				.andExpect(content().string(not(containsString("<script"))));
	}

	/** Steps 72–73: upload into the folder, rename and delete its files, where the user may write. */
	@Test
	void htmlHasUploadAndTheFilesForRenameAndDelete() throws Exception {
		mockMvc.perform(folder("/tree/projects/shared/", "guest"))
				.andExpect(content().string(containsString("<form id=\"upload\" hidden>")))
				.andExpect(content().string(containsString("<input type=\"file\" name=\"files\" multiple>")))
				.andExpect(content().string(containsString("<li data-file=\"notes.md\">")))
				.andExpect(content().string(containsString("<li data-file=\"a &lt;b&gt;.md\">")));
	}

	/** The folder page's scripts are public static files, like the editor's. */
	@Test
	void theFolderScriptsLoadWithoutLogin() throws Exception {
		mockMvc.perform(get("/ui/folder/page.js")).andExpect(status().isOk());
		mockMvc.perform(get("/ui/folder/folder.js")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/javascript"));
	}

	@Test
	void nonAsciiFileNamesStayAsTheyAreOnDisk() throws Exception {
		mockMvc.perform(json("/tree/public/", "artem"))
				.andExpect(jsonPath("$.files", contains("Ru\u0308cksendeportal.pdf")));
		mockMvc.perform(folder("/tree/public/", "artem"))
				.andExpect(content().string(containsString(">Ru\u0308cksendeportal.pdf</a>")));
	}

	@Test
	void rootHasNoParentLinkButALinkToTheStartPage() throws Exception {
		mockMvc.perform(folder("/tree/", "artem"))
				.andExpect(content().string(not(containsString("<a href=\"../\">"))))
				.andExpect(content().string(containsString("<a href=\"/\">")));
	}

	// 4. only what the user may see (D4), never hidden folders (D3)

	/** Step 68: which files open in the editor (by name, D73), and whether the user may write in the folder. */
	@Test
	void theListingSaysWhatIsEditableAndWhereTheUserMayWrite() throws Exception {
		mockMvc.perform(json("/tree/public/", "guest"))
				.andExpect(jsonPath("$.files", contains("Rücksendeportal.pdf")))
				.andExpect(jsonPath("$.editable", empty()))
				.andExpect(jsonPath("$.writable").value(true));
		mockMvc.perform(json("/tree/projects/shared/", "guest"))
				.andExpect(jsonPath("$.editable", contains("a <b>.md", "notes.md")))
				.andExpect(jsonPath("$.writable").value(true));
		// above guest's grants: no files, read-only (D4)
		mockMvc.perform(json("/tree/projects/", "guest"))
				.andExpect(jsonPath("$.editable", empty()))
				.andExpect(jsonPath("$.writable").value(false));
		mockMvc.perform(json("/tree/", "guest")).andExpect(jsonPath("$.writable").value(false));
		mockMvc.perform(json("/tree/", "artem"))
				.andExpect(jsonPath("$.editable", contains("file.txt")))
				.andExpect(jsonPath("$.writable").value(true));
	}

	@Test
	void partialUserSeesOnlyTheWayToTheGrantedFolders() throws Exception {
		mockMvc.perform(json("/tree/", "guest"))
				.andExpect(jsonPath("$.folders", contains("projects", "public")))
				.andExpect(jsonPath("$.files", empty()));
		mockMvc.perform(json("/tree/projects/", "guest"))
				.andExpect(jsonPath("$.folders", contains("shared")))
				.andExpect(jsonPath("$.files", empty()));
		mockMvc.perform(json("/tree/projects/shared/", "guest"))
				.andExpect(jsonPath("$.folders", contains("deep")))
				.andExpect(jsonPath("$.files", contains("a <b>.md", "notes.md")));
	}

	@Test
	void foldersOutsideTheGrantsAreNotFound() throws Exception {
		mockMvc.perform(folder("/tree/private/", "guest")).andExpect(status().isNotFound());
		mockMvc.perform(folder("/tree/projects/other/", "guest")).andExpect(status().isNotFound());
	}

	@Test
	void hiddenFolderIsNotFoundEvenForWholeRepositoryUser() throws Exception {
		mockMvc.perform(folder("/tree/.hidden/", "artem"))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 404));
	}

	@Test
	void notExistingFolderAndFileAreNotFound() throws Exception {
		mockMvc.perform(folder("/tree/nothing/", "artem")).andExpect(status().isNotFound());
		mockMvc.perform(folder("/tree/file.txt/", "artem")).andExpect(status().isNotFound());
	}

	// 5. content negotiation (D6)

	@Test
	void anyAcceptGivesHtmlAndOtherFormatsAreNotAcceptable() throws Exception {
		mockMvc.perform(folder("/tree/", "artem").accept(MediaType.ALL))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
		mockMvc.perform(folder("/tree/", "artem").accept(MediaType.APPLICATION_XML))
				.andExpect(status().isNotAcceptable());
	}

	// 6. special names

	@Test
	void specialNamesAreEncodedInLinksAndEscapedInText() throws Exception {
		mockMvc.perform(folder("/tree/", "artem"))
				.andExpect(content().string(containsString("<a href=\"with%20space/\">with space</a>")))
				.andExpect(content().string(containsString("<a href=\"hash%23/\">hash#</a>")))
				.andExpect(content().string(not(containsString("50%"))))
				.andExpect(content().string(containsString("<a href=\"%C3%9Cn%C3%AFcode/\">Ünïcode</a>")))
				.andExpect(content().string(containsString("<a href=\"a%20%3Cb%3E/\">a &lt;b&gt;</a>")))
				.andExpect(content().string(not(containsString("<b>"))));
	}

	@Test
	void encodedFolderUrlsWork() throws Exception {
		for (final String url : new String[] {"/tree/with%20space/", "/tree/hash%23/",
				"/tree/%C3%9Cn%C3%AFcode/", "/tree/a%20%3Cb%3E/"}) {
			final int status = mockMvc.perform(json(url, "artem")).andReturn().getResponse().getStatus();
			assertThat(status).as(url).isEqualTo(200);
		}
		mockMvc.perform(json("/tree/hash%23/", "artem")).andExpect(jsonPath("$.path").value("/hash#/"));
	}

	@Test
	void folderWithPercentInTheNameIsNotReachable() throws Exception {
		final int status = mockMvc.perform(json("/tree/50%25/", "artem")).andReturn().getResponse().getStatus();

		assertThat(status).isIn(400, 404);
	}

	// 7. trailing slash (D5)

	@Test
	void folderWithoutTrailingSlashRedirects() throws Exception {
		mockMvc.perform(folder("/tree/projects", "artem"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/tree/projects/"));
		mockMvc.perform(folder("/tree/with%20space", "artem"))
				.andExpect(redirectedUrl("/tree/with%20space/"));
	}

	@Test
	void noRedirectForFoldersTheUserCannotSee() throws Exception {
		// a redirect would tell that the folder exists
		mockMvc.perform(folder("/tree/private", "guest")).andExpect(status().isNotFound());
		mockMvc.perform(folder("/tree/nothing", "artem")).andExpect(status().isNotFound());
	}
}
