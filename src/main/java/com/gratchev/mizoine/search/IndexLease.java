package com.gratchev.mizoine.search;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;

/** A point-in-time view of an index for one search; closing gives it back, also after the index was replaced. */
public final class IndexLease implements AutoCloseable {

	private final SearcherManager manager;
	private final IndexSearcher searcher;

	IndexLease(final SearcherManager manager) throws IOException {
		this.manager = manager;
		this.searcher = manager.acquire();
	}

	public IndexReader reader() {
		return searcher.getIndexReader();
	}

	@Override
	public void close() throws IOException {
		manager.release(searcher);
	}
}
