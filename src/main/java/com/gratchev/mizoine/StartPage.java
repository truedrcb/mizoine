package com.gratchev.mizoine;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The start page: the logged-in user and the repositories they can access, sorted by name.
 *
 * @param user username of the logged-in user
 * @param repositories empty if the user has no access to any repository
 */
public record StartPage(String user, List<Repository> repositories) {

	/**
	 * @param name repository name, also its URL: {@code /<name>/}
	 * @param available {@code false} if the repository is configured wrongly
	 * @param problems what is wrong; only for users with access to the whole repository (D8), otherwise left out
	 */
	public record Repository(String name, boolean available,
			@JsonInclude(JsonInclude.Include.NON_NULL) List<String> problems) {
	}
}
