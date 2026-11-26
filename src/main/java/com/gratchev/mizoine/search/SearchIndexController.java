package com.gratchev.mizoine.search;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import com.gratchev.mizoine.ApiTexts;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Step 60: the state of the search indexes with their dates, and reindexing and updates on request. Nothing runs on
 * its own (D61): the user compares the dates and asks. HTML and JSON on one URL are one operation in the API
 * description (see {@link ApiTexts}).
 */
@Controller
@SearchEnabled
@Tag(name = "Search")
class SearchIndexController {

	private static final String REPOSITORY = "repository";
	private static final String STATE = "The state of a repository's search index, and the dates to judge whether it "
			+ "is current: when it was built and last changed, the newest file in the folders the user may read, and the "
			+ "last commit (with Git access). A newest file younger than the last change is not in the index yet: "
			+ "update it. The number of files and what could not be read only with access to the whole repository.";
	private static final String NOT_FOUND = "Unknown repository, or one the user has no grant in";
	private static final String NOT_WHOLE = "Unknown repository, or the user has no access to the whole repository";
	private static final String PROBLEM = "The repository is configured wrongly, or the index could not be read";
	private static final String JSON_BODY = " Send Content-Type: application/json; the body may be empty.";

	private final IndexReports reports;

	SearchIndexController(final IndexReports reports) {
		this.reports = reports;
	}

	/** Why an update was not started. */
	record Refusal(@Schema(description = "Why") String message, IndexReport index) {
	}

	@GetMapping(value = "/{repository}/.search/index", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "searchIndexState", summary = "The state of a repository's search index", description = STATE)
	@ApiResponse(responseCode = "200", description = "The state: an HTML page, or JSON", content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndexReport.class))})
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	String statePage(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal, final Model model) {
		model.addAttribute("reports", List.of(reports.report(principal.getName(), repository)));
		model.addAttribute(REPOSITORY, repository);
		return "search-index";
	}

	@GetMapping(value = "/{repository}/.search/index", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@Hidden // documented together with the HTML variant on statePage(...)
	IndexReport state(@PathVariable(REPOSITORY) final String repository, final Principal principal) {
		return reports.report(principal.getName(), repository);
	}

	@GetMapping(value = "/.search/index", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "searchIndexStates", summary = "The states of all search indexes",
			description = "The state and dates of the search index of every repository the user may search, by name; "
					+ "e.g. to see after installing or upgrading which repositories need a reindexing.")
	@ApiResponse(responseCode = "200", description = "The states: an HTML page, or JSON", content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					array = @ArraySchema(schema = @Schema(implementation = IndexReport.class)))})
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	String statesPage(final Principal principal, final Model model) {
		model.addAttribute("reports", reports.reports(principal.getName()));
		return "search-index";
	}

	@GetMapping(value = "/.search/index", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@Hidden // documented together with the HTML variant on statesPage(...)
	List<IndexReport> states(final Principal principal) {
		return reports.reports(principal.getName());
	}

	// no "produces": a client without an Accept header must reach it too, not the file move on the same pattern
	@PostMapping(value = "/{repository}/.search/reindex", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "searchReindex", summary = "Build a repository's search index anew",
			description = "Builds the search index anew from all the repository's files, in the background, beside the "
					+ "old index, which answers searches meanwhile. For a first index, after an upgrade, for a damaged "
					+ "index, or after many changes by hand. Requested again while it runs, the running one counts. "
					+ "Only with access to the whole repository." + JSON_BODY)
	@ApiResponse(responseCode = "202", description = "Started, or running already; the state",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndexReport.class)))
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_WHOLE, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	ResponseEntity<Object> reindex(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal) {
		return json(HttpStatus.ACCEPTED, reports.reindex(principal.getName(), repository));
	}

	// no "produces": see reindex(...)
	@PostMapping(value = "/{repository}/.search/update", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "searchUpdate", summary = "Update a repository's search index",
			description = "Indexes only what changed since the index's own record, in the background: new, changed "
					+ "(by size and modification time) and deleted files and folders. Quicker than a reindexing; for "
					+ "changes by hand or a pull. Only on an indexed index; requested again while it runs, the running "
					+ "one counts. Only with access to the whole repository." + JSON_BODY)
	@ApiResponse(responseCode = "202", description = "Started, or running already; the state",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IndexReport.class)))
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_WHOLE, content = @Content)
	@ApiResponse(responseCode = "409", description = "The index needs a reindexing first (not indexed, another format, "
			+ "damaged, failed, or reindexing)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	ResponseEntity<Object> update(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal) {
		final IndexReports.Updated updated = reports.update(principal.getName(), repository);
		return updated.start() == RepositoryIndex.Start.NEEDS_REINDEXING
				? json(HttpStatus.CONFLICT, new Refusal("The index needs a reindexing first.", updated.report()))
				: json(HttpStatus.ACCEPTED, updated.report());
	}

	/**
	 * The page's buttons: the same reindexing or update, then back to the page (Post/Redirect/Get, 303), which says
	 * what happened. {@code back=all}: to the page of all repositories, else to the repository's.
	 */
	@PostMapping(value = "/{repository}/.search/{action:reindex|update}",
			consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@Hidden // the browser's way to the operations documented above
	RedirectView form(@PathVariable(REPOSITORY) final String repository, @PathVariable("action") final String action,
			@RequestParam(name = "back", required = false) final String back, final Principal principal,
			final RedirectAttributes redirect) {
		final String user = principal.getName();
		final String done;
		if ("reindex".equals(action)) {
			reports.reindex(user, repository);
			done = "Reindexing started.";
		} else {
			done = reports.update(user, repository).start() == RepositoryIndex.Start.NEEDS_REINDEXING
					? "The index needs a reindexing first." : "Update started.";
		}
		final boolean all = "all".equals(back);
		redirect.addFlashAttribute("outcome", all ? repository + ": " + done.substring(0, 1).toLowerCase(Locale.ROOT)
				+ done.substring(1) : done);
		final RedirectView page = new RedirectView(all ? "/.search/index" : "/" + repository + "/.search/index");
		page.setStatusCode(HttpStatus.SEE_OTHER);
		return page;
	}

	/** Always JSON, also without an Accept header (the pages' default is HTML, D6). */
	private static ResponseEntity<Object> json(final HttpStatus status, final Object body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
