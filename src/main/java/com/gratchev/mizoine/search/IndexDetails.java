package com.gratchev.mizoine.search;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What an index knows of itself (step 60, D49, D59).
 *
 * @param updating whether an update runs
 * @param progress while a reindexing runs: how far it is
 * @param indexedAt when the last full reindexing finished
 * @param updatedAt the last change of the index: a reindexing, Mizoine's own write, or an update
 * @param files how many files and folders the index holds
 * @param unreadable what the last reindexing or update could not read, by path (folders end with '/')
 */
public record IndexDetails(IndexState state, boolean updating, Optional<Progress> progress, Optional<Instant> indexedAt,
		Optional<Instant> updatedAt, long files, List<String> unreadable) {

	/** @param total the files and folders to index; 0 while they are still counted */
	public record Progress(int done, int total) {
	}
}
