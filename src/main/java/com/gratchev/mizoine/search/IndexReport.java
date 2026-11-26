package com.gratchev.mizoine.search;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The state of a repository's search index as one user may see it (step 60, D49, D59). The dates let the user judge
 * whether the index is current: a newest file younger than {@code updatedAt} is not in the index yet.
 */
@Schema(description = "The search index of a repository, with the dates to judge whether it is current")
public record IndexReport(String repository,
		@Schema(description = "not-indexed, needs-reindexing, reindexing, indexed or failed") IndexStatus status,
		@Schema(description = "Why: format or damaged (needs-reindexing), or what went wrong (failed)") Optional<String> reason,
		@Schema(description = "Whether search answers from it: the current index, or an older one meanwhile") boolean searchable,
		@Schema(description = "Whether an update runs") boolean updating,
		@Schema(description = "Whether you may reindex and update it: with access to the whole repository") boolean mayReindex,
		@Schema(description = "While a reindexing runs: files and folders done, and in all (0 while counting)")
		Optional<IndexDetails.Progress> progress,
		@Schema(description = "When the last reindexing finished") Optional<Instant> indexedAt,
		@Schema(description = "The last change of the index: a reindexing, a write through Mizoine, or an update")
		Optional<Instant> updatedAt,
		@Schema(description = "The newest modification time of a file in the folders you may read") Optional<Instant> newestFile,
		@Schema(description = "The time of the last Git commit; only with Git access") Optional<Instant> lastCommit,
		@Schema(description = "Files and folders in the index; only with access to the whole repository") Optional<Long> files,
		@Schema(description = "What the last reindexing or update could not read; only with access to the whole "
				+ "repository") Optional<List<String>> unreadable) {
}
