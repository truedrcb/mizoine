package com.gratchev.mizoine.search;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The search indexes of all repositories (D60), each opened when first needed, in
 * {@code mizoine.search.index-dir/<repository>/}. Repository names are safe as folder names (step 14). Reindexing
 * and updates on request run in the background (step 60).
 */
public class SearchIndexes {

	private static final Logger log = LoggerFactory.getLogger(SearchIndexes.class);

	private final Path dir;
	private final SearchAnalysis analysis;
	private final Map<String, RepositoryIndex> indexes = new ConcurrentHashMap<>();
	private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

	public SearchIndexes(final Path dir, final SearchAnalysis analysis) {
		this.dir = dir;
		this.analysis = analysis;
	}

	public SearchAnalysis analysis() {
		return analysis;
	}

	public RepositoryIndex index(final String repository) {
		return indexes.computeIfAbsent(repository, name -> RepositoryIndex.open(name, dir.resolve(name), analysis));
	}

	/** Starts a reindexing in the background, unless one runs: then that one counts. */
	public void reindexLater(final String repository, final Path root) {
		final RepositoryIndex index = index(repository);
		if (index.begin()) {
			background.execute(() -> index.build(root));
		}
	}

	/** Starts an update in the background, if the index can take one and none runs. */
	public RepositoryIndex.Start updateLater(final String repository, final Path root) {
		final RepositoryIndex index = index(repository);
		final RepositoryIndex.Start start = index.beginUpdate();
		if (start == RepositoryIndex.Start.STARTED) {
			background.execute(() -> {
				try {
					index.runUpdate(root);
				} catch (final RuntimeException e) {
					log.error("Search index of {} could not be updated: {}", repository, e.toString());
				}
			});
		}
		return start;
	}

	/** Ends what runs in the background, then closes the indexes. */
	public void close() {
		background.shutdownNow();
		try {
			background.awaitTermination(10, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		indexes.values().forEach(RepositoryIndex::close);
		indexes.clear();
	}
}
