package com.gratchev.mizoine.git;

/** Git did not finish in time (e.g. a remote that does not answer) and was ended. */
public class GitTimeoutException extends GitException {

	public GitTimeoutException(final String message) {
		super(message);
	}
}
