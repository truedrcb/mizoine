package com.gratchev.mizoine.repositories;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A repository that is configured wrongly (D8).
 *
 * @param repository repository name
 * @param error always "repository not available"
 * @param file the repository file to fix; only for users with access to the whole repository
 * @param problems what is wrong; only for users with access to the whole repository
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A repository that is configured wrongly")
public record RepositoryProblem(
		@Schema(description = "Repository name") String repository,
		@Schema(description = "Always 'repository not available'") String error,
		@Schema(description = "The repository file to fix; only for users with access to the whole repository") String file,
		@Schema(description = "What is wrong; only for users with access to the whole repository") List<String> problems) {

	static final String NOT_AVAILABLE = "repository not available";
}
