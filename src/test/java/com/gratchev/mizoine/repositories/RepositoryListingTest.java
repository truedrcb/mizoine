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
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

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

/**
 * Step 21: folders list their subfolders, as HTML (default) or JSON. The repository "tree" is created
 * in a temporary folder (names with '#', '%', '<' or non-ASCII letters do not belong in Git):
 * artem may access everything, guest only {@code public} and {@code projects/shared}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RepositoryListingTest {

	private static final Path BASE = createTree();

	private static Path createTree() {
		try {
			final Path base = Files.createTempDirectory("mizoine-listing");
			final Path tree = base.resolve("tree");
			for (final String folder : new String[] {"public/sub", "projects/shared/deep", "projects/other", "private",
					"with space", "hash#", "50%", "Ünïcode", "a <b>", ".hidden/inside"}) {
				Files.createDirectories(tree.resolve(folder));
			}
			Files.writeString(tree.resolve("file.txt"), "not a folder");
			Files.writeString(tree.resolve(".secret"), "hidden file");
			Files.writeString(tree.resolve("projects/plan.md"), "above the guest's grant");
			Files.writeString(tree.resolve("projects/shared/notes.md"), "inside the guest's grant");
			Files.writeString(tree.resolve("projects/shared/a <b>.md"), "special name");
			// "ü" decomposed (NFD, as macOS writes names): u + combining diaeresis
			Files.writeString(tree.resolve("public/Ru\u0308cksendeportal.pdf"), "non-ASCII name");
			Files.writeString(base.resolve("tree.yaml"), """
					path: tree
					access:
					  artem: ["/"]
					  guest: [public, projects/shared]
					""");
			return base;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		registry.add("mizoine.repositories-dir", BASE::toString);
	}

	@AfterAll
	static void deleteTree() throws IOException {
		try (Stream<Path> paths = Files.walk(BASE)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
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
	void htmlShowsFilesAsTextWithoutLinks() throws Exception {
		// files cannot be opened yet: no links that would only lead to 404
		mockMvc.perform(folder("/tree/projects/shared/", "artem"))
				.andExpect(content().string(containsString("<li>notes.md</li>")))
				.andExpect(content().string(containsString("<li>a &lt;b&gt;.md</li>")))
				.andExpect(content().string(not(containsString("href=\"notes.md"))))
				.andExpect(content().string(not(containsString("Empty folder."))));
	}

	@Test
	void nonAsciiFileNamesStayAsTheyAreOnDisk() throws Exception {
		mockMvc.perform(json("/tree/public/", "artem"))
				.andExpect(jsonPath("$.files", contains("Ru\u0308cksendeportal.pdf")));
		mockMvc.perform(folder("/tree/public/", "artem"))
				.andExpect(content().string(containsString("<li>Ru\u0308cksendeportal.pdf</li>")));
	}

	@Test
	void rootHasNoParentLinkButALinkToTheStartPage() throws Exception {
		mockMvc.perform(folder("/tree/", "artem"))
				.andExpect(content().string(not(containsString("<a href=\"../\">"))))
				.andExpect(content().string(containsString("<a href=\"/\">")));
	}

	// 4. only what the user may see (D4), never hidden folders (D3)

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
