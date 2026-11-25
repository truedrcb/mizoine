package com.gratchev.mizoine.repositories;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Repository names come from file names ({@code notes.yaml}) and appear in URLs ({@code /notes/}).
 * Allowed: letters, digits, {@code .}, {@code _}, {@code -}; not starting with {@code .};
 * not a name the application uses itself ({@code mcp} and {@code oauth2} for agents and chat apps). Case-sensitive.
 */
public final class RepositoryNames {

	private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9._-]*$");
	private static final Set<String> RESERVED = Set.of("login", "logout", "error", "default-ui.css", "favicon.ico", "ui", "mcp",
			"oauth2");

	private RepositoryNames() {
	}

	/** @return the names the application uses itself, sorted, for messages */
	static String reserved() {
		return String.join(", ", RESERVED.stream().sorted().toList());
	}

	public static boolean isValid(final String name) {
		return name != null && VALID.matcher(name).matches() && !RESERVED.contains(name);
	}
}
