package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.GUEST;
import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Steps 76–77 end to end: a link made over the API as an agent's tool does, then the real {@code ui/folder/folder.js}
 * in Node ({@code src/test/js/e2e/upload-link-flow.mjs}) uploads through it as the link's page on a phone does: no
 * login, no cookie, no CSRF token; asked to replace, the link still never overwrites (D83).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UploadLinkEndToEndTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("tree/inbox")
			.repository("tree", "path: tree\naccess:\n  guest: [inbox]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
		// only the host of the link; the flow uses its path on this server
		registry.add("mizoine.public-url", () -> "https://mizoine.example.org");
	}

	@AfterAll
	static void deleteTree() throws IOException {
		REPOSITORIES.delete();
	}

	private final int port;

	UploadLinkEndToEndTest(@LocalServerPort final int port) {
		this.port = port;
	}

	@Test
	void aPhotoThroughALinkWithoutLogin() throws Exception {
		final Process node = new ProcessBuilder("node", "src/test/js/e2e/upload-link-flow.mjs", "http://127.0.0.1:" + port,
				GUEST, password(GUEST), "/tree/inbox/").redirectErrorStream(true).start();
		if (!node.waitFor(60, TimeUnit.SECONDS)) {
			node.destroyForcibly();
			throw new AssertionError("the upload link flow did not finish");
		}
		final List<String> steps = new String(node.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();

		assertThat(steps).containsExactly("link 201", "page 200", "upload uploaded", "upload-again exists", "done");
		assertThat(Files.readAllBytes(REPOSITORIES.path("tree/inbox/IMG_0001.JPG")))
				.isEqualTo(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10});
	}
}
