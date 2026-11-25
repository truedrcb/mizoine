package com.gratchev.mizoine.git;

/** The message of a commit (step 47), checked where it comes in: never empty. Kept as written. */
public record CommitMessage(String text) {

	/** @throws IllegalArgumentException if it is empty or only spaces; the message says so for the page */
	public CommitMessage {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Write a commit message.");
		}
	}
}
