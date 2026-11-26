package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gratchev.mizoine.TestRepositories;

/**
 * Step 30: {@code PUT /{repository}/{path}} creates or replaces a file. artem may write everywhere, guest only
 * in {@code public}. Each test uses its own file names: the tree is shared.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FileWriteTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("tree/public/folder")
			.folder("tree/private")
			.folder("tree/.git")
			.file("tree/public/existing.md", "old")
			.file("outside.txt", "outside")
			.link("tree/public/leak.txt", "outside.txt")
			.repository("tree", "path: tree\n" + "access:\n  artem: [\"/\"]\n  guest: [public]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
		registry.add("mizoine.max-file-size", () -> "1KB");
	}

	@AfterAll
	static void deleteTree() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	FileWriteTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/** Raw URL, not re-encoded by MockMvc. HTTP Basic: no CSRF token needed (D11). */
	private static MockHttpServletRequestBuilder write(final String url, final String user, final String content) {
		return put(URI.create(url)).with(loggedInAs(user)).content(content.getBytes(StandardCharsets.UTF_8));
	}

	private String etag(final String url) throws Exception {
		return mockMvc.perform(get(URI.create(url)).with(loggedInAs(ARTEM))).andReturn().getResponse()
				.getHeader(HttpHeaders.ETAG);
	}

	// 1. create, replace

	@Test
	void newFileIsCreated() throws Exception {
		mockMvc.perform(write("/tree/public/new.md", GUEST, "# Neu: ü\r\n"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, "/tree/public/new.md"))
				.andExpect(header().exists(HttpHeaders.ETAG));

		assertThat(Files.readString(REPOSITORIES.path("tree/public/new.md"))).isEqualTo("# Neu: ü\r\n");
	}

	/**
	 * Bug found live 2026-11-23: {@code curl --data-binary} sends this content type by default, and Spring Boot's
	 * FormContentFilter read such PUT bodies as form fields, so the file was saved empty.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"application/x-www-form-urlencoded", "text/plain;charset=UTF-8", "application/octet-stream"})
	void contentIsStoredWhateverTheContentType(final String contentType) throws Exception {
		final String name = "typed-" + Math.abs(contentType.hashCode()) + ".md";
		mockMvc.perform(write("/tree/public/" + name, ARTEM, "a=b&c=d\r\n# Plan").contentType(contentType))
				.andExpect(status().isCreated());

		assertThat(Files.readString(REPOSITORIES.path("tree/public/" + name))).isEqualTo("a=b&c=d\r\n# Plan");
	}

	/** Step 67: any file can be uploaded, not only text: every byte value round-trips exactly. */
	@Test
	void binaryContentIsStoredAndReadExactly() throws Exception {
		final byte[] bytes = new byte[512];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) i;
		}
		mockMvc.perform(put(URI.create("/tree/public/all-bytes.bin")).with(loggedInAs(ARTEM)).content(bytes))
				.andExpect(status().isCreated());

		assertThat(Files.readAllBytes(REPOSITORIES.path("tree/public/all-bytes.bin"))).isEqualTo(bytes);
		mockMvc.perform(get(URI.create("/tree/public/all-bytes.bin")).with(loggedInAs(ARTEM)))
				.andExpect(content().bytes(bytes));
	}

	/** Step 67: a picture is stored as sent and served by its name's type, whatever the request said it was. */
	@ParameterizedTest
	@ValueSource(strings = {"image/png", "multipart/form-data; boundary=x", ""})
	void aPictureIsServedByItsNamesType(final String contentType) throws Exception {
		final byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0x0d, 'I', 'H', 'D', 'R'};
		final String url = "/tree/public/picture-" + Math.abs(contentType.hashCode()) + ".png";
		final MockHttpServletRequestBuilder upload = put(URI.create(url)).with(loggedInAs(ARTEM)).content(png);
		mockMvc.perform(contentType.isEmpty() ? upload : upload.contentType(contentType)).andExpect(status().isCreated());

		mockMvc.perform(get(URI.create(url)).with(loggedInAs(ARTEM)))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(content().bytes(png));
	}

	@Test
	void theEtagOfTheAnswerIsTheEtagOfTheFile() throws Exception {
		final String written = mockMvc.perform(write("/tree/public/tagged.md", ARTEM, "x"))
				.andReturn().getResponse().getHeader(HttpHeaders.ETAG);

		assertThat(written).isEqualTo(etag("/tree/public/tagged.md"));
	}

	@Test
	void existingFileIsReplaced() throws Exception {
		Files.writeString(REPOSITORIES.path("tree/public/replace.md"), "old");

		mockMvc.perform(write("/tree/public/replace.md", ARTEM, "new"))
				.andExpect(status().isNoContent())
				.andExpect(header().exists(HttpHeaders.ETAG));
		mockMvc.perform(get("/tree/public/replace.md").with(loggedInAs(ARTEM))).andExpect(content().string("new"));
	}

	@Test
	void specialNamesAreWrittenByTheirEncodedUrl() throws Exception {
		mockMvc.perform(write("/tree/public/a%20%3Cb%3E%20%C3%BC.md", ARTEM, "x"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, "/tree/public/a%20%3Cb%3E%20%C3%BC.md"));

		assertThat(REPOSITORIES.path("tree/public/a <b> ü.md")).exists();
	}

	// 2. preconditions (D13)

	@Test
	void ifMatchProtectsAgainstLostUpdates() throws Exception {
		Files.writeString(REPOSITORIES.path("tree/public/shared.md"), "v1");
		final String etag = etag("/tree/public/shared.md");

		mockMvc.perform(write("/tree/public/shared.md", ARTEM, "v2").header(HttpHeaders.IF_MATCH, etag))
				.andExpect(status().isNoContent());
		mockMvc.perform(write("/tree/public/shared.md", ARTEM, "lost").header(HttpHeaders.IF_MATCH, etag))
				.andExpect(status().isPreconditionFailed());

		assertThat(Files.readString(REPOSITORIES.path("tree/public/shared.md"))).isEqualTo("v2");
	}

	@Test
	void ifNoneMatchStarCreatesOnly() throws Exception {
		mockMvc.perform(write("/tree/public/once.md", ARTEM, "first").header(HttpHeaders.IF_NONE_MATCH, "*"))
				.andExpect(status().isCreated());
		mockMvc.perform(write("/tree/public/once.md", ARTEM, "second").header(HttpHeaders.IF_NONE_MATCH, "*"))
				.andExpect(status().isPreconditionFailed());

		assertThat(Files.readString(REPOSITORIES.path("tree/public/once.md"))).isEqualTo("first");
	}

	// 3. size (D12)

	@Test
	void tooLargeIsRejectedAndNothingIsWritten() throws Exception {
		mockMvc.perform(write("/tree/public/big.md", ARTEM, "x".repeat(1025)))
				.andExpect(status().isContentTooLarge());

		assertThat(REPOSITORIES.path("tree/public/big.md")).doesNotExist();
	}

	// 4. conflicts (D14)

	@Test
	void missingParentFolderIsAConflict() throws Exception {
		mockMvc.perform(write("/tree/public/nothing/new.md", ARTEM, "x")).andExpect(status().isConflict());

		assertThat(REPOSITORIES.path("tree/public/nothing")).doesNotExist();
	}

	@Test
	void folderAtThePathIsAConflict() throws Exception {
		mockMvc.perform(write("/tree/public/folder", ARTEM, "x")).andExpect(status().isConflict());
	}

	// 5. not writable: the same 404 as unknown (D2, D3, D4)

	@ParameterizedTest
	@ValueSource(strings = {"/tree/readme.md", "/tree/private/x.md", "/tree/public/.hidden", "/tree/.git/config",
			"/tree/public/leak.txt", "/nothing/x.md"})
	void notWritableForGuestIsNotFound(final String url) throws Exception {
		final int status = mockMvc.perform(write(url, GUEST, "x")).andReturn().getResponse().getStatus();

		assertThat(status).as(url).isIn(400, 404);
	}

	@Test
	void hiddenAndOutsideStayUnwritableForWholeRepositoryUser() throws Exception {
		for (final String url : new String[] {"/tree/.git/config", "/tree/public/leak.txt", "/tree/public/.hidden"}) {
			final int status = mockMvc.perform(write(url, ARTEM, "x")).andReturn().getResponse().getStatus();
			assertThat(status).as(url).isIn(400, 404);
		}
		assertThat(Files.readString(REPOSITORIES.path("outside.txt"))).isEqualTo("outside");
		assertThat(REPOSITORIES.path("tree/.git/config")).doesNotExist();
	}

	@Test
	void withoutLoginNothingIsWritten() throws Exception {
		// no credentials at all: a browser-like request without CSRF token → 403 before authentication
		mockMvc.perform(put("/tree/public/anonymous.md").content("x")).andExpect(status().isForbidden());
		// an API client with a wrong password → 401
		mockMvc.perform(put("/tree/public/anonymous.md").content("x").with(httpBasic(ARTEM, "wrong")))
				.andExpect(status().isUnauthorized());

		assertThat(REPOSITORIES.path("tree/public/anonymous.md")).doesNotExist();
	}

	// step 31: create a folder (PUT …/, D14)

	private static MockHttpServletRequestBuilder createFolder(final String url, final String user) {
		return put(URI.create(url)).with(loggedInAs(user));
	}

	@Test
	void folderIsCreatedExplicitly() throws Exception {
		mockMvc.perform(createFolder("/tree/public/made/", GUEST))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, "/tree/public/made/"));
		mockMvc.perform(createFolder("/tree/public/made/", GUEST)).andExpect(status().isNoContent());
		mockMvc.perform(createFolder("/tree/public/made/", GUEST).header(HttpHeaders.IF_NONE_MATCH, "*"))
				.andExpect(status().isPreconditionFailed());

		assertThat(REPOSITORIES.path("tree/public/made")).isDirectory();
	}

	@Test
	void folderConflicts() throws Exception {
		mockMvc.perform(createFolder("/tree/public/none/deeper/", ARTEM)).andExpect(status().isConflict());
		mockMvc.perform(createFolder("/tree/public/existing.md/", ARTEM)).andExpect(status().isConflict());
		mockMvc.perform(createFolder("/tree/", ARTEM)).andExpect(status().isConflict());
	}

	@Test
	void folderHasNoContent() throws Exception {
		mockMvc.perform(createFolder("/tree/public/with-body/", ARTEM).content("x"))
				.andExpect(status().isUnsupportedMediaType());

		assertThat(REPOSITORIES.path("tree/public/with-body")).doesNotExist();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/tree/new/", "/tree/private/new/", "/tree/public/.hidden/", "/tree/.git/new/"})
	void folderNotWritableForGuestIsNotFound(final String url) throws Exception {
		final int status = mockMvc.perform(createFolder(url, GUEST)).andReturn().getResponse().getStatus();

		assertThat(status).as(url).isIn(400, 404);
	}

	// step 32: delete (D15)

	private static MockHttpServletRequestBuilder remove(final String url, final String user) {
		return delete(URI.create(url)).with(loggedInAs(user));
	}

	@Test
	void fileIsDeleted() throws Exception {
		Files.writeString(REPOSITORIES.path("tree/public/gone.md"), "x");

		mockMvc.perform(remove("/tree/public/gone.md", GUEST)).andExpect(status().isNoContent());
		mockMvc.perform(remove("/tree/public/gone.md", GUEST)).andExpect(status().isNotFound());
	}

	@Test
	void fileIsDeletedOnlyIfUnchanged() throws Exception {
		Files.writeString(REPOSITORIES.path("tree/public/guarded.md"), "x");

		mockMvc.perform(remove("/tree/public/guarded.md", ARTEM).header(HttpHeaders.IF_MATCH, "\"old\""))
				.andExpect(status().isPreconditionFailed());
		mockMvc.perform(remove("/tree/public/guarded.md", ARTEM).header(HttpHeaders.IF_MATCH, etag("/tree/public/guarded.md")))
				.andExpect(status().isNoContent());
	}

	@Test
	void onlyEmptyFoldersAreDeleted() throws Exception {
		Files.createDirectories(REPOSITORIES.path("tree/public/empty"));
		Files.createDirectories(REPOSITORIES.path("tree/public/full"));
		Files.writeString(REPOSITORIES.path("tree/public/full/.gitkeep"), "");

		mockMvc.perform(remove("/tree/public/empty/", GUEST)).andExpect(status().isNoContent());
		mockMvc.perform(remove("/tree/public/full/", GUEST)).andExpect(status().isConflict());

		assertThat(REPOSITORIES.path("tree/public/full/.gitkeep")).exists();
	}

	@Test
	void rootAndTheTopOfAGrantAreNotDeleted() throws Exception {
		mockMvc.perform(remove("/tree/", ARTEM)).andExpect(status().isConflict());
		mockMvc.perform(remove("/tree/public/", GUEST)).andExpect(status().isConflict());

		assertThat(REPOSITORIES.path("tree/public")).isDirectory();
	}

	@Test
	void kindMustMatchTheUrl() throws Exception {
		mockMvc.perform(remove("/tree/public/existing.md/", ARTEM)).andExpect(status().isNotFound());
		mockMvc.perform(remove("/tree/public/folder", ARTEM)).andExpect(status().isNotFound());

		assertThat(REPOSITORIES.path("tree/public/existing.md")).exists();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/tree/readme.md", "/tree/private/", "/tree/public/.hidden", "/tree/.git/",
			"/tree/public/leak.txt", "/nothing/x.md"})
	void notDeletableForGuestIsNotFound(final String url) throws Exception {
		final int status = mockMvc.perform(remove(url, GUEST)).andReturn().getResponse().getStatus();

		assertThat(status).as(url).isIn(400, 404);
	}
}
