package com.gratchev.mizoine.users;

import java.util.Optional;

/** The email addresses of the users, as the users file gives them (step 47, D38). */
public interface UserEmails {

	/** @return the user's email; empty if none is set, or the user is unknown */
	Optional<String> email(String username);
}
