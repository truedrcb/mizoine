package com.gratchev.mizoine.search;

/** The state of a repository's search index (step 56, D59). */
public enum IndexStatus implements ApiCode {
	/** No index yet: first start, a new repository. */
	NOT_INDEXED,
	/** Made by another Lucene major or analysis version, or cannot be read: must be built anew. */
	NEEDS_REINDEXING,
	/** Being built anew. */
	REINDEXING,
	/** Current format; whether it covers the latest changes by hand, its dates tell. */
	INDEXED,
	/** The last reindexing failed. */
	FAILED
}
