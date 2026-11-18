package com.gratchev.mizoine.repositories;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A repository that is configured wrongly (D8).
 *
 * @param repository repository name
 * @param error always "repository not available"
 * @param file the repository file to fix; only for users with access to the whole repository
 * @param problems what is wrong; only for users with access to the whole repository
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryProblem(String repository, String error, String file, List<String> problems) {

	static final String NOT_AVAILABLE = "repository not available";
}
