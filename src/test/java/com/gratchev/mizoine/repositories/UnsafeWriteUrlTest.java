package com.gratchev.mizoine.repositories;

import static com.gratchev.mizoine.TestUsers.GUEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gratchev.mizoine.TestHttp;
import com.gratchev.mizoine.TestRepositories;

/**
 * Step 35 (security): the write methods with URLs and {@code Destination} headers that try to leave the grants
 * change nothing — on a real server, where Tomcat decodes and normalizes before Spring sees the path. guest may
 * write in {@code public} only; {@code public/link-private} is a link to {@code private}. The URL forms are those of
 * {@link UnsafeUrlTest}; the permission rules themselves are in {@link WritePermissionMatrixTest}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UnsafeWriteUrlTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("tree/public/note.md", "note")
			.file("tree/private/secret.md", "secret")
			.file("tree/private/folder/f.md", "f")
			.folder("tree/private/empty")
			.file("tree/.git/config", "config")
			.link("tree/public/link-private", "tree/private")
			.repository("tree", "path: tree\naccess:\n  artem: [\"/\"]\n  guest: [public]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void delete() throws IOException {
		REPOSITORIES.delete();
	}

	private final TestHttp http;

	UnsafeWriteUrlTest(@LocalServerPort final int port) {
		this.http = new TestHttp(port);
	}

	/** The paths below {@code /tree/}: leaving {@code public} by every encoding, a hidden folder, a link. */
	private static final String[] ATTACKS = {"public/../private/", "%2e%2e/private/", "public/%2E%2E/private/",
			"public%2f..%2fprivate/", "public/..%2fprivate/", "public%5c..%5cprivate/", "public/%252e%252e/private/",
			"public/;x=1/../../private/", "public/%00/", ".git/", "%2egit/", "public/link-private/"};

	@ParameterizedTest
	@ValueSource(strings = {"secret.md", "new.md"})
	void noFileIsPut(final String name) {
		for (final String attack : ATTACKS) {
			assertRefusedWithoutChange("PUT " + attack + name, http.request("/tree/" + attack + name, GUEST)
					.PUT(HttpRequest.BodyPublishers.ofString("overwritten")));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"new/", "empty/"})
	void noFolderIsCreatedOrDeleted(final String name) {
		for (final String attack : ATTACKS) {
			assertRefusedWithoutChange("PUT " + attack + name, http.request("/tree/" + attack + name, GUEST)
					.PUT(HttpRequest.BodyPublishers.noBody()));
			assertRefusedWithoutChange("DELETE " + attack + name, http.request("/tree/" + attack + name, GUEST).DELETE());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"secret.md", "config"})
	void noFileIsDeleted(final String name) {
		for (final String attack : ATTACKS) {
			assertRefusedWithoutChange("DELETE " + attack + name, http.request("/tree/" + attack + name, GUEST).DELETE());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"secret.md", "folder/"})
	void nothingIsMovedOut(final String name) {
		for (final String attack : ATTACKS) {
			final String to = name.endsWith("/") ? "/tree/public/moved/" : "/tree/public/moved.md";
			assertRefusedWithoutChange("MOVE " + attack + name, http.request("/tree/" + attack + name, GUEST)
					.header("Destination", to).POST(HttpRequest.BodyPublishers.noBody()));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"moved.md", "secret.md"})
	void nothingIsMovedIn(final String name) {
		for (final String attack : ATTACKS) {
			assertRefusedWithoutChange("MOVE to " + attack + name, http.request("/tree/public/note.md", GUEST)
					.header("Destination", "/tree/" + attack + name).POST(HttpRequest.BodyPublishers.noBody()));
		}
	}

	private void assertRefusedWithoutChange(final String what, final HttpRequest.Builder request) {
		final Map<String, String> before = snapshot();
		final int status;
		try {
			status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		} catch (final IOException | InterruptedException e) {
			throw new IllegalStateException(what, e);
		}

		assertThat(status).as(what).isIn(400, 404);
		assertThat(snapshot()).as(what).isEqualTo(before);
	}

	/** Every entry of the tree: a link's target, a file's content or "folder". */
	private static Map<String, String> snapshot() {
		final Path root = REPOSITORIES.path("tree");
		try (Stream<Path> entries = Files.walk(root)) {
			final Map<String, String> result = new TreeMap<>();
			entries.forEach(entry -> result.put(root.relativize(entry).toString(), describe(entry)));
			return result;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String describe(final Path entry) {
		try {
			if (Files.isSymbolicLink(entry)) {
				return "link to " + Files.readSymbolicLink(entry);
			}
			return Files.isDirectory(entry) ? "folder" : Files.readString(entry);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
