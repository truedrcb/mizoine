package com.gratchev.mizoine.users;

import java.util.Optional;

/** The users the users file names: whether one exists, and their email address (step 47, D38). */
public interface KnownUsers {

	/** @return whether the users file names this user (now: a removed user is gone) */
	boolean exists(String username);

	/** @return the user's email; empty if none is set, or the user is unknown */
	Optional<String> email(String username);
}
