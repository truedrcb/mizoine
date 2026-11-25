package com.gratchev.mizoine.git;

import java.security.Principal;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.gratchev.mizoine.ApiTexts;
import com.gratchev.mizoine.repositories.RepositoryFiles;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Git in the browser (steps 43–48): {@code /{repository}/.git/} is a reserved address, never the {@code .git}
 * folder, which stays unreachable as files (D3). For users with Git access (grant {@code .git} or {@code /}, D33);
 * for everyone else 404, as for anything not visible (D2).
 */
@Controller
@Tag(name = "Git")
class GitController {

	private static final String REPOSITORY = "repository";
	private static final String NOT_FOUND = "Unknown repository, or no Git access to it: deliberately the same answer";
	private static final String PROBLEM = "Repository configured wrongly (e.g. missing root folder); or Git failed";
	private static final String NOT_A_REPOSITORY = "The repository's folder is not the top of a Git working tree";

	private final RepositoryFiles files;
	private final GitCommands git;

	GitController(final RepositoryFiles files, final GitCommands git) {
		this.files = files;
		this.git = git;
	}

	@GetMapping(value = "/{repository}/.git/", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "gitStatus", summary = "Git status of a repository",
			description = "The Git status: branch, upstream with ahead and behind, the staged and the not "
					+ "staged changes, conflicts; an HTML page (default) or JSON when asked for. File names link to "
					+ "their pages where the user may read them. Only for users with Git access: grant '.git' or '/' "
					+ "in the repository file; the status covers the whole repository. A repository whose folder is not "
					+ "the top of a Git working tree has no status: the page says so, JSON gives 409.")
	@ApiResponse(responseCode = "200", description = "The Git page, or the status as JSON", content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GitStatus.class))})
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = NOT_A_REPOSITORY + " (JSON only)", content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	String page(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal, final Model model) {
		final String user = principal.getName();
		final Optional<GitStatus> status = git.status(files.gitRoot(user, repository));
		model.addAttribute(REPOSITORY, repository);
		model.addAttribute("git", status.map(found -> GitStatusView.of(found, files.fileLinks(user, repository))).orElse(null));
		return "git";
	}

	@GetMapping(value = "/{repository}/.git/", produces = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // documented together with the HTML variant on page(...)
	@ResponseBody
	GitStatus status(@PathVariable(REPOSITORY) final String repository, final Principal principal) {
		return git.status(files.gitRoot(principal.getName(), repository))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, NOT_A_REPOSITORY));
	}

	@GetMapping(value = "/{repository}/.git", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "gitRedirect", summary = "Git page without trailing slash",
			description = "Redirects to /{repository}/.git/ for users with Git access (folder URLs end with '/').")
	@ApiResponse(responseCode = "302", description = "Redirect to /{repository}/.git/; or " + ApiTexts.LOGIN_REDIRECT,
			content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	String withoutTrailingSlash(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal) {
		files.gitRoot(principal.getName(), repository);
		return "redirect:/" + repository + "/.git/";
	}
}
