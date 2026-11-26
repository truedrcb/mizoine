package com.gratchev.mizoine.uploads;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.gratchev.mizoine.repositories.RepositoryFiles;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Steps 76–77 (D82–D89): upload links. Made with {@code POST …/?upload-link} where the user may write; then
 * {@code /.upload/<token>/} takes files without login, into that folder only, as the user who made it.
 */
@Controller
@io.swagger.v3.oas.annotations.tags.Tag(name = "Upload links",
		description = "Temporary addresses that take files into one folder without login, e.g. a photo from a phone")
class UploadLinkController {

	private static final String TOKEN = "token";
	private static final String NOT_VALID = "This upload link is not valid (any more). Ask for a new one.";

	private final UploadLinkService service;

	UploadLinkController(final UploadLinkService service) {
		this.service = service;
	}

	/** The body of {@code POST …/?upload-link}; the name is optional. */
	record LinkRequest(@Schema(description = "The one name the file gets; without it files keep their own names")
			String name) {
	}

	/** A link made. */
	record LinkAnswer(@Schema(description = "The address to open, e.g. on a phone") String url,
			@Schema(description = "When it stops working") Instant expires) {
	}

	/** Why not. */
	record Refusal(String message) {
	}

	@PostMapping(value = "/{repository}/{*path}", params = "upload-link")
	@Hidden // one operation per path and method: described on the move (FileWriteController.move)
	ResponseEntity<Object> create(@PathVariable("repository") final String repository,
			@PathVariable("path") final String path, @RequestBody(required = false) final LinkRequest request,
			final Principal principal) {
		if (!RepositoryFiles.isFolder(path)) {
			return json(HttpStatus.BAD_REQUEST, new Refusal("Upload links are for folders: a folder's path ends with '/'."));
		}
		final Optional<String> name = Optional.ofNullable(request).map(LinkRequest::name);
		try {
			return switch (service.create(principal.getName(), repository, RepositoryFiles.segments(path), name)) {
				case UploadLinkService.Made.Link link -> json(HttpStatus.CREATED, new LinkAnswer(link.url(), link.expires()));
				case UploadLinkService.Made.TooMany many -> json(HttpStatus.TOO_MANY_REQUESTS, new Refusal(
						"Too many open upload links (" + UploadLinks.MAX_OPEN_PER_USER + "): wait until some expire."));
				case UploadLinkService.Made.NoPublicUrl none -> json(HttpStatus.CONFLICT, new Refusal(
						"This Mizoine has no public address set, so it cannot make upload links."));
			};
		} catch (final IllegalArgumentException e) {
			return json(HttpStatus.BAD_REQUEST, new Refusal(e.getMessage()));
		}
	}

	@GetMapping(value = UploadLinkService.PATH + "{token}/", produces = MediaType.TEXT_HTML_VALUE)
	@Operation(operationId = "uploadLinkPage", summary = "The page of an upload link",
			description = "A page with a file picker (on a phone: camera, gallery, files) that uploads into the link's "
					+ "folder. No login: the link is the permission, for 15 minutes and at most 10 files (1 if it was made "
					+ "for one name). Not valid (any more): 404 with a page saying so.")
	@ApiResponse(responseCode = "200", description = "The page", content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
	@ApiResponse(responseCode = "404", description = "Not valid (any more)",
			content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
	String page(@PathVariable(TOKEN) @Parameter(description = "The link's secret part") final String token,
			final Model model, final HttpServletResponse response) {
		secret(response);
		service.find(token).ifPresentOrElse(link -> {
			model.addAttribute("target", link.repository() + "/" + String.join("/", link.folder())
					+ (link.folder().isEmpty() ? "" : "/"));
			model.addAttribute("name", link.name().orElse(null));
			model.addAttribute("expires", link.expires());
			model.addAttribute("filesLeft", link.filesLeft());
		}, () -> {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			model.addAttribute("message", NOT_VALID);
		});
		return "upload";
	}

	@PutMapping(UploadLinkService.PATH + "{token}/{name}")
	@Operation(operationId = "uploadThroughLink", summary = "Upload a file through an upload link",
			description = "The request body is the file, stored exactly as sent under this name in the link's folder, "
					+ "e.g. curl -T photo.jpg <link>photo.jpg. No login and no CSRF token: the link is the permission. "
					+ "Never over an existing file. A file not stored does not count against the link.")
	@ApiResponse(responseCode = "201", description = "Stored", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
	@ApiResponse(responseCode = "404", description = "Not valid (any more), or a name the link does not take",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "412", description = "A file of that name exists already: nothing changed",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "413", description = "Larger than the server's maximum file size",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Refusal.class)))
	@ApiResponse(responseCode = "500", description = "Repository configured wrongly", content = @Content)
	ResponseEntity<Object> upload(@PathVariable(TOKEN) @Parameter(description = "The link's secret part") final String token,
			@PathVariable("name") @Parameter(description = "The file's name") final String name,
			final HttpServletRequest request, final HttpServletResponse response) throws IOException {
		secret(response);
		return switch (service.upload(token, name, request.getInputStream())) {
			case UploadLinkService.Uploaded.Done done -> ResponseEntity.status(HttpStatus.CREATED)
					.header(HttpHeaders.ETAG, done.etag()).contentType(MediaType.APPLICATION_JSON)
					.body(new Refusal("Uploaded " + done.name() + "."));
			case UploadLinkService.Uploaded.Exists exists -> json(HttpStatus.PRECONDITION_FAILED,
					new Refusal("A file of that name exists already: not uploaded."));
			case UploadLinkService.Uploaded.TooLarge large -> json(HttpStatus.CONTENT_TOO_LARGE,
					new Refusal("Too large for the server: not uploaded."));
			case UploadLinkService.Uploaded.NotValid notValid -> json(HttpStatus.NOT_FOUND, new Refusal(NOT_VALID));
		};
	}

	/**
	 * Everything else below {@code /.upload/} (no login there): nothing is read, listed or changed through a link,
	 * only files added by {@link #upload}.
	 */
	@RequestMapping(UploadLinkService.PATH + "**")
	@Hidden // no operation: always 404
	ResponseEntity<Object> nothingElse(final HttpServletResponse response) {
		secret(response);
		return json(HttpStatus.NOT_FOUND, new Refusal(NOT_VALID));
	}

	/** D88: the token is in the URL: sent nowhere else, kept by no cache, shown by no search engine. */
	private static void secret(final HttpServletResponse response) {
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		response.setHeader("X-Robots-Tag", "noindex");
	}

	private static ResponseEntity<Object> json(final HttpStatus status, final Object body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
