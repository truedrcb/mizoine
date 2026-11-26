package com.gratchev.mizoine.search;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.gratchev.mizoine.ApiTexts;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Step 65, D71: the tags in use in a repository, at the technical address {@code /{repository}/.tags/}, from the
 * search index. HTML and JSON on one URL are one operation in the API description (see {@link ApiTexts}).
 */
@Controller
@SearchEnabled
@Tag(name = "Folder tags")
class TagsController {

	private static final String REPOSITORY = "repository";
	private static final String NO_INDEX = "There is no search index to answer from yet.";

	private final Search search;

	TagsController(final Search search) {
		this.search = search;
	}

	@GetMapping(value = "/{repository}/.tags/", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "tagsInUse", summary = "The tags in use in a repository",
			description = "Every tag in use among the folders the user may read, in lower case, sorted, with on how "
					+ "many folders; key:value tags such as statuses grouped by their key on the page, each linked to "
					+ "its search. From the search index: as current as the index.")
	@ApiResponse(responseCode = "200", description = "The tags: an HTML page, or JSON", content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Vocabulary.class))})
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = "Unknown repository, or one the user has no grant in", content = @Content)
	@ApiResponse(responseCode = "503", description = "No search index to answer from yet; the state says why", content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = SearchController.Unavailable.class))})
	String page(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal, final Model model, final HttpServletResponse response) {
		model.addAttribute(REPOSITORY, repository);
		try {
			final Vocabulary vocabulary = search.vocabulary(principal.getName(), repository);
			model.addAttribute("groups", groups(vocabulary.tags()));
		} catch (final NotSearchableException e) {
			response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
			model.addAttribute("message", NO_INDEX);
			model.addAttribute("indexes", e.indexes());
		}
		return "tags";
	}

	@GetMapping(value = "/{repository}/.tags/", produces = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // documented together with the HTML variant on page(...)
	ResponseEntity<Object> json(@PathVariable(REPOSITORY) final String repository, final Principal principal) {
		try {
			return json(HttpStatus.OK, search.vocabulary(principal.getName(), repository));
		} catch (final NotSearchableException e) {
			return json(HttpStatus.SERVICE_UNAVAILABLE, new SearchController.Unavailable(NO_INDEX, e.indexes()));
		}
	}

	/** Plain tags first (under ""), then the key:value tags by key, e.g. all statuses together. */
	static Map<String, List<TagCount>> groups(final List<TagCount> tags) {
		final Map<String, List<TagCount>> groups = new LinkedHashMap<>();
		groups.put("", new ArrayList<>());
		tags.forEach(tag -> groups.computeIfAbsent(com.gratchev.mizoine.tags.Tag.of(tag.tag()).key().orElse(""),
				key -> new ArrayList<>()).add(tag));
		return groups;
	}

	private static ResponseEntity<Object> json(final HttpStatus status, final Object body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
