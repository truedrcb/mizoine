package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gratchev.mizoine.ApiTexts;
import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.MoveResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Writing repository files over HTTP (T1: REST on the file URLs). Writable is what lies inside a granted folder
 * and is not hidden; everything else gives the same 404 as unknown paths (D2, D3, D4).
 */
@RestController
@Tag(name = "Repositories")
class FileWriteController {

	private static final String REPOSITORY = "repository";
	private static final String PATH = "path";
	/** WebDAV's header for the new place of a move (step 41). */
	static final String DESTINATION = "Destination";
	private static final String NOT_FOUND = "Unknown repository or path, no write access, or a hidden name — "
			+ "deliberately the same answer";

	private final RepositoryFiles files;
	private final long maxFileSize;

	FileWriteController(final RepositoryFiles files, final MizoineProperties properties) {
		this.files = files;
		this.maxFileSize = properties.maxFileSize().toBytes();
	}

	@PutMapping("/{repository}/{*path}")
	@Operation(summary = "Create or replace a file, or create a folder",
			description = "A folder URL (ending with '/', empty body) creates the folder; it exists already → 204. "
					+ "A file URL: the request body is the new content, stored exactly as sent. Atomic: readers see the old "
					+ "or the new content, never a part. The folder must exist (folders are created explicitly). "
					+ "If-Match: <ETag> writes only if the file is unchanged since it was read; If-None-Match: * "
					+ "creates only. API clients with an Authorization header need no CSRF token; the browser UI "
					+ "(session cookie) sends X-XSRF-TOKEN. "
					+ "'?tags' on a folder replaces its own tags instead: the body a JSON list of tags, an empty list "
					+ "removes them; 200 with the tags as they are now. A tag is a word of letters, digits, -, _ and /, "
					+ "with one ':' for key:value tags such as statuses (one value per key, the later wins); at most "
					+ "30 tags of 50 characters; a word that is no tag is 400. If-Match: the ETag of the tags. Only "
					+ "where the user may write; the repository root only with access to the whole repository.")
	@ApiResponse(responseCode = "200", description = "?tags: the folder's tags as they are now; ETag when any are left",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
	@ApiResponse(responseCode = "400", description = "?tags: not a folder, not a list of tags, or a word that is no tag",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
	@ApiResponse(responseCode = "201", description = "Created; Location (and for files the ETag)", content = @Content)
	@ApiResponse(responseCode = "204", description = "File replaced (ETag of the new content), or folder exists already",
			content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "403", description = "Browser session without CSRF token", content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content)
	@ApiResponse(responseCode = "409", description = "The parent folder does not exist; a folder or file is in the way; "
			+ "or the repository root",
			content = @Content)
	@ApiResponse(responseCode = "412", description = "If-Match or If-None-Match not met; nothing written",
			content = @Content)
	@ApiResponse(responseCode = "413", description = "Larger than the server's maximum file size; nothing written",
			content = @Content)
	@ApiResponse(responseCode = "415", description = "A folder URL with a request body", content = @Content)
	@ApiResponse(responseCode = "500", description = "Repository configured wrongly", content = @Content)
	ResponseEntity<Void> put(
			@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@PathVariable(PATH) @Parameter(description = "File below the repository root; or a folder, ending with '/'")
			final String path,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
			@Parameter(description = "Files: the ETag the file must still have") final String ifMatch,
			@RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
			@Parameter(description = "'*': create only") final String ifNoneMatch,
			final Principal principal, final HttpServletRequest request, final InputStream body) throws IOException {
		final Place place = new Place(principal.getName(), repository, RepositoryUrls.segments(path));
		final boolean createOnly = "*".equals(ifNoneMatch);
		if (RepositoryUrls.isFolder(path)) {
			return createFolder(place, createOnly, hasBody(request, body));
		}
		return writeFile(place, new Precondition(Optional.ofNullable(ifMatch), createOnly), request.getContentLengthLong(), body);
	}

	/** Who acts where: the user and the path below a repository. */
	private record Place(String user, String repository, List<String> segments) {
	}

	private ResponseEntity<Void> writeFile(final Place place, final Precondition precondition, final long contentLength,
			final InputStream body) {
		files.checkWritable(place.user(), place.repository(), place.segments());
		if (contentLength > maxFileSize) {
			// known too large before reading anything
			return status(HttpStatus.CONTENT_TOO_LARGE);
		}
		return switch (files.write(place.user(), place.repository(), place.segments(), body, maxFileSize, precondition)) {
			case Result.Created created -> ResponseEntity.created(location(place, false)).eTag(created.etag()).build();
			case Result.Replaced replaced -> ResponseEntity.noContent().eTag(replaced.etag()).build();
			case Result.PreconditionFailed failed -> status(HttpStatus.PRECONDITION_FAILED);
			case Result.ParentMissing missing -> status(HttpStatus.CONFLICT);
			case Result.IsFolder folder -> status(HttpStatus.CONFLICT);
			case Result.TooLarge large -> status(HttpStatus.CONTENT_TOO_LARGE);
			case Result.NotFound notFound -> throw GrantedRepository.notFound();
		};
	}

	@DeleteMapping("/{repository}/{*path}")
	@Operation(summary = "Delete a file or an empty folder",
			description = "A file URL deletes the file, a folder URL (ending with '/') an empty folder. A folder "
					+ "with content, also hidden content such as .git, is never deleted (409); nor the repository "
					+ "root or the user's own granted folder. If-Match: <ETag> deletes a file only if unchanged. "
					+ "A symbolic link is removed, never its target.")
	@ApiResponse(responseCode = "204", description = "Deleted", content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "403", description = "Browser session without CSRF token", content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND + "; or a folder URL for a file, or the other way round",
			content = @Content)
	@ApiResponse(responseCode = "409", description = "Folder not empty; or the repository root or a granted folder",
			content = @Content)
	@ApiResponse(responseCode = "412", description = "If-Match not met; nothing deleted", content = @Content)
	@ApiResponse(responseCode = "500", description = "Repository configured wrongly", content = @Content)
	ResponseEntity<Void> delete(
			@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@PathVariable(PATH) @Parameter(description = "File; or folder, ending with '/'") final String path,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
			@Parameter(description = "Files: the ETag the file must still have") final String ifMatch,
			final Principal principal) {
		return switch (files.delete(principal.getName(), repository, RepositoryUrls.segments(path), RepositoryUrls.isFolder(path),
				Optional.ofNullable(ifMatch))) {
			case DeleteResult.Deleted deleted -> ResponseEntity.noContent().build();
			case DeleteResult.NotEmpty notEmpty -> status(HttpStatus.CONFLICT);
			case DeleteResult.IsGrantedFolder grantedFolder -> status(HttpStatus.CONFLICT);
			case DeleteResult.PreconditionFailed failed -> status(HttpStatus.PRECONDITION_FAILED);
			case DeleteResult.NotFound notFound -> throw GrantedRepository.notFound();
		};
	}

	@PostMapping("/{repository}/{*path}")
	@Operation(summary = "Move or rename a file or a folder",
			description = "The Destination header names the new place: a path on this server in the same repository, "
					+ "e.g. /notes/archive/plan.md, encoded like a URL; folders end with '/' in both. WebDAV MOVE semantics "
					+ "on POST. A folder moves with everything in it, in one atomic step. Never overwrites; the "
					+ "destination's folder must exist. If-Match: <ETag> moves a file only if unchanged. Not the "
					+ "repository root or a granted folder. API clients and the browser UI need no CSRF token for it "
					+ "(a cross-site form cannot send a Destination header).")
	@ApiResponse(responseCode = "201", description = "Moved; Location of the new place", content = @Content)
	@ApiResponse(responseCode = "400", description = "No Destination, or not a path in this repository, or a folder URL "
			+ "for a file (or the other way round)", content = @Content)
	@ApiResponse(responseCode = "401", description = ApiTexts.NOT_LOGGED_IN, content = @Content)
	@ApiResponse(responseCode = "403", description = "Browser session: POST without Destination (CSRF)", content = @Content)
	@ApiResponse(responseCode = "404", description = NOT_FOUND + "; for the source or the destination", content = @Content)
	@ApiResponse(responseCode = "409", description = "The destination's folder does not exist; a folder into itself; "
			+ "the repository root or a granted folder; source and destination on different file systems",
			content = @Content)
	@ApiResponse(responseCode = "412", description = "The destination exists (never overwritten), or If-Match not met",
			content = @Content)
	@ApiResponse(responseCode = "500", description = "Repository configured wrongly", content = @Content)
	ResponseEntity<Void> move(
			@PathVariable(REPOSITORY) @Parameter(description = "Repository name") final String repository,
			@PathVariable(PATH) @Parameter(description = "File; or folder, ending with '/'") final String path,
			@RequestHeader(name = DESTINATION, required = false)
			@Parameter(description = "New path on this server, e.g. /notes/archive/plan.md") final String destination,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
			@Parameter(description = "Files: the ETag the file must still have") final String ifMatch,
			final Principal principal) {
		final boolean folder = RepositoryUrls.isFolder(path);
		final List<String> to = RepositoryUrls.destination(destination, repository, folder)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
		return switch (files.move(principal.getName(), repository, RepositoryUrls.segments(path), to, folder,
				Optional.ofNullable(ifMatch))) {
			case MoveResult.Moved moved -> ResponseEntity.created(location(new Place(principal.getName(), repository, to),
					folder)).build();
			case MoveResult.DestinationExists exists -> status(HttpStatus.PRECONDITION_FAILED);
			case MoveResult.PreconditionFailed failed -> status(HttpStatus.PRECONDITION_FAILED);
			case MoveResult.DestinationParentMissing missing -> status(HttpStatus.CONFLICT);
			case MoveResult.IntoItself itself -> status(HttpStatus.CONFLICT);
			case MoveResult.IsGrantedFolder grantedFolder -> status(HttpStatus.CONFLICT);
			case MoveResult.NotAtomic notAtomic -> status(HttpStatus.CONFLICT);
			case MoveResult.NotFound notFound -> throw GrantedRepository.notFound();
		};
	}

	private ResponseEntity<Void> createFolder(final Place place, final boolean createOnly, final boolean hasBody) {
		if (place.segments().isEmpty()) {
			// the repository root always exists and is not created
			return status(HttpStatus.CONFLICT);
		}
		files.checkWritable(place.user(), place.repository(), place.segments());
		if (hasBody) {
			// as WebDAV MKCOL: a folder has no content
			return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		}
		return switch (files.createFolder(place.user(), place.repository(), place.segments(), createOnly)) {
			case FolderResult.Created created -> ResponseEntity.created(location(place, true)).build();
			case FolderResult.Exists exists -> ResponseEntity.noContent().build();
			case FolderResult.PreconditionFailed failed -> status(HttpStatus.PRECONDITION_FAILED);
			case FolderResult.ParentMissing missing -> status(HttpStatus.CONFLICT);
			case FolderResult.FileInTheWay file -> status(HttpStatus.CONFLICT);
			case FolderResult.NotFound notFound -> throw GrantedRepository.notFound();
		};
	}

	private static boolean hasBody(final HttpServletRequest request, final InputStream body) throws IOException {
		return request.getContentLengthLong() > 0 || body.read() >= 0;
	}

	private static URI location(final Place place, final boolean folder) {
		return URI.create(RepositoryUrls.url(place.repository(), place.segments(), folder));
	}

	private static ResponseEntity<Void> status(final HttpStatus status) {
		return ResponseEntity.status(status).build();
	}
}
