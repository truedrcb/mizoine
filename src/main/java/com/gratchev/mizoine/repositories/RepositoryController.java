package com.gratchev.mizoine.repositories;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /{repository}/}: a repository the user has access to.
 * <p>
 * Unknown repository, unreadable repository file and "no grant" all give the same 404 (D2, D7).
 * A readable file with problems gives 500; the details only to users with access to the whole repository (D8).
 */
@Controller
@Tag(name = "Repositories")
class RepositoryController {

	private static final String REPOSITORY = "repository";

	private final RepositoryRegistry registry;

	RepositoryController(final RepositoryRegistry registry) {
		this.registry = registry;
	}

	@GetMapping("/{repository}")
	@Operation(summary = "Repository without trailing slash",
			description = "Redirects to the same URL with a trailing slash (folder URLs end with '/'). "
					+ "Done for every valid name, so it reveals nothing about which repositories exist.")
	@ApiResponse(responseCode = "302", description = "Redirect to /{repository}/", content = @Content)
	@ApiResponse(responseCode = "401", description = "Not logged in", content = @Content)
	@ApiResponse(responseCode = "404", description = "Not a valid repository name", content = @Content)
	String withoutTrailingSlash(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository) {
		if (!RepositoryNames.isValid(repository)) {
			throw notFound();
		}
		return "redirect:/" + repository + "/";
	}

	// returns a view name, not a ModelAndView: springdoc leaves methods returning ModelAndView out of the description
	@GetMapping(value = "/{repository}/", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(summary = "Repository",
			description = "Answers 'ok' when the repository exists and the user has access to it.")
	@ApiResponse(responseCode = "200", description = "The repository page",
			content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
	@ApiResponse(responseCode = "302", description = "Browser not logged in: redirect to the login page",
			content = @Content)
	@ApiResponse(responseCode = "401", description = "Not logged in (non-browser clients): HTTP Basic challenge",
			content = @Content)
	@ApiResponse(responseCode = "404",
			description = "Unknown repository, or no access to it — deliberately the same answer", content = @Content)
	@ApiResponse(responseCode = "406", description = "Only text/html is available", content = @Content)
	@ApiResponse(responseCode = "500",
			description = "Repository configured wrongly (e.g. missing root folder); details only for users "
					+ "with access to the whole repository",
			content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
	String repository(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			final Principal principal, final Model model, final HttpServletResponse response) {
		final RepositoryConfig config = registry.find(repository).orElseThrow(RepositoryController::notFound);
		final RepositoryAccess access = RepositoryAccess.of(config, principal.getName());
		if (!access.hasAccess()) {
			throw notFound();
		}
		model.addAttribute(REPOSITORY, config.name());
		if (!config.problems().isEmpty()) {
			if (access.isWholeRepository()) {
				model.addAttribute("file", config.name() + ".yaml");
				model.addAttribute("problems", config.problems());
			}
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			return "repository-problem";
		}
		return REPOSITORY;
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}
}
