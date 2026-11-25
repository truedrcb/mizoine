package com.gratchev.mizoine.search;

import java.util.Optional;

/**
 * The state of a repository's index (D59).
 *
 * @param reason why, for {@link IndexStatus#NEEDS_REINDEXING} ({@code format} or {@code damaged}) and
 *            {@link IndexStatus#FAILED} (what went wrong)
 * @param searchable whether search answers from an index meanwhile: the current one, or an older one that can
 *            still be read (D57)
 */
public record IndexState(IndexStatus status, Optional<String> reason, boolean searchable) {

	static final String FORMAT = "format";
	static final String DAMAGED = "damaged";

	static IndexState of(final IndexStatus status, final boolean searchable) {
		return new IndexState(status, Optional.empty(), searchable);
	}

	static IndexState needsReindexing(final String reason, final boolean searchable) {
		return new IndexState(IndexStatus.NEEDS_REINDEXING, Optional.of(reason), searchable);
	}
}
