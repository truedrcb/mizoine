package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Step 28: {@code GET /{repository}/{path}} of a file gives its content, safely for the browser.
 * artem may access everything, guest only {@code public}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FileDownloadTest {

	private static final Path BASE = createTree();
	private static final String NOTE = "# Note\n\nÜber uns: ü\r\n";
	private static final String RUSSIAN = "мой дом - ";
	/** "ü" decomposed, as macOS writes names. */
	private static final String NFD_NAME = "Rücksendeportal.pdf";

	private static Path createTree() {
		try {
			final Path base = Files.createTempDirectory("mizoine-files");
			final Path tree = Files.createDirectories(base.resolve("tree"));
			Files.createDirectories(tree.resolve("public/sub"));
			Files.createDirectories(tree.resolve("private"));
			Files.createDirectories(tree.resolve(".git"));
			Files.writeString(tree.resolve("public/note.md"), NOTE);
			Files.writeString(tree.resolve("public/russian.md"), RUSSIAN);
			Files.writeString(tree.resolve("public/plain.txt"), RUSSIAN);
			Files.write(tree.resolve("public/image.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
			Files.writeString(tree.resolve("public/page.html"), "<script>alert(1)</script>");
			Files.writeString(tree.resolve("public/drawing.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
			Files.writeString(tree.resolve("public/data.unknownext"), "?");
			Files.writeString(tree.resolve("public/a <b>.md"), "special");
			Files.writeString(tree.resolve("public/" + NFD_NAME), "pdf");
			Files.writeString(tree.resolve("public/.secret"), "hidden");
			Files.writeString(tree.resolve("readme.md"), "above guest's grant");
			Files.writeString(tree.resolve("private/plan.md"), "outside guest's grant");
			Files.writeString(tree.resolve(".git/config"), "git");
			final Path outside = Files.writeString(base.resolve("outside.txt"), "outside the root");
			Files.createSymbolicLink(tree.resolve("public/leak.txt"), outside);
			Files.writeString(base.resolve("tree.yaml"), """
					path: tree
					access:
					  artem: ["/"]
					  guest: [public]
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

	FileDownloadTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/** Raw URL, not re-encoded by MockMvc. */
	private static MockHttpServletRequestBuilder file(final String url, final String user) {
		return get(URI.create(url)).with(loggedInAs(user));
	}

	// 1. content

	@ParameterizedTest
	@ValueSource(strings = {"*/*", "text/html,application/xhtml+xml,*/*;q=0.8", "image/avif,image/webp,*/*",
			"text/markdown", "application/json"})
	void contentIsTheExactBytesWhateverTheBrowserAccepts(final String accept) throws Exception {
		mockMvc.perform(file("/tree/public/note.md", GUEST).header(HttpHeaders.ACCEPT, accept))
				.andExpect(status().isOk())
				.andExpect(content().bytes(NOTE.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void withoutAcceptHeaderTooAndTheTypeComesFromTheName() throws Exception {
		mockMvc.perform(file("/tree/public/image.png", ARTEM))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}));
	}

	/** Without a charset browsers read text as Windows-1252: "мой дом" became "Ð¼Ð¾Ð¹ Ð´Ð¾Ð¼". */
	@ParameterizedTest
	@ValueSource(strings = {"russian.md:text/markdown", "plain.txt:text/plain"})
	void textIsDeclaredAsUtf8(final String nameAndType) throws Exception {
		final String[] parts = nameAndType.split(":");
		mockMvc.perform(file("/tree/public/" + parts[0], ARTEM))
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, parts[1] + ";charset=UTF-8"))
				.andExpect(content().bytes(RUSSIAN.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void binaryTypesGetNoCharset() throws Exception {
		mockMvc.perform(file("/tree/public/image.png", ARTEM))
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
	}

	@Test
	void unknownTypeIsOctetStream() throws Exception {
		mockMvc.perform(file("/tree/public/data.unknownext", ARTEM))
				.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
	}

	// 2. safe in the browser (D10)

	@Test
	void filesNeverRunScriptsInTheApplicationsOrigin() throws Exception {
		mockMvc.perform(file("/tree/public/note.md", ARTEM))
				.andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"page.html", "drawing.svg", "data.unknownext"})
	void typesABrowserWouldExecuteAreDownloadedNotShown(final String name) throws Exception {
		mockMvc.perform(file("/tree/public/" + name, ARTEM))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"image.png", "note.md", NFD_NAME})
	void safeTypesAreShownInline(final String name) throws Exception {
		mockMvc.perform(file("/tree/public/" + encode(name), ARTEM))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")));
	}

	// 3. caching, ranges, HEAD

	@Test
	void unchangedFileIsNotSentAgain() throws Exception {
		final MvcResult first = mockMvc.perform(file("/tree/public/note.md", ARTEM)).andReturn();
		final String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
		assertThat(etag).startsWith("\"");
		assertThat(first.getResponse().getHeader(HttpHeaders.LAST_MODIFIED)).isNotBlank();

		mockMvc.perform(file("/tree/public/note.md", ARTEM).header(HttpHeaders.IF_NONE_MATCH, etag))
				.andExpect(status().isNotModified());
	}

	@Test
	void rangeGivesPartialContent() throws Exception {
		mockMvc.perform(file("/tree/public/note.md", ARTEM).header(HttpHeaders.RANGE, "bytes=0-5"))
				.andExpect(status().isPartialContent())
				.andExpect(content().string("# Note"));
	}

	@Test
	void headHasTheHeaders() throws Exception {
		// the empty body is Tomcat's part, not visible in MockMvc
		mockMvc.perform(head(URI.create("/tree/public/note.md")).with(loggedInAs(ARTEM)))
				.andExpect(status().isOk())
				.andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, NOTE.getBytes(StandardCharsets.UTF_8).length))
				.andExpect(header().exists(HttpHeaders.ETAG));
	}

	@ParameterizedTest
	@ValueSource(strings = {"note.md:text/markdown", "data.yaml:application/yaml", "x.yml:application/yaml",
			"list.csv:text/csv", "a.txt:text/plain", "photo.JPG:image/jpeg", "doc.pdf:application/pdf",
			"no-extension:application/octet-stream"})
	void typeFromTheFileName(final String nameAndType) {
		final String[] parts = nameAndType.split(":");
		assertThat(FileDownload.type(parts[0])).isEqualTo(MediaType.parseMediaType(parts[1]));
	}

	// 4. the same 404 for everything the user may not read (D2, D3, D4)

	@ParameterizedTest
	@ValueSource(strings = {"/tree/public/.secret", "/tree/.git/config", "/tree/readme.md", "/tree/private/plan.md",
			"/tree/public/missing.md", "/tree/public/leak.txt", "/tree/public/note.md/", "/tree/public/sub/x"})
	void notReadableForGuestIsNotFound(final String url) throws Exception {
		final int status = mockMvc.perform(file(url, GUEST)).andReturn().getResponse().getStatus();

		assertThat(status).as(url).isIn(400, 404);
	}

	@Test
	void wholeRepositoryUserReadsAboveAndOutsideGuestsGrant() throws Exception {
		mockMvc.perform(file("/tree/readme.md", ARTEM)).andExpect(status().isOk());
		mockMvc.perform(file("/tree/private/plan.md", ARTEM)).andExpect(status().isOk());
	}

	@Test
	void hiddenFilesStayHiddenEvenForWholeRepositoryUser() throws Exception {
		mockMvc.perform(file("/tree/public/.secret", ARTEM))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 404));
	}

	@Test
	void withoutLoginNothingIsServed() throws Exception {
		mockMvc.perform(get("/tree/public/note.md").accept(MediaType.TEXT_HTML))
				.andExpect(redirectedUrl("/login"));
	}

	// 5. special names

	@Test
	void specialNamesDownloadByTheirEncodedUrl() throws Exception {
		mockMvc.perform(file("/tree/public/a%20%3Cb%3E.md", ARTEM)).andExpect(content().string("special"));
		mockMvc.perform(file("/tree/public/" + encode(NFD_NAME), ARTEM)).andExpect(content().string("pdf"));
	}

	// folders keep their behaviour

	@Test
	void folderWithoutTrailingSlashStillRedirects() throws Exception {
		mockMvc.perform(file("/tree/public", ARTEM)).andExpect(redirectedUrl("/tree/public/"));
		mockMvc.perform(file("/tree/public", ARTEM).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, "/tree/public/"));
	}

	@Test
	void folderAsOtherTypeIsNotAcceptable() throws Exception {
		mockMvc.perform(file("/tree/public/", ARTEM).accept(MediaType.IMAGE_PNG)).andExpect(status().isNotAcceptable());
	}

	@Test
	void listingLinksFiles() throws Exception {
		mockMvc.perform(file("/tree/public/", ARTEM))
				.andExpect(content().string(containsString("<a href=\"note.md\">note.md</a>")))
				.andExpect(content().string(containsString("<a href=\"a%20%3Cb%3E.md\">a &lt;b&gt;.md</a>")));
	}

	private static String encode(final String name) {
		return org.springframework.web.util.UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);
	}
}
