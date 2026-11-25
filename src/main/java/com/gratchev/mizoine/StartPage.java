package com.gratchev.mizoine;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The start page: the logged-in user and the repositories they can access, sorted by name.
 *
 * @param user username of the logged-in user
 * @param repositories empty if the user has no access to any repository
 */
@Schema(description = "The logged-in user and the repositories they can access")
public record StartPage(
		@Schema(description = "Username of the logged-in user") String user,
		@Schema(description = "Sorted by name; empty without access to any repository") List<Repository> repositories) {

	/**
	 * @param name repository name, also its URL: {@code /<name>/}
	 * @param available {@code false} if the repository is configured wrongly
	 * @param problems what is wrong; only for users with access to the whole repository (D8), otherwise left out
	 */
	public record Repository(
			@Schema(description = "Repository name, also its URL: /{name}/") String name,
			@Schema(description = "false if the repository is configured wrongly") boolean available,
			@Schema(description = "What is wrong; only for users with access to the whole repository, otherwise left out")
			@JsonInclude(JsonInclude.Include.NON_NULL) List<String> problems) {
	}
}
