package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
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
 * Steps 71–73 end to end: the real {@code ui/folder/folder.js} in Node ({@code src/test/js/e2e/folder-flow.mjs})
 * against the real server, with a browser's session and the CSRF cookie of the folder page: New file, upload,
 * replace, rename and delete, each never overwriting what it was not asked to.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FolderEndToEndTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("tree/inbox")
			.repository("tree", "path: tree\n" + TestRepositories.ARTEM_EVERYTHING);

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void deleteTree() throws IOException {
		REPOSITORIES.delete();
	}

	private final int port;

	FolderEndToEndTest(@LocalServerPort final int port) {
		this.port = port;
	}

	@Test
	void createUploadReplaceRenameAndDelete() throws Exception {
		final Process node = new ProcessBuilder("node", "src/test/js/e2e/folder-flow.mjs", "http://127.0.0.1:" + port,
				ARTEM, password(ARTEM), "/tree/inbox/").redirectErrorStream(true).start();
		if (!node.waitFor(60, TimeUnit.SECONDS)) {
			node.destroyForcibly();
			throw new AssertionError("the folder flow did not finish");
		}
		final List<String> steps = new String(node.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();

		assertThat(steps).containsExactly("login 302", "page 200", "create created", "create-again exists", "upload uploaded",
				"upload-again exists", "replace replaced", "rename renamed", "rename-onto-existing exists", "delete deleted",
				"done");
		assertThat(Files.readAllBytes(REPOSITORIES.path("tree/inbox/Foto.png")))
				.isEqualTo(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a});
		try (var files = Files.list(REPOSITORIES.path("tree/inbox"))) {
			assertThat(files.map(file -> file.getFileName().toString())).containsExactly("Foto.png");
		}
	}
}
