package com.gratchev.mizoine.repositories;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

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
 * {@code /{repository}/…/}: the subfolders and files of a repository folder, as HTML (default) or JSON (D6).
 * <p>
 * Unknown repository, unreadable repository file, no grant, a folder the user may not see and a folder
 * that does not exist all give the same 404 (D2, D7). A readable file with problems gives 500; the details
 * only to users with access to the whole repository (D8). Paths are checked by {@link RepositoryPaths}
 * (no way out of the root, no hidden folders) and {@link RepositoryAccess} (grants).
 */
@Controller
@Tag(name = "Repositories")
class RepositoryController {

	private static final String REPOSITORY = "repository";
	private static final String PATH = "path";

	// HTML and JSON on one URL: documented once, see ApiTexts
	private static final String SUMMARY = "Folder of a repository";
	private static final String DESCRIPTION = "Lists the subfolders and files of a folder that the user may see, "
			+ "sorted by name: an HTML page (default; folders as links, files as names), or JSON when asked for. "
			+ "Folder URLs end with '/'; without it the server redirects. Hidden folders and files (starting with '.' "
			+ "or containing '%') are not listed; above a granted folder only the way down is shown, no files.";
	private static final String OK = "The folder page (HTML) or its subfolders and files (JSON)";
	private static final String REDIRECT = "Folder URL without trailing slash: redirect to the same URL with '/' "
			+ "(only for folders the user may see); or " + ApiTexts.LOGIN_REDIRECT;
	private static final String NOT_FOUND = "Unknown repository or folder, or no access to it — deliberately "
			+ "the same answer";
	private static final String PROBLEM = "Repository configured wrongly (e.g. missing root folder); the file to "
			+ "fix and the problems only for users with access to the whole repository";

	/** What a folder URL leads to: a listing, a redirect or a problem page. */
	private record Target(RepositoryConfig config, RepositoryAccess access, List<String> segments,
			Optional<String> redirect) {
	}

	private final RepositoryRegistry registry;

	RepositoryController(final RepositoryRegistry registry) {
		this.registry = registry;
	}

	@GetMapping("/{repository}")
	@Operation(summary = "Repository without trailing slash",
			description = "Redirects to the same URL with a trailing slash (folder URLs end with '/'). "
					+ "Done for every valid name, so it reveals nothing about which repositories exist.")
	@ApiResponse(responseCode = "302", description = "Redirect to /{repository}/; or " + ApiTexts.LOGIN_REDIRECT,
			content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = "Not a valid repository name", content = @Content)
	String withoutTrailingSlash(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository) {
		if (!RepositoryNames.isValid(repository)) {
			throw notFound();
		}
		return "redirect:/" + repository + "/";
	}

	// returns a view name, not a ModelAndView: springdoc leaves methods returning ModelAndView out of the description
	@GetMapping(value = "/{repository}/{*path}", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(summary = SUMMARY, description = DESCRIPTION)
	@ApiResponse(responseCode = "200", description = OK, content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FolderListing.class))})
	@ApiResponse(responseCode = "302", description = REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "406", description = ApiTexts.NOT_ACCEPTABLE, content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RepositoryProblem.class))})
	String folder(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@PathVariable(PATH) @Parameter(description = "Folder below the repository root, ending with '/'")
			final String path, final Principal principal, final Model model, final HttpServletResponse response) {
		final Target target = target(repository, path, principal);
		model.addAttribute(REPOSITORY, target.config().name());
		if (!target.config().problems().isEmpty()) {
			final RepositoryProblem problem = problem(target);
			model.addAttribute("file", problem.file());
			model.addAttribute("problems", problem.problems());
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			return "repository-problem";
		}
		if (target.redirect().isPresent()) {
			// fully encoded, so names with '{' cannot be taken for URI template variables
			return "redirect:" + target.redirect().get();
		}
		final FolderListing listing = listing(target);
		model.addAttribute("listing", listing);
		model.addAttribute("links", listing.folders().stream()
				.map(name -> new Link(name, UriUtils.encodePathSegment(name, StandardCharsets.UTF_8) + "/"))
				.toList());
		model.addAttribute("root", target.segments().isEmpty());
		return "folder";
	}

	@GetMapping(value = "/{repository}/{*path}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // documented together with the HTML variant on folder(...)
	ResponseEntity<Object> folderJson(@PathVariable(REPOSITORY) final String repository,
			@PathVariable(PATH) final String path, final Principal principal) {
		final Target target = target(repository, path, principal);
		if (!target.config().problems().isEmpty()) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(target));
		}
		if (target.redirect().isPresent()) {
			return ResponseEntity.status(HttpStatus.FOUND).header("Location", target.redirect().get()).build();
		}
		return ResponseEntity.ok(listing(target));
	}

	/** A subfolder link in the HTML page: the name as text, the URL-encoded name as relative link. */
	record Link(String name, String href) {
	}

	private Target target(final String repository, final String path, final Principal principal) {
		final RepositoryConfig config = registry.find(repository).orElseThrow(RepositoryController::notFound);
		final RepositoryAccess access = RepositoryAccess.of(config, principal.getName());
		if (!access.hasAccess()) {
			throw notFound();
		}
		if (!config.problems().isEmpty()) {
			return new Target(config, access, List.of(), Optional.empty());
		}
		final List<String> segments = segments(path);
		if (!access.canEnter(segments) || RepositoryPaths.resolveFolder(config.root(), segments).isEmpty()) {
			throw notFound();
		}
		final Optional<String> redirect = path.endsWith("/") ? Optional.empty() : Optional.of(url(config.name(), segments));
		return new Target(config, access, segments, redirect);
	}

	/** "/" → [], "/a/b/" → [a, b]; empty segments ("a//b") stay and are rejected by {@link RepositoryPaths}. */
	private static List<String> segments(final String path) {
		final String inner = path.replaceFirst("^/", "").replaceFirst("/$", "");
		return inner.isEmpty() ? List.of() : Arrays.asList(inner.split("/", -1));
	}

	private static String url(final String repository, final List<String> segments) {
		return UriComponentsBuilder.newInstance()
				.pathSegment(repository)
				.pathSegment(segments.toArray(String[]::new))
				.path("/")
				.encode()
				.build()
				.toUriString();
	}

	private static FolderListing listing(final Target target) {
		final Path root = target.config().root();
		final List<String> segments = target.segments();
		final String path = segments.isEmpty() ? "/" : "/" + String.join("/", segments) + "/";
		return new FolderListing(target.config().name(), path,
				target.access().visibleChildren(segments, RepositoryPaths.childFolders(root, segments)),
				target.access().visibleFiles(segments, RepositoryPaths.childFiles(root, segments)));
	}

	private static RepositoryProblem problem(final Target target) {
		final RepositoryConfig config = target.config();
		return target.access().isWholeRepository()
				? new RepositoryProblem(config.name(), RepositoryProblem.NOT_AVAILABLE, config.name() + ".yaml", config.problems())
				: new RepositoryProblem(config.name(), RepositoryProblem.NOT_AVAILABLE, null, null);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}
}
