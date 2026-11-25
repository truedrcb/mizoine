package com.gratchev.mizoine.search;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The search indexes of all repositories (D60), each opened when first needed, in
 * {@code mizoine.search.index-dir/<repository>/}. Repository names are safe as folder names (step 14).
 */
public class SearchIndexes {

	private final Path dir;
	private final SearchAnalysis analysis;
	private final Map<String, RepositoryIndex> indexes = new ConcurrentHashMap<>();

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

	public void close() {
		indexes.values().forEach(RepositoryIndex::close);
		indexes.clear();
	}
}
