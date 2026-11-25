package com.gratchev.mizoine.users;

import java.util.Optional;

/** @param email optional; the author address of the user's Git commits (step 47, D38) */
public record UserEntry(String username, String passwordHash, Optional<String> email) {

	public UserEntry(final String username, final String passwordHash) {
		this(username, passwordHash, Optional.empty());
	}
}
