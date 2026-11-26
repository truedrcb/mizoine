package com.gratchev.mizoine.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gratchev.mizoine.repositories.RepositoryFileParser;
import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryRegistry;
import com.gratchev.mizoine.repositories.RepositoryWriter;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
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
		Files.createDirectories(base.resolve("tree/knowledge"));
		Files.createDirectories(base.resolve("tree/private"));
		Files.writeString(base.resolve("tree.yaml"), "path: tree\naccess:\n  agent: [inbox, knowledge]\n");
		final RepositoryFiles files = new RepositoryFiles(
				new RepositoryRegistry(base, new RepositoryFileParser(), user -> true, null), new RepositoryWriter());
		this.tools = new McpTools(files, LIMIT, new JacksonMcpJsonMapper(JsonMapper.builder().build()), Optional.empty()).all();
	}

	private CallToolResult call(final String tool, final Map<String, Object> arguments) {
		return tools.stream().filter(spec -> spec.tool().name().equals(tool)).findFirst().orElseThrow()
				.callHandler().apply(McpTransportContext.create(Map.of(McpTools.USER, "agent")), TestMcp.request(tool, arguments));
	}

	private String call(final String tool, final String path) {
		return TestMcp.text(call(tool, Map.of("repository", "tree", "path", path)));
	}

	private String write(final String path, final String content) {
		return TestMcp.text(call("write_file", Map.of("repository", "tree", "path", path, "content", content)));
	}

	// tags (step 64)

	private String setTags(final String path, final Object tags) {
		return TestMcp.text(call("set_tags", Map.of("repository", "tree", "path", path, "tags", tags)));
	}

	@Test
	void setTagsAndSeeThemInTheListing() throws IOException {
		assertThat(setTags("inbox/", List.of("project", "status:open"))).startsWith(
				"Tags of tree/inbox/: project, status:open (version \"");
		assertThat(Files.readString(base.resolve("tree/inbox/.tags"))).isEqualTo("project\nstatus:open\n");

		assertThat(call("list_folder", "inbox/")).contains("\"tags\":[\"project\",\"status:open\"]");
		assertThat(call("list_folder", "/")).contains("\"folderTags\":{\"inbox\":[\"project\",\"status:open\"]}");
		assertThat(setTags("inbox/", List.of())).isEqualTo("Tags of tree/inbox/: none");
	}

	@Test
	void setTagsExplainsWhyNot() {
		assertThat(setTags("inbox/", List.of("two words"))).isEqualTo(
				"error: Not a tag: \"two words\" (letters, digits, -, _, / and one : only).");
		assertThat(setTags("inbox/", "project")).isEqualTo("error: Not a list of texts: tags");
		assertThat(setTags("inbox/note.md", List.of("a"))).isEqualTo(
				"error: Tags are for folders: a folder's path ends with '/'.");
		assertThat(setTags("private/", List.of("a"))).isEqualTo("error: Not found: tree/private/");
		assertThat(TestMcp.text(call("set_tags", Map.of("repository", "tree", "path", "inbox/", "tags", List.of("a"),
				"if_match", "\"0-0\"")))).isEqualTo("error: Not changed: the tags of tree/inbox/ were changed since that "
						+ "version. Read them again first (list_folder).");
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
		assertThat(TestMcp.text(call("read_file", Map.of("repository", "tree")))).isEqualTo("error: Missing argument: path");
		assertThat(TestMcp.text(call("read_file", Map.of("repository", "tree", "path", 42)))).isEqualTo("error: Missing argument: path");
	}

	@Test
	void aVersionThatIsNoTextIsSaid() {
		assertThat(TestMcp.text(call("write_file", Map.of("repository", "tree", "path", "inbox/n.md", "content", "x",
				"if_match", 42)))).isEqualTo("error: Not a text: if_match");
		assertThat(TestMcp.text(call("move", Map.of("repository", "tree", "from", "inbox/n.md", "to", "inbox/m.md",
				"if_match", 42)))).isEqualTo("error: Not a text: if_match");
	}

	// step 42: read_file gives the version, so the agent can write back safely

	@SuppressWarnings("unchecked")
	private static String version(final CallToolResult result) {
		return (String) ((Map<String, Object>) result.structuredContent()).get("version");
	}

	@Test
	void readGivesTextAndVersion() {
		final CallToolResult read = call("read_file", Map.of("repository", "tree", "path", "inbox/note.md"));

		assertThat(TestMcp.text(read)).isEqualTo("old");
		assertThat(((Map<?, ?>) read.structuredContent()).get("text")).isEqualTo("old");
		assertThat(version(read)).startsWith("\"");
	}

	@Test
	void theVersionFromReadProtectsTheWriteBack() throws IOException {
		final String version = version(call("read_file", Map.of("repository", "tree", "path", "inbox/note.md")));

		assertThat(TestMcp.text(call("write_file", Map.of("repository", "tree", "path", "inbox/note.md", "content", "mine",
				"if_match", version)))).startsWith("Replaced");

		// someone else changes it in between: the same version no longer fits
		Files.writeString(base.resolve("tree/inbox/note.md"), "theirs, longer");
		assertThat(TestMcp.text(call("write_file", Map.of("repository", "tree", "path", "inbox/note.md", "content", "lost",
				"if_match", version)))).startsWith("error: Not written: tree/inbox/note.md was changed");
		assertThat(Files.readString(base.resolve("tree/inbox/note.md"))).isEqualTo("theirs, longer");
	}

	@Test
	void writeTellsTheAgentToPassTheVersionFromRead() {
		assertThat(tools.stream().filter(spec -> spec.tool().name().equals("write_file")).findFirst().orElseThrow()
				.tool().description()).contains("version from read_file");
	}

	// step 41: move and rename

	private String move(final String from, final String to) {
		return TestMcp.text(call("move", Map.of("repository", "tree", "from", from, "to", to)));
	}

	@Test
	void moveBetweenGrantedFoldersAndRename() throws IOException {
		assertThat(move("inbox/note.md", "knowledge/note.md")).isEqualTo("Moved tree/inbox/note.md to tree/knowledge/note.md");
		assertThat(Files.readString(base.resolve("tree/knowledge/note.md"))).isEqualTo("old");
		assertThat(move("knowledge/note.md", "knowledge/renamed.md")).startsWith("Moved");
		assertThat(move("inbox/full/", "knowledge/full/")).startsWith("Moved");
		assertThat(base.resolve("tree/knowledge/full/kept.md")).exists();
	}

	@Test
	void moveExplainsWhyNot() throws IOException {
		Files.writeString(base.resolve("tree/knowledge/taken.md"), "x");

		assertThat(move("inbox/note.md", "knowledge/taken.md")).isEqualTo("error: Not moved: tree/knowledge/taken.md exists. "
				+ "Choose another name, or delete it first.");
		assertThat(move("inbox/note.md", "knowledge/none/x.md")).isEqualTo("error: Not moved: the folder of "
				+ "tree/knowledge/none/x.md does not exist. Create it with create_folder first.");
		assertThat(move("inbox/full/", "inbox/full/deeper/")).isEqualTo("error: Not moved: a folder cannot move into itself.");
		assertThat(move("inbox/", "knowledge/inbox/")).isEqualTo("error: Not moved: tree/inbox/ is one of your granted folders.");
		assertThat(TestMcp.text(call("move", Map.of("repository", "tree", "from", "inbox/note.md", "to", "knowledge/x.md",
				"if_match", "\"outdated\"")))).isEqualTo("error: Not moved: tree/inbox/note.md was changed since that version. "
				+ "Read it again first.");
		assertThat(base.resolve("tree/inbox/note.md")).exists();
	}

	@Test
	void nothingMovesOutOfOrIntoFoldersTheAgentMayNotWrite() throws IOException {
		Files.writeString(base.resolve("tree/private/secret.md"), "s");

		assertThat(move("inbox/note.md", "private/note.md")).isEqualTo("error: Not found: tree/inbox/note.md");
		assertThat(move("private/secret.md", "inbox/secret.md")).isEqualTo("error: Not found: tree/private/secret.md");
		assertThat(move("inbox/note.md", "inbox/.hidden.md")).isEqualTo("error: Not found: tree/inbox/note.md");
		assertThat(base.resolve("tree/inbox/note.md")).exists();
		assertThat(base.resolve("tree/private/secret.md")).exists();
	}
}
