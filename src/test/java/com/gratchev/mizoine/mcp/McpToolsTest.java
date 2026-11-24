package com.gratchev.mizoine.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gratchev.mizoine.repositories.RepositoryFileParser;
import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryRegistry;
import com.gratchev.mizoine.repositories.RepositoryWriter;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import tools.jackson.databind.json.JsonMapper;

/**
 * Step 33: what each MCP tool answers in every case, with the real {@link RepositoryFiles} on a temporary folder
 * (no server: {@code McpEndpointTest} covers the protocol). The texts tell the agent what happened and what to do.
 */
class McpToolsTest {

	private static final long LIMIT = 10;

	private final Path base;
	private final List<SyncToolSpecification> tools;

	McpToolsTest(@TempDir final Path base) throws IOException {
		this.base = base;
		Files.createDirectories(base.resolve("tree/inbox/full"));
		Files.writeString(base.resolve("tree/inbox/full/kept.md"), "x");
		Files.writeString(base.resolve("tree/inbox/note.md"), "old");
		Files.write(base.resolve("tree/inbox/binary.bin"), new byte[] {(byte) 0xff, (byte) 0xfe});
		Files.writeString(base.resolve("tree.yaml"), "path: tree\naccess:\n  agent: [inbox]\n");
		final RepositoryFiles files = new RepositoryFiles(
				new RepositoryRegistry(base, new RepositoryFileParser(), user -> true, null), new RepositoryWriter());
		this.tools = new McpTools(files, LIMIT, new JacksonMcpJsonMapper(JsonMapper.builder().build())).all();
	}

	private CallToolResult call(final String tool, final Map<String, Object> arguments) {
		return tools.stream().filter(spec -> spec.tool().name().equals(tool)).findFirst().orElseThrow()
				.callHandler().apply(McpTransportContext.create(Map.of(McpTools.USER, "agent")), new CallToolRequest(tool, arguments));
	}

	private static String text(final CallToolResult result) {
		return (result.isError() ? "error: " : "") + ((TextContent) result.content().getFirst()).text();
	}

	private String call(final String tool, final String path) {
		return text(call(tool, Map.of("repository", "tree", "path", path)));
	}

	private String write(final String path, final String content) {
		return text(call("write_file", Map.of("repository", "tree", "path", path, "content", content)));
	}

	// write_file

	@Test
	void writeCreatesAndReplacesAndNamesTheVersion() throws IOException {
		assertThat(write("inbox/new.md", "a")).startsWith("Created tree/inbox/new.md, version \"");
		assertThat(write("inbox/note.md", "new")).startsWith("Replaced tree/inbox/note.md, version \"");
		assertThat(Files.readString(base.resolve("tree/inbox/note.md"), StandardCharsets.UTF_8)).isEqualTo("new");
	}

	@Test
	void writeExplainsWhyNot() {
		assertThat(write("inbox/missing/x.md", "a")).isEqualTo("error: Not written: the folder of tree/inbox/missing/x.md "
				+ "does not exist. Create it with create_folder first.");
		assertThat(write("inbox/full", "a")).isEqualTo("error: Not written: tree/inbox/full is a folder.");
		assertThat(write("inbox/big.md", "x".repeat((int) LIMIT + 1))).isEqualTo("error: Not written: larger than 10 bytes.");
		assertThat(write("elsewhere.md", "a")).isEqualTo("error: Not found: tree/elsewhere.md");
	}

	// create_folder

	@Test
	void createFolderOnceAndAgain() {
		assertThat(call("create_folder", "inbox/topic/")).isEqualTo("Created folder tree/inbox/topic/");
		assertThat(call("create_folder", "inbox/topic/")).isEqualTo("Folder exists: tree/inbox/topic/");
	}

	@Test
	void createFolderExplainsWhyNot() {
		assertThat(call("create_folder", "inbox/a/b/")).isEqualTo("error: Not created: the parent folder of tree/inbox/a/b/ "
				+ "does not exist.");
		assertThat(call("create_folder", "inbox/note.md/")).isEqualTo("error: Not created: a file is at tree/inbox/note.md/");
		assertThat(call("create_folder", "other/")).isEqualTo("error: Not found: tree/other/");
	}

	// delete

	@Test
	void deleteExplainsWhyNot() {
		assertThat(call("delete", "inbox/full/")).isEqualTo("error: Not deleted: folder tree/inbox/full/ is not empty.");
		assertThat(call("delete", "inbox/")).isEqualTo("error: Not deleted: tree/inbox/ is one of your granted folders.");
		assertThat(call("delete", "inbox/missing.md")).isEqualTo("error: Not found: tree/inbox/missing.md");
		assertThat(call("delete", "inbox/note.md")).isEqualTo("Deleted tree/inbox/note.md");
	}

	// read_file, list_folder, arguments

	@Test
	void notTextIsSaid() {
		assertThat(call("read_file", "inbox/binary.bin")).isEqualTo("error: Not a text file (UTF-8, up to 2 MB): tree/inbox/binary.bin");
	}

	@Test
	void folderTheAgentMayNotSeeIsNotFound() {
		assertThat(call("list_folder", "secret/")).isEqualTo("error: Not found: tree/secret/");
		assertThat(call("list_folder", "inbox/")).contains("\"folders\":[\"full\"]");
	}

	@Test
	void missingArgumentIsSaid() {
		assertThat(text(call("read_file", Map.of("repository", "tree")))).isEqualTo("error: Missing argument: path");
		assertThat(text(call("read_file", Map.of("repository", "tree", "path", 42)))).isEqualTo("error: Missing argument: path");
	}
}
