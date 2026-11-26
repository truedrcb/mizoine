package com.gratchev.mizoine.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gratchev.mizoine.repositories.RepositoryFileParser;
import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryRegistry;
import com.gratchev.mizoine.repositories.RepositoryWriter;
import com.gratchev.mizoine.search.Language;
import com.gratchev.mizoine.search.Search;
import com.gratchev.mizoine.search.SearchAnalysis;
import com.gratchev.mizoine.search.SearchIndexes;
import com.gratchev.mizoine.uploads.UploadLinkService;
import com.gratchev.mizoine.uploads.UploadLinks;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Step 59: the MCP tool {@code search}, as the agent user: only in its folders, the same search as the page. The
 * agent {@code agent} may use {@code inbox} and {@code knowledge}; {@code private} is not for it, nor is the
 * repository {@code other}.
 */
class McpSearchToolTest {

	private final Path base;
	private final SearchIndexes indexes;
	private final RepositoryFiles files;

	McpSearchToolTest(@TempDir final Path base) throws IOException {
		this.base = base;
		write("tree/knowledge/garden.md", "# Garden plan\n\nApples and pears.");
		write("tree/inbox/shopping.md", "Buy apples.");
		write("tree/private/apples.md", "Private apples.");
		write("other/apples.md", "Other apples.");
		Files.writeString(base.resolve("tree.yaml"), "path: tree\naccess:\n  agent: [inbox, knowledge]\n  artem: [\"/\"]\n");
		Files.writeString(base.resolve("other.yaml"), "path: other\naccess:\n  artem: [\"/\"]\n");
		this.files = new RepositoryFiles(new RepositoryRegistry(base, new RepositoryFileParser(), user -> true, null),
				new RepositoryWriter());
		this.indexes = new SearchIndexes(base.resolve("search-index"), SearchAnalysis.of(Language.parse("en")));
	}

	@AfterEach
	void close() {
		indexes.close();
	}

	private void write(final String path, final String content) throws IOException {
		Files.createDirectories(base.resolve(path).getParent());
		Files.writeString(base.resolve(path), content);
	}

	private List<SyncToolSpecification> tools(final Optional<Search> search) {
		return new McpTools(files, 1000, new JacksonMcpJsonMapper(JsonMapper.builder().build()), search,
				new UploadLinkService(files, new UploadLinks(Clock.systemUTC()), Optional.empty(), 1000)).all();
	}

	private CallToolResult search(final Map<String, Object> arguments) {
		return tools(Optional.of(new Search(files, indexes))).stream().filter(spec -> spec.tool().name().equals("search"))
				.findFirst().orElseThrow()
				.callHandler().apply(McpTransportContext.create(Map.of(McpTools.USER, "agent")), TestMcp.request("search", arguments));
	}

	private void reindex() {
		indexes.index("tree").reindex(base.resolve("tree"));
		indexes.index("other").reindex(base.resolve("other"));
	}

	@Test
	void findsOnlyInTheAgentsFolders() {
		reindex();

		final CallToolResult result = search(Map.of("query", "apples"));

		assertThat(TestMcp.text(result)).isEqualTo("""
				2 results for "apples" (all words):
				- tree/inbox/shopping.md: shopping.md
				  Buy **apples**.
				- tree/knowledge/garden.md: Garden plan
				  # Garden plan **Apples** and pears.
				Read a file with read_file.""");
		assertThat(result.structuredContent()).asInstanceOf(InstanceOfAssertFactories.MAP).containsEntry("total", 2L);
	}

	@Test
	void inOneRepository() {
		reindex();

		assertThat(TestMcp.text(search(Map.of("query", "pears", "repository", "tree", "limit", 1))))
				.startsWith("1 result for \"pears\" (all words):\n- tree/knowledge/garden.md: Garden plan");
	}

	@Test
	void aRepositoryWithoutAGrantIsUnknown() {
		reindex();

		assertThat(TestMcp.text(search(Map.of("query", "apples", "repository", "other"))))
				.isEqualTo("error: Not found: other");
	}

	@Test
	void nothingFoundAndLooserLevelsAreSaid() {
		reindex();

		assertThat(TestMcp.text(search(Map.of("query", "zebra")))).isEqualTo("Nothing found for \"zebra\".");
		assertThat(TestMcp.text(search(Map.of("query", "appels")))).startsWith(
				"2 results for \"appels\" (no word as written: similar spelling):");
	}

	@Test
	void withoutAnIndexTheAgentIsToldWhy() {
		assertThat(TestMcp.text(search(Map.of("query", "apples", "repository", "tree"))))
				.isEqualTo("error: Not searched: no search index yet for tree. A person with access to the whole "
						+ "repository can build it.");
	}

	@Test
	void wrongArgumentsAreSaid() {
		reindex();

		assertThat(TestMcp.text(search(Map.of("query", " ")))).isEqualTo("error: Write what to search for.");
		assertThat(TestMcp.text(search(Map.of()))).isEqualTo("error: Missing argument: query");
		assertThat(TestMcp.text(search(Map.of("query", "apples", "limit", 0))))
				.isEqualTo("error: limit must be from 1 to 100.");
		assertThat(TestMcp.text(search(Map.of("query", "apples", "limit", "ten")))).isEqualTo("error: Not a number: limit");
	}

	private CallToolResult tool(final String name, final Map<String, Object> arguments) {
		return tools(Optional.of(new Search(files, indexes))).stream().filter(spec -> spec.tool().name().equals(name))
				.findFirst().orElseThrow()
				.callHandler().apply(McpTransportContext.create(Map.of(McpTools.USER, "agent")), TestMcp.request(name, arguments));
	}

	@Test
	void theTagsInUseOnlyInTheAgentsFolders() throws IOException {
		write("tree/knowledge/topic/.tags", "project status:open");
		write("tree/inbox/new/.tags", "project");
		write("tree/private/secret/.tags", "secret");
		reindex();

		assertThat(TestMcp.text(tool("list_tags", Map.of("repository", "tree"))))
				.isEqualTo("Tags in use in tree: project (2), status:open (1). Keep to these where they fit.");
		assertThat(TestMcp.text(tool("list_tags", Map.of("repository", "other")))).isEqualTo("error: Not found: other");
	}

	@Test
	void searchByTag() throws IOException {
		write("tree/knowledge/topic/.tags", "project status:open");
		write("tree/inbox/new/.tags", "project");
		reindex();

		assertThat(TestMcp.text(search(Map.of("query", "#status:open")))).contains("- tree/knowledge/topic/: topic [project, status:open]");
		assertThat(TestMcp.text(search(Map.of("query", "#project", "tags", List.of("status:open")))))
				.startsWith("1 result for \"#project\"");
		assertThat(TestMcp.text(search(Map.of("query", "#project", "tags", "status:open"))))
				.isEqualTo("error: Not a list of texts: tags");
	}

	@Test
	void withoutSearchThereIsNoTool() {
		assertThat(tools(Optional.empty())).noneMatch(spec -> spec.tool().name().equals("search")
				|| spec.tool().name().equals("list_tags"));
		assertThat(tools(Optional.of(new Search(files, indexes))).stream().filter(spec -> spec.tool().name().equals("search"))
				.findFirst().orElseThrow().tool().description())
				.contains("\"a phrase\"", "-leftout", "prefix*", "only in the folders you may read");
	}
}
