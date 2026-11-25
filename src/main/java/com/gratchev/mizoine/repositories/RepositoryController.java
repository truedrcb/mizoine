package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import com.gratchev.mizoine.ApiTexts;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
	private static final String RAW = "raw";
	/** The editor page (step 39, D28): the file's URL path as parameter {@code file}. */
	private static final String EDITOR = "/ui/editor.html?file=";

	// HTML and JSON on one URL: documented once, see ApiTexts
	private static final String SUMMARY = "Folder or file of a repository";
	private static final String DESCRIPTION = "A folder (URL ending with '/') lists the subfolders and files the user "
			+ "may see, sorted by name: an HTML page (default; folders and files as links), or JSON when asked for. "
			+ "Hidden folders and files (starting with '.' or containing '%') are not listed; above a granted folder "
			+ "only the way down is shown, no files. A folder URL without '/' redirects. "
			+ "A file (URL without '/') gives its content, whatever the Accept header asks for: type from the name, "
			+ "ETag and Last-Modified (304 for If-None-Match), Range (206), HEAD. For safety it is sent with "
			+ "'Content-Security-Policy: sandbox', and as download (attachment) unless its type cannot run in a browser. "
			+ "Exception: a Markdown file (valid UTF-8, up to 2 MB) is a rendered HTML page when the request asks "
			+ "explicitly for text/html (a browser), and neither '?raw' nor an OAuth token (Bearer) is given; "
			+ "relative links and pictures work, headings have GitHub-style ids for '#topic'.";
	private static final String OK = "The folder page (HTML) or its subfolders and files (JSON); or the file's content";
	private static final String REDIRECT = "Folder URL without trailing slash: redirect to the same URL with '/' "
			+ "(only for folders the user may see); or " + ApiTexts.LOGIN_REDIRECT;
	private static final String NOT_FOUND = "Unknown repository or folder, or no access to it — deliberately "
			+ "the same answer";
	private static final String PROBLEM = "Repository configured wrongly (e.g. missing root folder); the file to "
			+ "fix and the problems only for users with access to the whole repository";

	/** What a URL below a repository leads to: exactly one of these. */
	private sealed interface Target {

		/** The repository file is readable, but the repository is configured wrongly (D8). */
		record Problem(RepositoryConfig config, RepositoryAccess access) implements Target {
		}

		/** A folder URL without trailing slash (D5). */
		record Redirect(String url) implements Target {
		}

		record Folder(RepositoryConfig config, RepositoryAccess access, List<String> segments) implements Target {
		}

		/** A readable file: its real path, already checked, and where it is in the repository. */
		record File(RepositoryConfig config, List<String> segments, Path path) implements Target {
		}
	}

	private final RepositoryRegistry registry;

	RepositoryController(final RepositoryRegistry registry) {
		this.registry = registry;
	}

	@GetMapping("/{repository}")
	@Operation(operationId = "repositoryRedirect", summary = "Repository without trailing slash",
			description = "Redirects to the same URL with a trailing slash (folder URLs end with '/'). "
					+ "Done for every valid name, so it reveals nothing about which repositories exist.")
	@ApiResponse(responseCode = "302", description = "Redirect to /{repository}/; or " + ApiTexts.LOGIN_REDIRECT,
			content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = "Not a valid repository name", content = @Content)
	String withoutTrailingSlash(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository) {
		if (!RepositoryNames.isValid(repository)) {
			throw GrantedRepository.notFound();
		}
		return "redirect:/" + repository + "/";
	}

	// returns a view name, not a ModelAndView: springdoc leaves methods returning ModelAndView out of the description
	@GetMapping(value = "/{repository}/{*path}", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(summary = SUMMARY, description = DESCRIPTION)
	@Parameter(name = RAW, in = ParameterIn.QUERY, description = "Markdown files: '?raw' gives the file itself, never "
			+ "the rendered page")
	@ApiResponse(responseCode = "200", description = OK, content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FolderListing.class)),
			@Content(mediaType = MediaType.ALL_VALUE)})
	@ApiResponse(responseCode = "206", description = "Part of a file (Range)", content = @Content)
	@ApiResponse(responseCode = "304", description = "File not modified (If-None-Match, If-Modified-Since)",
			content = @Content)
	@ApiResponse(responseCode = "302", description = REDIRECT, content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "406", description = ApiTexts.NOT_ACCEPTABLE + " (folders only)", content = @Content)
	@ApiResponse(responseCode = "500", description = PROBLEM, content = {
			@Content(mediaType = MediaType.TEXT_HTML_VALUE),
			@Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RepositoryProblem.class))})
	Object folder(@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@PathVariable(PATH) @Parameter(description = "Folder (ending with '/') or file below the repository root")
			final String path,
			final Principal principal, final Model model, final HttpServletRequest request,
			final HttpServletResponse response) {
		return switch (target(repository, path, principal)) {
			case Target.Problem problem -> {
				final RepositoryProblem details = problem(problem);
				model.addAttribute(REPOSITORY, problem.config().name());
				model.addAttribute("file", details.file());
				model.addAttribute("problems", details.problems());
				response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
				yield "repository-problem";
			}
			// fully encoded, so names with '{' cannot be taken for URI template variables
			case Target.Redirect redirect -> "redirect:" + redirect.url();
			// '?raw' has no value: present or not
			case Target.File file -> pageOrDownload(file, request.getParameterMap().containsKey(RAW), model, request, response);
			case Target.Folder folder -> {
				final FolderListing listing = listing(folder);
				model.addAttribute("listing", listing);
				model.addAttribute("links", links(listing.folders(), "/"));
				model.addAttribute("fileLinks", links(listing.files(), ""));
				model.addAttribute("root", folder.segments().isEmpty());
				// step 43: the way to the Git page, from the repository's root
				model.addAttribute("git", folder.segments().isEmpty() && folder.access().canUseGit());
				yield "folder";
			}
		};
	}

	@GetMapping(value = "/{repository}/{*path}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // documented together with the HTML variant on folder(...)
	ResponseEntity<?> folderJson(@PathVariable(REPOSITORY) final String repository,
			@PathVariable(PATH) final String path, final Principal principal) {
		return switch (target(repository, path, principal)) {
			case Target.Problem problem -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(problem));
			case Target.Redirect redirect -> ResponseEntity.status(HttpStatus.FOUND).header("Location", redirect.url()).build();
			case Target.File file -> download(file);
			case Target.Folder folder -> ResponseEntity.ok(listing(folder));
		};
	}

	/** Any other {@code Accept} (images, {@code text/markdown}, …): files only; folders are HTML or JSON. */
	@GetMapping("/{repository}/{*path}")
	@Hidden // documented together with the HTML variant on folder(...)
	ResponseEntity<Resource> file(@PathVariable(REPOSITORY) final String repository,
			@PathVariable(PATH) final String path, final Principal principal) {
		if (target(repository, path, principal) instanceof final Target.File file) {
			return download(file);
		}
		throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
	}

	/** A link in the HTML page: the name as text, the URL-encoded name as relative link. */
	record Link(String name, String href) {
	}

	private Target target(final String repository, final String path, final Principal principal) {
		final GrantedRepository granted = GrantedRepository.find(registry, repository, principal.getName())
				.orElseThrow(GrantedRepository::notFound);
		final RepositoryConfig config = granted.config();
		final RepositoryAccess access = granted.access();
		if (granted.hasProblems()) {
			return new Target.Problem(config, access);
		}
		final List<String> segments = RepositoryUrls.segments(path);
		final boolean folderUrl = RepositoryUrls.isFolder(path);
		if (RepositoryFiles.mayEnter(config, access, segments)) {
			return folderUrl ? new Target.Folder(config, access, segments)
					: new Target.Redirect(RepositoryUrls.url(config.name(), segments, true));
		}
		return RepositoryFiles.readable(config, access, segments).filter(file -> !folderUrl)
				.map(file -> new Target.File(config, segments, file))
				.orElseThrow(GrantedRepository::notFound);
	}

	/** Step 38, D22: a Markdown file is a readable page for a person's browser, the raw file for everyone else. */
	private static Object pageOrDownload(final Target.File file, final boolean raw, final Model model,
			final HttpServletRequest request, final HttpServletResponse response) {
		final Optional<String> text = MarkdownPage.isMarkdown(file.path())
				&& MarkdownPage.isWanted(request.getHeader(HttpHeaders.ACCEPT), request.getHeader(HttpHeaders.AUTHORIZATION), raw)
				? TextFile.read(file.path()) : Optional.empty();
		if (text.isEmpty()) {
			return download(file);
		}
		final String name = file.segments().getLast();
		final MarkdownRenderer.Document document = MarkdownRenderer.render(text.get(), name);
		response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT);
		model.addAttribute("document", document);
		model.addAttribute("rawHref", UriUtils.encodePathSegment(name, StandardCharsets.UTF_8) + "?" + RAW);
		model.addAttribute("editHref", EDITOR + UriUtils.encodeQueryParam(
				RepositoryUrls.url(file.config().name(), file.segments(), false), StandardCharsets.UTF_8));
		return "document";
	}

	private static ResponseEntity<Resource> download(final Target.File file) {
		try {
			return FileDownload.of(file.path());
		} catch (final IOException e) {
			// removed or unreadable since it was resolved
			throw GrantedRepository.notFound();
		}
	}

	private static List<Link> links(final List<String> names, final String suffix) {
		return names.stream()
				.map(name -> new Link(name, UriUtils.encodePathSegment(name, StandardCharsets.UTF_8) + suffix))
				.toList();
	}

	private static FolderListing listing(final Target.Folder target) {
		return RepositoryFiles.listing(target.config(), target.access(), target.segments());
	}

	private static RepositoryProblem problem(final Target.Problem target) {
		final RepositoryConfig config = target.config();
		return target.access().isWholeRepository()
				? new RepositoryProblem(config.name(), RepositoryProblem.NOT_AVAILABLE, config.name() + ".yaml", config.problems())
				: new RepositoryProblem(config.name(), RepositoryProblem.NOT_AVAILABLE, null, null);
	}
}
