package com.gratchev.mizoine.mcp;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.GUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static com.gratchev.mizoine.mcp.TestMcp.call;
import static com.gratchev.mizoine.mcp.TestMcp.text;

import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gratchev.mizoine.TestHttp;
import com.gratchev.mizoine.TestRepositories;
import com.gratchev.mizoine.TestUsers;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Step 33: {@code /mcp}, the MCP endpoint for AI agents, with the real MCP client against the real server.
 * The agent is the test user guest, with grants only to {@code knowledge} and {@code inbox}: it stores and
 * retrieves knowledge there and sees nothing else. Login with HTTP Basic (OAuth: step 34).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class McpEndpointTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("tree/knowledge/ideas.md", "# Ideen\r\nmehr ü\r\n")
			.folder("tree/inbox")
			.file("tree/private/secret.md", "secret")
			.file("tree/readme.md", "above the grants")
			.file("tree/.git/config", "git")
			.repository("tree", "path: tree\naccess:\n  artem: [\"/\"]\n  guest: [knowledge, inbox]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void delete() throws IOException {
		REPOSITORIES.delete();
	}

	private final int port;

	McpEndpointTest(@LocalServerPort final int port) {
		this.port = port;
	}

	private McpSyncClient client(final String user) {
		return TestMcp.connect("http://127.0.0.1:" + port, user == null ? null : TestUsers.basicAuthHeader(user));
	}

	// the tools

	@Test
	void toolsAreDescribedForTheAgent() {
		try (McpSyncClient client = client(GUEST)) {
			final List<McpSchema.Tool> tools = client.listTools().tools();

			assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
					"list_repositories", "list_folder", "read_file", "write_file", "upload_file", "create_upload_link", "create_folder",
					"delete", "move",
					"search", "set_tags", "list_tags");
			assertThat(tools).allSatisfy(tool -> assertThat(tool.description()).isNotBlank());
			assertThat(tools).filteredOn(tool -> tool.name().equals("write_file")).singleElement()
					.satisfies(tool -> assertThat(tool.description()).contains("git"));
		}
	}

	@Test
	void listsOnlyItsRepositoriesAndTheWayDownToItsFolders() {
		try (McpSyncClient client = client(GUEST)) {
			assertThat(text(call(client, "list_repositories", Map.of()))).contains("tree");

			final String root = text(call(client, "list_folder", Map.of("repository", "tree", "path", "/")));
			assertThat(root).contains("inbox").contains("knowledge")
					.doesNotContain("private").doesNotContain("readme.md").doesNotContain(".git");
		}
	}

	@Test
	void readsFilesRaw() {
		try (McpSyncClient client = client(GUEST)) {
			final CallToolResult result = call(client, "read_file", Map.of("repository", "tree", "path", "knowledge/ideas.md"));

			assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
			assertThat(text(result)).isEqualTo("# Ideen\r\nmehr ü\r\n");
			// step 42: the version travels as structured content too
			assertThat(result.structuredContent()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
					.containsEntry("text", "# Ideen\r\nmehr ü\r\n").containsKey("version");
		}
	}

	@Test
	void storesKnowledgeExactlyAsGiven() throws IOException {
		try (McpSyncClient client = client(GUEST)) {
			final CallToolResult written = call(client, "write_file",
					Map.of("repository", "tree", "path", "inbox/note.md", "content", "# Notiz ü\n"));

			assertThat(written.isError()).isNotEqualTo(Boolean.TRUE);
			assertThat(Files.readString(REPOSITORIES.path("tree/inbox/note.md"))).isEqualTo("# Notiz ü\n");
			assertThat(text(call(client, "read_file", Map.of("repository", "tree", "path", "inbox/note.md"))))
					.isEqualTo("# Notiz ü\n");
		}
	}

	/** Step 74 (D80): the largest binary file, 10 MB as base64 (about 13 MB of JSON), through the real endpoint. */
	@Test
	void theLargestUploadGoesThroughTheEndpointAndBack() throws IOException {
		final byte[] bytes = new byte[McpTools.MAX_BINARY_BYTES];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) (i * 31);
		}
		try (McpSyncClient client = client(GUEST)) {
			final CallToolResult uploaded = call(client, "upload_file", Map.of("repository", "tree", "path", "inbox/scan.pdf",
					"content", Base64.getEncoder().encodeToString(bytes)));

			assertThat(text(uploaded)).startsWith("Created tree/inbox/scan.pdf");
			assertThat(Files.readAllBytes(REPOSITORIES.path("tree/inbox/scan.pdf"))).isEqualTo(bytes);
			final CallToolResult read = call(client, "read_file", Map.of("repository", "tree", "path", "inbox/scan.pdf"));
			assertThat(Base64.getDecoder().decode(((McpSchema.BlobResourceContents)
					((McpSchema.EmbeddedResource) read.content().get(1)).resource()).blob())).isEqualTo(bytes);
		}
	}

	@Test
	void anOldVersionIsNotOverwritten() throws IOException {
		try (McpSyncClient client = client(GUEST)) {
			call(client, "write_file", Map.of("repository", "tree", "path", "inbox/shared.md", "content", "v1"));
			final CallToolResult stale = call(client, "write_file",
					Map.of("repository", "tree", "path", "inbox/shared.md", "content", "lost", "if_match", "\"outdated\""));

			assertThat(stale.isError()).isTrue();
			assertThat(Files.readString(REPOSITORIES.path("tree/inbox/shared.md"))).isEqualTo("v1");
		}
	}

	@Test
	void createsAndDeletesFoldersExplicitly() {
		try (McpSyncClient client = client(GUEST)) {
			assertThat(call(client, "create_folder", Map.of("repository", "tree", "path", "inbox/topic/")).isError())
					.isNotEqualTo(Boolean.TRUE);
			assertThat(REPOSITORIES.path("tree/inbox/topic")).isDirectory();

			assertThat(call(client, "delete", Map.of("repository", "tree", "path", "inbox/topic/")).isError())
					.isNotEqualTo(Boolean.TRUE);
			assertThat(REPOSITORIES.path("tree/inbox/topic")).doesNotExist();
		}
	}

	@Test
	void theAgentFilesANoteFromItsInbox() throws IOException {
		try (McpSyncClient client = client(GUEST)) {
			call(client, "write_file", Map.of("repository", "tree", "path", "inbox/to-file.md", "content", "# Wissen"));

			assertThat(text(call(client, "move", Map.of("repository", "tree", "from", "inbox/to-file.md",
					"to", "knowledge/filed.md")))).startsWith("Moved");
			assertThat(Files.readString(REPOSITORIES.path("tree/knowledge/filed.md"))).isEqualTo("# Wissen");
			assertThat(REPOSITORIES.path("tree/inbox/to-file.md")).doesNotExist();
		}
	}

	// nothing outside its folders: the same answer for missing and not allowed (D2)

	@ParameterizedTest
	@ValueSource(strings = {"private/secret.md", "readme.md", ".git/config", "../tree.yaml", "knowledge/missing.md"})
	void nothingElseIsReadable(final String path) {
		try (McpSyncClient client = client(GUEST)) {
			final CallToolResult result = call(client, "read_file", Map.of("repository", "tree", "path", path));

			assertThat(result.isError()).as(path).isTrue();
			assertThat(text(result)).as(path).isEqualTo("error: Not found: tree/" + path);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"private/x.md", "x.md", ".git/config", "knowledge/.hidden"})
	void nothingElseIsWritable(final String path) throws IOException {
		try (McpSyncClient client = client(GUEST)) {
			assertThat(call(client, "write_file", Map.of("repository", "tree", "path", path, "content", "x")).isError())
					.as(path).isTrue();
		}
		assertThat(Files.readString(REPOSITORIES.path("tree/.git/config"))).isEqualTo("git");
		assertThat(REPOSITORIES.path("tree/private/x.md")).doesNotExist();
	}

	@Test
	void theWholeRepositoryUserSeesMore() {
		try (McpSyncClient client = client(ARTEM)) {
			assertThat(text(call(client, "read_file", Map.of("repository", "tree", "path", "private/secret.md"))))
					.isEqualTo("secret");
		}
	}

	// login

	@Test
	void withoutLoginNoConnection() {
		assertThatThrownBy(() -> client(null)).isInstanceOf(RuntimeException.class);
	}

	/** Chat apps connect without credentials first: they need 401 to find the way to OAuth (step 34), not 403. */
	@Test
	void withoutCredentialsTheAnswerIs401() throws Exception {
		final TestHttp http = new TestHttp(port);
		final HttpResponse<String> response = http.send(http.request("/mcp", null)
						.header("Content-Type", "application/json")
						.header("Accept", "application/json, text/event-stream")
						.POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(401);
	}

	@Test
	void aCrossSiteFormCannotCallTools() throws Exception {
		// a browser may send a cached Basic login with a form post; forms cannot send application/json (D11)
		final TestHttp http = new TestHttp(port);
		final HttpResponse<String> response = http.send(http.request("/mcp", GUEST)
						.header("Content-Type", "text/plain")
						.POST(HttpRequest.BodyPublishers.ofString(
								"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}")),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(403);
	}
}
