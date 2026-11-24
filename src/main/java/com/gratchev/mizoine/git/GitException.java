package com.gratchev.mizoine.git;

/** Git could not be run, or gave what Mizoine does not understand. The message may be Git's own. */
public class GitException extends RuntimeException {

	public GitException(final String message) {
		super(message);
	}

	public GitException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
