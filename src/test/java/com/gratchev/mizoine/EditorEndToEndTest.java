package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.password;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The editor end to end: the real {@code ui/editor/editor.js} in Node ({@code src/test/js/e2e/editor-flow.mjs})
 * against the real server, with a browser's session (form login, cookies, CSRF header): load, save, save again.
 * Also through a proxy that weakens ETags as compressing proxies and CDNs do (bug 2026-11-23: behind Cloudflare
 * every save was "changed elsewhere").
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EditorEndToEndTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.folder("tree/notes")
			.repository("tree", "path: tree\n" + TestRepositories.ARTEM_EVERYTHING);
	private static final Path FILE = REPOSITORIES.path("tree/notes/plan.md");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void deleteTree() throws IOException {
		REPOSITORIES.delete();
	}

	private final int port;

	EditorEndToEndTest(@LocalServerPort final int port) {
		this.port = port;
	}

	@ParameterizedTest
	@ValueSource(strings = {"direct", "behind a proxy that weakens ETags"})
	void loadSaveAndSaveAgain(final String route) throws Exception {
		Files.writeString(FILE, "# Plan\r\nold\r\n");
		final List<String> steps;
		if (route.equals("direct")) {
			steps = runEditor("http://127.0.0.1:" + port);
		} else {
			try (WeakeningProxy proxy = new WeakeningProxy(port)) {
				steps = runEditor(proxy.baseUrl());
			}
		}

		assertThat(steps).as(route).containsExactly("login 302", "load loaded", "save-1 saved", "save-2 saved", "done");
		assertThat(Files.readString(FILE)).isEqualTo("# Plan\r\nsecond ü\r\n");
	}

	/** @return the lines the script printed */
	private static List<String> runEditor(final String baseUrl) throws IOException, InterruptedException {
		final Process node = new ProcessBuilder("node", "src/test/js/e2e/editor-flow.mjs", baseUrl, ARTEM, password(ARTEM),
				"/tree/notes/plan.md", "# Plan\nfirst\n", "# Plan\nsecond ü\n")
				.redirectErrorStream(true)
				.start();
		if (!node.waitFor(60, TimeUnit.SECONDS)) {
			node.destroyForcibly();
			throw new AssertionError("the editor flow did not finish");
		}
		return new String(node.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();
	}
}
