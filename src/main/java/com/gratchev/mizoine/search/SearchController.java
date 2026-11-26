package com.gratchev.mizoine.search;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.gratchev.mizoine.ApiTexts;
import com.gratchev.mizoine.tags.Tag;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Step 58: the search page and its JSON, for one repository and for all the user may read in. The search terms are
 * part of the URL, so a search can be bookmarked and opened in several windows (BACKLOG §3). HTML and JSON on one
 * URL are one operation in the API description (see {@link ApiTexts}).
 */
@Controller
@SearchEnabled
@io.swagger.v3.oas.annotations.tags.Tag(name = "Search")
class SearchController {

	private static final String REPOSITORY = "repository";
	private static final String DESCRIPTION = "Finds files and folders by their names, and text files by their content, "
			+ "among those the user may read; best first. Words are found in every spelling of a letter (German ä = ae "
			+ "= a, ß = ss, accents, French elision, Russian ё = е) and in their word forms (English, German, French, "
			+ "Russian by default). A query: words, \"a phrase\", -leftout, prefix*, #tag (a folder's tag); at most 200 characters. All words "
			+ "first; if nothing is found, any of them; then words of a similar spelling: strictness says which. "
			+ "Email addresses and file names are found whole and by their parts. An HTML page (default; without q "
			+ "the search box) or JSON when asked for. Every answer gives the state of each searched repository's index.";
	private static final String OK = "The results: an HTML page, or JSON";
	private static final String BAD = "q is missing, empty, too long or only leaves out; or limit or offset is wrong; "
			+ "the message says which";
	private static final String NO_INDEX = "None of the searched repositories has a search index to answer from; "
			+ "the states say why (e.g. not-indexed: to be built)";
	private static final String NOT_FOUND = "Unknown repository, or one the user has no grant in";
	private static final String PROBLEM = "The repository is configured wrongly, or the index could not be read";
	private static final String Q = "What to find: words, \"a phrase\", -leftout, prefix*, #tag";
	private static final String LIMIT = "How many results, 1 to 100";
	private static final String OFFSET = "From which result, from 0";
	private static final String TAG = "Only folders with this tag (repeatable: all of them), at every level of strictness";

	private final Search search;

	SearchController(final Search search) {
		this.search = search;
	}

	/** Why the search was not done. */
	record Refusal(@Schema(description = "Why, for the user") String message) {
	}

	/** No index to search. */
	record Unavailable(String message, List<IndexInfo> indexes) {
	}

	@GetMapping(value = "/{repository}/.search", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "searchRepository", summary = "Search in a repository", description = DESCRIPTION)
	@ApiResponse(responseCode = "200", description = OK, content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SearchResults.class))})
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "400", description = BAD, content = {@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class))})
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	@ApiResponse(responseCode = "503", description = NO_INDEX, content = {@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Unavailable.class))})
	String repositoryPage(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@RequestParam(name = "q", required = false) @Parameter(description = Q) final String q,
			@RequestParam(name = "limit", defaultValue = "20") @Parameter(description = LIMIT) final int limit,
			@RequestParam(name = "offset", defaultValue = "0") @Parameter(description = OFFSET) final int offset,
			@RequestParam(name = "tag", required = false) @Parameter(description = TAG) final List<String> tags,
			final Principal principal, final Model model, final HttpServletResponse response) {
		return page(principal, Optional.of(repository), new Asked(q, limit, offset, tags), model, response);
	}

	@GetMapping(value = "/{repository}/.search", produces = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // documented together with the HTML variant on repositoryPage(...)
	ResponseEntity<Object> repositoryJson(@PathVariable(REPOSITORY) final String repository,
			@RequestParam(name = "q", required = false) final String q,
			@RequestParam(name = "limit", defaultValue = "20") final int limit,
			@RequestParam(name = "offset", defaultValue = "0") final int offset,
			@RequestParam(name = "tag", required = false) final List<String> tags,
			final Principal principal) {
		return json(principal, Optional.of(repository), new Asked(q, limit, offset, tags));
	}

	@GetMapping(value = "/.search", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "searchAllRepositories", summary = "Search in all repositories",
			description = DESCRIPTION + " Searches every repository the user may read in, results of all in one order.")
	@ApiResponse(responseCode = "200", description = OK, content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SearchResults.class))})
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "400", description = BAD, content = {@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class))})
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	@ApiResponse(responseCode = "503", description = NO_INDEX, content = {@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Unavailable.class))})
	String allPage(@RequestParam(name = "q", required = false) @Parameter(description = Q) final String q,
			@RequestParam(name = "limit", defaultValue = "20") @Parameter(description = LIMIT) final int limit,
			@RequestParam(name = "offset", defaultValue = "0") @Parameter(description = OFFSET) final int offset,
			@RequestParam(name = "tag", required = false) @Parameter(description = TAG) final List<String> tags,
			final Principal principal, final Model model, final HttpServletResponse response) {
		return page(principal, Optional.empty(), new Asked(q, limit, offset, tags), model, response);
	}

	@GetMapping(value = "/.search", produces = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // documented together with the HTML variant on allPage(...)
	ResponseEntity<Object> allJson(@RequestParam(name = "q", required = false) final String q,
			@RequestParam(name = "limit", defaultValue = "20") final int limit,
			@RequestParam(name = "offset", defaultValue = "0") final int offset,
			@RequestParam(name = "tag", required = false) final List<String> tags,
			final Principal principal) {
		return json(principal, Optional.empty(), new Asked(q, limit, offset, tags));
	}

	/** The request's search parameters, as given. */
	private record Asked(String q, int limit, int offset, List<String> tags) {

		/** @throws IllegalArgumentException with a message for the user */
		SearchResults search(final Search search, final String user, final Optional<String> repository) {
			return search.search(user, repository, SearchQuery.parse(q), new Page(offset, limit),
					tags == null ? List.of() : tags.stream().map(Tag::of).toList());
		}
	}

	/** Without a query, the page is the search box. */
	private String page(final Principal principal, final Optional<String> repository, final Asked asked,
			final Model model, final HttpServletResponse response) {
		final String q = asked.q();
		model.addAttribute(REPOSITORY, repository.orElse(null));
		model.addAttribute("query", q == null ? "" : q);
		model.addAttribute("searchUrl", repository.map(name -> "/" + name + "/.search").orElse("/.search"));
		if (q == null) {
			return "search";
		}
		try {
			final SearchResults results = asked.search(search, principal.getName(), repository);
			model.addAttribute("results", results);
			model.addAttribute("indexes", results.indexes());
		} catch (final IllegalArgumentException e) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			model.addAttribute("message", e.getMessage());
		} catch (final NotSearchableException e) {
			response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
			model.addAttribute("message", "There is no search index to answer from yet.");
			model.addAttribute("indexes", e.indexes());
		}
		return "search";
	}

	/** Always JSON, also without an Accept header (the pages' default is HTML, D6). */
	private ResponseEntity<Object> json(final Principal principal, final Optional<String> repository, final Asked asked) {
		try {
			return json(HttpStatus.OK, asked.search(search, principal.getName(), repository));
		} catch (final IllegalArgumentException e) {
			return json(HttpStatus.BAD_REQUEST, new Refusal(e.getMessage()));
		} catch (final NotSearchableException e) {
			return json(HttpStatus.SERVICE_UNAVAILABLE,
					new Unavailable("There is no search index to answer from yet.", e.indexes()));
		}
	}

	private static ResponseEntity<Object> json(final HttpStatus status, final Object body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
