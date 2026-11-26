package com.gratchev.mizoine.search;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The tags in use in a repository (step 65, D71), among the folders the user may read: so that people and agents
 * keep to the tags there are.
 */
@Schema(description = "The tags in use among the folders you may read, sorted, with how often")
public record Vocabulary(String repository, List<TagCount> tags, @Schema(description = "The state of the search index") IndexInfo index) {
}
