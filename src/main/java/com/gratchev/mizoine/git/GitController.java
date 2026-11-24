package com.gratchev.mizoine.git;

import java.security.Principal;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.gratchev.mizoine.ApiTexts;
import com.gratchev.mizoine.repositories.RepositoryFiles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
	private static final String PROBLEM = "Repository configured wrongly (e.g. missing root folder)";

	private final RepositoryFiles files;

	GitController(final RepositoryFiles files) {
		this.files = files;
	}

	@GetMapping(value = "/{repository}/.git/", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(summary = "Git page of a repository",
			description = "The page for Git in the browser: status, stage and unstage, commit, pull and push (steps "
					+ "43–48). Only for users with Git access: grant '.git' or '/' in the repository file.")
	@ApiResponse(responseCode = "200", description = "The Git page", content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
	@ApiResponse(responseCode = "302", description = ApiTexts.LOGIN_REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = @Content)
	String page(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal, final Model model) {
		files.gitRoot(principal.getName(), repository);
		model.addAttribute(REPOSITORY, repository);
		return "git";
	}

	@GetMapping(value = "/{repository}/.git", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(summary = "Git page without trailing slash",
			description = "Redirects to /{repository}/.git/ for users with Git access (D5).")
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
