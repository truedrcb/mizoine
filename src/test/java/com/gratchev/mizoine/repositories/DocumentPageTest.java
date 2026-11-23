package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gratchev.mizoine.security.SecurityConfig;

/** Step 38: {@code /repo/folder/document.md} in the browser is a readable page; everyone else gets the file. */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentPageTest {

	private static final String BROWSER = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
	private static final String PLAN = """
			# Plan *2026*

			See [costs](#costs-and-risks), [the budget](budget.md#total) and [the folder](sub/).

			![Diagram](images/diagram.png)

			## Costs and Risks

			<script>alert(1)</script>
			""";
	private static final Path BASE = createTree();

	private static Path createTree() {
		try {
			final Path base = Files.createTempDirectory("mizoine-page");
			final Path notes = Files.createDirectories(base.resolve("tree/notes"));
			Files.createDirectories(notes.resolve("images"));
			Files.writeString(notes.resolve("plan.md"), PLAN);
			Files.writeString(notes.resolve("a b.md"), "# Space");
			Files.write(notes.resolve("images/diagram.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
			Files.write(notes.resolve("latin1.md"), "Grüße".getBytes(StandardCharsets.ISO_8859_1));
			Files.writeString(notes.resolve("big.md"), "x".repeat((int) MarkdownPage.MAX_BYTES + 1));
			Files.writeString(notes.resolve(".hidden.md"), "# Hidden");
			Files.writeString(base.resolve("tree.yaml"), "path: tree\naccess:\n  artem: [\"/\"]\n");
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

	DocumentPageTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private static MockHttpServletRequestBuilder browser(final String url) {
		return get(URI.create(url)).with(loggedInAs(ARTEM)).header(HttpHeaders.ACCEPT, BROWSER);
	}

	// 1. which version (D22)

	@Test
	void browserGetsTheRenderedPage() throws Exception {
		mockMvc.perform(browser("/tree/notes/plan.md"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("<h2 id=\"costs-and-risks\">Costs and Risks</h2>")))
				.andExpect(header().string(HttpHeaders.VARY, containsString("Accept")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"text/markdown", "*/*"})
	void othersGetTheRawFile(final String accept) throws Exception {
		mockMvc.perform(get("/tree/notes/plan.md").with(loggedInAs(ARTEM)).header(HttpHeaders.ACCEPT, accept))
				.andExpect(content().contentType("text/markdown;charset=UTF-8"))
				.andExpect(content().string(PLAN))
				.andExpect(header().string(HttpHeaders.VARY, containsString("Accept")));
	}

	@Test
	void withoutAcceptTheRawFile() throws Exception {
		mockMvc.perform(get("/tree/notes/plan.md").with(loggedInAs(ARTEM))).andExpect(content().string(PLAN));
	}

	@Test
	void rawParameterGivesTheFileToTheBrowser() throws Exception {
		mockMvc.perform(browser("/tree/notes/plan.md?raw")).andExpect(content().string(PLAN));
	}

	@Test
	void agentWithAnOauthTokenGetsTheRawFile() throws Exception {
		// the token is not checked yet (step 34): the user is logged in another way, the header alone decides
		mockMvc.perform(get("/tree/notes/plan.md").with(user(ARTEM))
						.header(HttpHeaders.ACCEPT, BROWSER).header(HttpHeaders.AUTHORIZATION, "Bearer agent-token"))
				.andExpect(content().string(PLAN));
	}

	// 2. relative references, resolved by the browser against the folder

	@Test
	void referencesStayRelativeAndThePictureDownloadsThere() throws Exception {
		mockMvc.perform(browser("/tree/notes/plan.md"))
				.andExpect(content().string(containsString("<img src=\"images/diagram.png\" alt=\"Diagram\" />")))
				.andExpect(content().string(containsString("href=\"budget.md#total\"")))
				.andExpect(content().string(containsString("href=\"sub/\"")))
				.andExpect(content().string(containsString("href=\"#costs-and-risks\"")));
		mockMvc.perform(get("/tree/notes/images/diagram.png").with(loggedInAs(ARTEM))
						.header(HttpHeaders.ACCEPT, "image/avif,image/webp,*/*"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	// 3. safety (D24); the page keeps the application's CSP (no sandbox: pictures need the session)

	@Test
	void htmlInTheDocumentIsShownAsText() throws Exception {
		mockMvc.perform(browser("/tree/notes/plan.md"))
				.andExpect(content().string(not(containsString("<script>alert"))))
				.andExpect(content().string(containsString("&lt;script&gt;")))
				.andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY));
	}

	// 4. the page around it

	@Test
	void titleIsTheFirstHeading() throws Exception {
		mockMvc.perform(browser("/tree/notes/plan.md"))
				.andExpect(content().string(containsString("<title>Plan 2026</title>")));
	}

	@Test
	void linksToTheFolderTheRawFileAndTheEditor() throws Exception {
		mockMvc.perform(browser("/tree/notes/plan.md"))
				.andExpect(content().string(containsString("<a href=\"./\">")))
				.andExpect(content().string(containsString("<a href=\"plan.md?raw\">")))
				.andExpect(content().string(containsString("<a href=\"/ui/editor.html?file=/tree/notes/plan.md\">")));
	}

	@Test
	void specialNamesAreEncodedInTheLinks() throws Exception {
		// D28: the editor gets the file's URL path, itself encoded once more as a query parameter
		mockMvc.perform(browser("/tree/notes/a%20b.md"))
				.andExpect(content().string(containsString("<a href=\"a%20b.md?raw\">")))
				.andExpect(content().string(containsString("<a href=\"/ui/editor.html?file=/tree/notes/a%2520b.md\">")));
	}

	// 5. not rendered: too large, not UTF-8 (D21); not shown at all: hidden

	@ParameterizedTest
	@ValueSource(strings = {"/tree/notes/latin1.md", "/tree/notes/big.md"})
	void notRenderableIsReturnedRaw(final String url) throws Exception {
		mockMvc.perform(browser(url))
				.andExpect(status().isOk())
				.andExpect(content().contentType("text/markdown;charset=UTF-8"));
	}

	@Test
	void hiddenDocumentIsNotFound() throws Exception {
		final int status = mockMvc.perform(browser("/tree/notes/.hidden.md")).andReturn().getResponse().getStatus();

		assertThat(status).isIn(400, 404);
	}
}
