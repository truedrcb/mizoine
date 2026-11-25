package com.gratchev.mizoine.git;

/** What a Git action (steps 46–48) ended with: exactly one of these. */
public sealed interface GitOutcome {

	/** @param message what was done, for the page, e.g. "Staged 2 files." */
	record Done(String message) implements GitOutcome {
	}

	/** Not done: Git refused, or there was nothing to do. @param message why, often Git's own words */
	record Refused(String message) implements GitOutcome {
	}
}
