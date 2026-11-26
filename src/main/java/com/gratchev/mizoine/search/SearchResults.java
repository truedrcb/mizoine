package com.gratchev.mizoine.search;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer to a search (step 57).
 *
 * @param total how many the user may see in all, as far as the index knows
 */
@Schema(description = "Search results, best first")
public record SearchResults(String query,
		@Schema(description = "all-words, any-word, similar-spelling (each tried only if the stricter found nothing) or none")
		Strictness strictness,
		long total, int offset, int limit, List<Hit> hits,
		@Schema(description = "The index state of each searched repository") List<IndexInfo> indexes) {

	/**
	 * One found file or folder.
	 *
	 * @param path below the repository root; folders end with '/'
	 * @param title a Markdown file's first heading, else the name
	 */
	@Schema(description = "A found file or folder")
	public record Hit(String repository, String path,
			@Schema(description = "Its page") String url,
			@Schema(description = "file or folder") String kind,
			String title,
			@Schema(description = "Up to 3 passages of a text file with the found words") List<Snippet> snippets,
			@Schema(description = "A folder's tags") List<String> tags) {
	}
}
