package com.gratchev.mizoine.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.search.IndexInfo;
import com.gratchev.mizoine.search.NotSearchableException;
import com.gratchev.mizoine.search.Page;
import com.gratchev.mizoine.search.Search;
import com.gratchev.mizoine.search.SearchQuery;
import com.gratchev.mizoine.search.SearchResults;
import com.gratchev.mizoine.search.Snippet;
import com.gratchev.mizoine.search.Strictness;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * The MCP tool {@code search} (step 59): the same search as the page, as the agent user, so only in the agent's
 * folders. The text lists the results for every client; the structured content has them all, as the JSON API.
 */
final class McpSearchTool {

	private static final String QUERY = "query";
	private static final String REPOSITORY = "repository";
	private static final String LIMIT = "limit";
	private static final String DESCRIPTION = "Search the files you may read: by name, and text files by content, best "
			+ "first. Words are found in every spelling of a letter (ä = ae = a, ß = ss, accents) and in their word forms. "
			+ "The query: words (all must be found), \"a phrase\", -leftout, prefix*; at most 200 characters. If no file "
			+ "has all words, files with any of them; then words of a similar spelling: the answer says which. Results "
			+ "are only in the folders you may read. Read a result with read_file.";
	private static final TypeRef<Map<String, Object>> MAP = new TypeRef<>() {
	};

	private final Search search;
	private final McpJsonMapper json;

	McpSearchTool(final Search search, final McpJsonMapper json) {
		this.search = search;
		this.json = json;
	}

	SyncToolSpecification specification() {
		final Map<String, Object> properties = new LinkedHashMap<>();
		properties.put(QUERY, Map.of("type", "string", "description", "What to find"));
		properties.put(REPOSITORY, Map.of("type", "string", "description",
				"Optional: only in this repository, from list_repositories; else in all"));
		properties.put(LIMIT, Map.of("type", "integer", "description", "Optional: how many results, 1 to 100 (default 20)"));
		return SyncToolSpecification.builder()
				.tool(McpSchema.Tool.builder("search", Map.of("type", "object", "properties", properties,
						"required", List.of(QUERY))).description(DESCRIPTION).build())
				.callHandler((final McpTransportContext context, final CallToolRequest request) -> call(
						(String) context.get(McpTools.USER), request.arguments() == null ? Map.of() : request.arguments()))
				.build();
	}

	private CallToolResult call(final String user, final Map<String, Object> arguments) {
		final Optional<String> repository = Optional.ofNullable(arguments.get(REPOSITORY)).map(String::valueOf);
		try {
			final SearchResults results = search.search(user, repository, SearchQuery.parse(McpTools.text(arguments, QUERY)),
					new Page(0, limit(arguments)));
			return CallToolResult.builder()
					.addTextContent(text(results))
					.structuredContent(json.convertValue(results, MAP))
					.isError(false)
					.build();
		} catch (final RepositoryFiles.NotVisibleException e) {
			return McpTools.error("Not found: " + repository.orElse(""));
		} catch (final NotSearchableException e) {
			return McpTools.error("Not searched: no search index yet for "
					+ e.indexes().stream().map(IndexInfo::repository).collect(Collectors.joining(", "))
					+ ". A person with access to the whole repository can build it.");
		} catch (final IllegalArgumentException | RepositoryFiles.MisconfiguredException e) {
			return McpTools.error(e.getMessage());
		}
	}

	private static int limit(final Map<String, Object> arguments) {
		final Object limit = arguments.get(LIMIT);
		if (limit == null) {
			return Page.DEFAULT_LIMIT;
		}
		if (!(limit instanceof final Number number)) {
			throw new IllegalArgumentException("Not a number: " + LIMIT);
		}
		return number.intValue();
	}

	/** One line per result, its first passage below it with the found words in {@code **bold**}. */
	private static String text(final SearchResults results) {
		if (results.strictness() == Strictness.NONE) {
			return "Nothing found for \"" + results.query() + "\"." + notSearched(results);
		}
		final StringBuilder text = new StringBuilder()
				.append(results.total()).append(results.total() == 1 ? " result" : " results")
				.append(" for \"").append(results.query()).append("\" (").append(level(results.strictness())).append("):");
		for (final SearchResults.Hit hit : results.hits()) {
			text.append("\n- ").append(hit.repository()).append('/').append(hit.path()).append(": ").append(hit.title());
			hit.snippets().stream().findFirst().ifPresent(snippet -> text.append("\n  ").append(passage(snippet)));
		}
		return text.append("\nRead a file with read_file.").append(notSearched(results)).toString();
	}

	private static String level(final Strictness strictness) {
		return switch (strictness) {
			case ALL_WORDS -> "all words";
			case ANY_WORD -> "not all words: any of them";
			default -> "no word as written: similar spelling";
		};
	}

	private static String passage(final Snippet snippet) {
		return snippet.parts().stream()
				.map(part -> part.match() ? "**" + part.text() + "**" : part.text())
				.collect(Collectors.joining())
				.replaceAll("\\s+", " ")
				.strip();
	}

	private static String notSearched(final SearchResults results) {
		final List<String> missing = results.indexes().stream().filter(index -> !index.searchable())
				.map(IndexInfo::repository).toList();
		return missing.isEmpty() ? "" : "\nNot searched, no search index yet: " + String.join(", ", missing) + ".";
	}
}
