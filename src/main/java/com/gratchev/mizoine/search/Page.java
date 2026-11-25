package com.gratchev.mizoine.search;

/**
 * Which results to show (step 57): {@code limit} from {@code offset}.
 *
 * @param offset from 0
 * @param limit 1 to {@link #MAX_LIMIT}
 */
public record Page(int offset, int limit) {

	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 100;
	public static final Page FIRST = new Page(0, DEFAULT_LIMIT);

	/** @throws IllegalArgumentException with a message for the user */
	public Page {
		if (offset < 0) {
			throw new IllegalArgumentException("offset must be 0 or more.");
		}
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit must be from 1 to " + MAX_LIMIT + ".");
		}
	}
}
