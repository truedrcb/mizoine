package com.gratchev.mizoine;

import java.security.Principal;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.gratchev.mizoine.repositories.RepositoryAccess;
import com.gratchev.mizoine.repositories.RepositoryConfig;
import com.gratchev.mizoine.repositories.RepositoryRegistry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code /}: greets the user and lists the repositories they can access. HTML unless JSON is asked for (D6).
 * Unreadable repository files are not listed (permissions unknown, D7).
 */
@Controller
@Tag(name = "Start page")
class StartPageController {

	// both methods share one operation in the API description (same URL): springdoc merges them,
	// so they use the same texts
	private static final String SUMMARY = "Start page: the user's repositories";
	private static final String DESCRIPTION = "Greets the logged-in user and lists the repositories they can access, "
			+ "sorted by name: an HTML page with links (default), or JSON when asked for. Without repositories "
			+ "the page says \"No accessible repositories configured for you.\" and the JSON list is empty.";
	private static final String OK = "The start page (HTML) or the user's repositories (JSON)";
	private static final String NOT_LOGGED_IN = "Not logged in (non-browser clients): HTTP Basic challenge";

	private final RepositoryRegistry registry;

	StartPageController(final RepositoryRegistry registry) {
		this.registry = registry;
	}

	@GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(summary = SUMMARY, description = DESCRIPTION)
	@ApiResponse(responseCode = "200", description = OK,
			content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
	@ApiResponse(responseCode = "302", description = "Browser not logged in: redirect to the login page",
			content = @Content)
	@ApiResponse(responseCode = "401", description = NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "406", description = "Neither text/html nor application/json accepted",
			content = @Content)
	String startPage(final Principal principal, final Model model) {
		model.addAttribute("page", page(principal));
		return "start";
	}

	@GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@Operation(summary = SUMMARY, description = DESCRIPTION)
	@ApiResponse(responseCode = "200", description = OK,
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = StartPage.class)))
	@ApiResponse(responseCode = "401", description = NOT_LOGGED_IN, content = @Content)
	StartPage startPageJson(final Principal principal) {
		return page(principal);
	}

	private StartPage page(final Principal principal) {
		final String user = principal.getName();
		return new StartPage(user, registry.all().stream()
				.filter(config -> RepositoryAccess.of(config, user).hasAccess())
				.map(config -> summary(config, RepositoryAccess.of(config, user)))
				.toList());
	}

	private static StartPage.Repository summary(final RepositoryConfig config, final RepositoryAccess access) {
		final boolean available = config.problems().isEmpty();
		return new StartPage.Repository(config.name(), available,
				!available && access.isWholeRepository() ? config.problems() : null);
	}
}
