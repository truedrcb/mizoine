package com.gratchev.mizoine.users;

public class UsersFileException extends RuntimeException {

	public UsersFileException(final String message) {
		super(message);
	}

	public UsersFileException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
