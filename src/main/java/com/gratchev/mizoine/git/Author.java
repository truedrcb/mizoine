package com.gratchev.mizoine.git;

import java.util.Optional;

/**
 * The author of a commit (step 47, D38): the Mizoine user, with the email from the users file, else
 * {@code <username>@mizoine}. The committer stays the server's own Git identity, so the history says both who
 * committed and on which server.
 */
public record Author(String name, String email) {

	public static Author of(final String username, final Optional<String> email) {
		return new Author(clean(username), clean(email.orElse(username + "@mizoine")));
	}

	/** @return as Git's {@code --author} takes it: {@code Name <email>} */
	public String git() {
		return name + " <" + email + ">";
	}

	/** '<', '>' and line breaks would end the name or the email in Git's form. */
	private static String clean(final String text) {
		return text.replaceAll("[<>\\r\\n]", "").strip();
	}
}
