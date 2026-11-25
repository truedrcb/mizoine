package com.gratchev.mizoine.search;

import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The index state of one searched repository, with every answer: a client can tell results may be incomplete.
 *
 * @param searchable whether this repository's results are in the answer
 */
@Schema(description = "The search index of a searched repository")
public record IndexInfo(String repository,
		@Schema(description = "not-indexed, needs-reindexing, reindexing, indexed or failed") IndexStatus status,
		@Schema(description = "Why: format or damaged (needs-reindexing), or what went wrong (failed)") Optional<String> reason,
		@Schema(description = "Whether its results are in the answer: from the current index, or an older one") boolean searchable) {

	static IndexInfo of(final String repository, final IndexState state) {
		return new IndexInfo(repository, state.status(), state.reason(), state.searchable());
	}
}
