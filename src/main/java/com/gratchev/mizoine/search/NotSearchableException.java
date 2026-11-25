package com.gratchev.mizoine.search;

import java.util.List;

/** None of the searched repositories has an index to search (D59): an answer of "nothing found" would be wrong. */
public final class NotSearchableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient List<IndexInfo> indexes;

	NotSearchableException(final List<IndexInfo> indexes) {
		super("No search index to search: " + indexes);
		this.indexes = List.copyOf(indexes);
	}

	public List<IndexInfo> indexes() {
		return indexes;
	}
}
