package com.gratchev.mizoine.users;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a users file problem at startup into a short "APPLICATION FAILED TO START"
 * report instead of a long bean creation stack trace.
 */
class UsersFileFailureAnalyzer extends AbstractFailureAnalyzer<UsersFileException> {

	@Override
	protected FailureAnalysis analyze(final Throwable rootFailure, final UsersFileException cause) {
		return new FailureAnalysis(cause.getMessage(),
				"Fix the users file, or point mizoine.users-file to a valid one. See users.example.yaml for the format.",
				cause);
	}
}
