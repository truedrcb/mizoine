package com.gratchev.mizoine.uploads;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gratchev.mizoine.TestRepositories;
import com.jayway.jsonpath.JsonPath;

/**
 * Steps 76–77 (D82–D89): an upload link is made where the user may write, and then takes files without login, into
 * that folder only, as that user, never over an existing file, until it expires or its files are used up. guest may
 * write in {@code notes/inbox} and {@code drop}; the time is a test clock.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UploadLinkPageTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("notes/inbox")
			.folder("notes/private")
			.file("notes/inbox/old.jpg", "old")
			.folder("drop/box")
			.repository("notes", "path: notes\naccess:\n  artem: [\"/\"]\n  guest: [inbox]\n")
			.repository("drop", "path: drop\naccess:\n  guest: [box]\n");
	private static final Instant START = Instant.parse("2026-11-26T10:00:00Z");

	/** The links' clock, moved by the tests. */
	static final class MovableClock extends Clock {

		private Instant now = START;

		void pass(final Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(final ZoneId zone) {
			return this;
		}
	}

	@TestConfiguration
	static class Times {
		@Bean
		MovableClock uploadClock() {
			return new MovableClock();
		}
	}

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
		registry.add("mizoine.public-url", () -> "https://mizoine.example.org");
		registry.add("mizoine.max-file-size", () -> "1KB");
	}

	@AfterAll
	static void deleteRepositories() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;
	private final MovableClock clock;

	UploadLinkPageTest(@Autowired final MockMvc mockMvc, @Autowired final MovableClock clock) {
		this.mockMvc = mockMvc;
		this.clock = clock;
	}

	private ResultActions makeLink(final String user, final String folder, final String body) throws Exception {
		return mockMvc.perform(post(URI.create(folder + "?upload-link")).with(loggedInAs(user))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** @return the link's path on this server, e.g. {@code /.upload/<token>/} */
	private String link(final String user, final String folder, final String body) throws Exception {
		final String url = JsonPath.read(makeLink(user, folder, body).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.url");
		assertThat(url).startsWith("https://mizoine.example.org/.upload/").endsWith("/");
		return url.substring("https://mizoine.example.org".length());
	}

	/** Without login and without a CSRF token: the link is the permission. */
	private ResultActions upload(final String link, final String name, final byte[] bytes) throws Exception {
		return mockMvc.perform(put(URI.create(link + name)).content(bytes));
	}

	@Test
	void aLinkTakesFilesWithoutLoginAsItsUser() throws Exception {
		makeLink(GUEST, "/notes/inbox/", "{}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.url", startsWith("https://mizoine.example.org/.upload/")))
				.andExpect(jsonPath("$.expires").value("2026-11-26T10:15:00Z"));
		final String link = link(GUEST, "/notes/inbox/", "");

		mockMvc.perform(get(URI.create(link)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Upload to notes/inbox/")))
				.andExpect(content().string(containsString("<script type=\"module\" src=\"/ui/upload/page.js\"></script>")))
				.andExpect(header().string("Referrer-Policy", "no-referrer"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("X-Robots-Tag", "noindex"));
		upload(link, "Foto%201.jpg", new byte[] {(byte) 0xff, (byte) 0xd8, 0})
				.andExpect(status().isCreated())
				.andExpect(header().string("Referrer-Policy", "no-referrer"));

		assertThat(Files.readAllBytes(REPOSITORIES.path("notes/inbox/Foto 1.jpg"))).containsExactly(0xff, 0xd8, 0);
	}

	@Test
	void aLinkNeverOverwritesAndNeverReads() throws Exception {
		final String link = link(GUEST, "/notes/inbox/", "{}");

		upload(link, "old.jpg", new byte[] {1}).andExpect(status().isPreconditionFailed());
		assertThat(Files.readString(REPOSITORIES.path("notes/inbox/old.jpg"))).isEqualTo("old");
		mockMvc.perform(get(URI.create(link + "old.jpg"))).andExpect(status().isNotFound());
		mockMvc.perform(delete(URI.create(link + "old.jpg")))
				.andExpect(status().isNotFound());
		mockMvc.perform(post(URI.create(link + "old.jpg")).header("Destination", "/notes/inbox/x.jpg"))
				.andExpect(status().isNotFound());
		assertThat(REPOSITORIES.path("notes/inbox/old.jpg")).exists();
		// only plain names in the folder itself
		// an encoded '/' does not even reach Mizoine (the firewall refuses it)
		upload(link, "..%2Fprivate%2Fx.jpg", new byte[] {1}).andExpect(status().is4xxClientError());
		upload(link, ".env", new byte[] {1}).andExpect(status().isNotFound());
		assertThat(REPOSITORIES.path("notes/private/x.jpg")).doesNotExist();
	}

	@Test
	void aLinkForOneNameTakesThatFileOnceAndARefusedFileDoesNotCount() throws Exception {
		final String link = link(GUEST, "/notes/inbox/", "{\"name\": \"receipt.jpg\"}");

		mockMvc.perform(get(URI.create(link)))
				.andExpect(content().string(containsString("<form id=\"upload-link\" hidden data-name=\"receipt.jpg\">")))
				.andExpect(content().string(containsString("Choose the file for receipt.jpg")))
				.andExpect(content().string(containsString("for 1 file.")));
		upload(link, "other.jpg", new byte[] {1}).andExpect(status().isNotFound());
		upload(link, "receipt.jpg", new byte[2048]).andExpect(status().isContentTooLarge());
		upload(link, "receipt.jpg", new byte[] {1}).andExpect(status().isCreated());
		upload(link, "receipt.jpg", new byte[] {2}).andExpect(status().isNotFound());
		mockMvc.perform(get(URI.create(link))).andExpect(status().isNotFound())
				.andExpect(content().string(containsString("This upload link is not valid (any more).")));
		assertThat(Files.readAllBytes(REPOSITORIES.path("notes/inbox/receipt.jpg"))).containsExactly(1);
	}

	@Test
	void aFolderInTheWayOrTheFolderGone() throws Exception {
		Files.createDirectories(REPOSITORIES.path("notes/inbox/sub"));
		final String link = link(GUEST, "/notes/inbox/", "{}");

		upload(link, "sub", new byte[] {1}).andExpect(status().isPreconditionFailed());
		Files.createDirectories(REPOSITORIES.path("drop/box/gone"));
		final String gone = link(GUEST, "/drop/box/gone/", "{}");
		Files.delete(REPOSITORIES.path("drop/box/gone"));
		upload(gone, "x.jpg", new byte[] {1}).andExpect(status().isNotFound());
	}

	@Test
	void aLinkExpiresAfterFifteenMinutes() throws Exception {
		final String link = link(GUEST, "/notes/inbox/", "{}");

		clock.pass(Duration.ofMinutes(15));
		try {
			mockMvc.perform(get(URI.create(link))).andExpect(status().isNotFound());
			upload(link, "late.jpg", new byte[] {1}).andExpect(status().isNotFound());
		} finally {
			clock.now = START;
		}
		assertThat(REPOSITORIES.path("notes/inbox/late.jpg")).doesNotExist();
	}

	@Test
	void theGrantIsCheckedAgainWhenTheFileComes() throws Exception {
		final String link = link(GUEST, "/drop/box/", "{}");

		Files.writeString(REPOSITORIES.path("drop.yaml"), "path: drop\naccess:\n  guest: [elsewhere]\n");
		upload(link, "x.jpg", new byte[] {1}).andExpect(status().isNotFound());
		assertThat(REPOSITORIES.path("drop/box/x.jpg")).doesNotExist();
	}

	@Test
	void linksOnlyWhereTheUserMayWrite() throws Exception {
		makeLink(GUEST, "/notes/private/", "{}").andExpect(status().isNotFound());
		makeLink(GUEST, "/notes/", "{}").andExpect(status().isNotFound());
		makeLink(GUEST, "/notes/missing/", "{}").andExpect(status().isNotFound());
		makeLink(GUEST, "/notes/inbox/old.jpg", "{}").andExpect(status().isBadRequest());
		makeLink(GUEST, "/notes/inbox/", "{\"name\": \".env\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Not a file name: .env"));
		makeLink(GUEST, "/notes/inbox/", "{\"name\": \"a/b.jpg\"}").andExpect(status().isBadRequest());
		// without login and without a token: refused as any write (CSRF first)
		mockMvc.perform(post(URI.create("/notes/inbox/?upload-link"))).andExpect(status().isForbidden());
	}

	@Test
	void atMostTwentyOpenLinksPerUser() throws Exception {
		for (int i = 0; i < UploadLinks.MAX_OPEN_PER_USER; i++) {
			makeLink(ARTEM, "/notes/private/", "{}").andExpect(status().isCreated());
		}
		makeLink(ARTEM, "/notes/private/", "{}").andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("Too many open upload links (20): wait until some expire."));
	}
}
