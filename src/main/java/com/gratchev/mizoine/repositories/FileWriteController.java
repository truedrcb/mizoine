package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gratchev.mizoine.ApiTexts;
import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
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
	private static final String NOT_FOUND = "Unknown repository or path, no write access, or a hidden name — "
			+ "deliberately the same answer";

	private final RepositoryRegistry registry;
	private final RepositoryWriter writer;
	private final long maxFileSize;

	FileWriteController(final RepositoryRegistry registry, final RepositoryWriter writer,
			final MizoineProperties properties) {
		this.registry = registry;
		this.writer = writer;
		this.maxFileSize = properties.maxFileSize().toBytes();
	}

	@PutMapping("/{repository}/{*path}")
	@Operation(summary = "Create or replace a file, or create a folder",
			description = "A folder URL (ending with '/', empty body) creates the folder; it exists already → 204. "
					+ "A file URL: the request body is the new content, stored exactly as sent. Atomic: readers see the old "
					+ "or the new content, never a part. The folder must exist (folders are created explicitly). "
					+ "If-Match: <ETag> writes only if the file is unchanged since it was read; If-None-Match: * "
					+ "creates only. API clients with an Authorization header need no CSRF token; the browser UI "
					+ "(session cookie) sends X-CSRF-TOKEN.")
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
	@ApiResponse(responseCode = "413", description = "Larger than mizoine.max-file-size; nothing written",
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
		final GrantedRepository granted = writable(repository, principal);
		final List<String> segments = RepositoryUrls.segments(path);
		final boolean createOnly = "*".equals(ifNoneMatch);
		if (RepositoryUrls.isFolder(path)) {
			return createFolder(granted, segments, createOnly, hasBody(request, body));
		}
		return writeFile(granted, segments, new Precondition(Optional.ofNullable(ifMatch), createOnly),
				request.getContentLengthLong(), body);
	}

	private ResponseEntity<Void> writeFile(final GrantedRepository granted, final List<String> segments,
			final Precondition precondition, final long contentLength, final InputStream body) {
		checkWritable(granted, segments);
		if (contentLength > maxFileSize) {
			// known too large before reading anything
			return status(HttpStatus.CONTENT_TOO_LARGE);
		}
		return switch (writer.write(granted.config().root(), segments, body, maxFileSize, precondition)) {
			case Result.Created created -> ResponseEntity.created(location(granted, segments, false)).eTag(created.etag()).build();
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
		final GrantedRepository granted = writable(repository, principal);
		final List<String> segments = RepositoryUrls.segments(path);
		if (granted.access().isGrantedFolder(segments)) {
			return status(HttpStatus.CONFLICT);
		}
		checkWritable(granted, segments);
		return switch (writer.delete(granted.config().root(), segments, RepositoryUrls.isFolder(path),
				Optional.ofNullable(ifMatch))) {
			case DeleteResult.Deleted deleted -> ResponseEntity.noContent().build();
			case DeleteResult.NotEmpty notEmpty -> status(HttpStatus.CONFLICT);
			case DeleteResult.PreconditionFailed failed -> status(HttpStatus.PRECONDITION_FAILED);
			case DeleteResult.NotFound notFound -> throw GrantedRepository.notFound();
		};
	}

	private ResponseEntity<Void> createFolder(final GrantedRepository granted, final List<String> segments,
			final boolean createOnly, final boolean hasBody) {
		if (segments.isEmpty()) {
			// the repository root always exists and is not created
			return status(HttpStatus.CONFLICT);
		}
		checkWritable(granted, segments);
		if (hasBody) {
			// as WebDAV MKCOL: a folder has no content
			return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		}
		return switch (writer.createFolder(granted.config().root(), segments, createOnly)) {
			case FolderResult.Created created -> ResponseEntity.created(location(granted, segments, true)).build();
			case FolderResult.Exists exists -> ResponseEntity.noContent().build();
			case FolderResult.PreconditionFailed failed -> status(HttpStatus.PRECONDITION_FAILED);
			case FolderResult.ParentMissing missing -> status(HttpStatus.CONFLICT);
			case FolderResult.FileInTheWay file -> status(HttpStatus.CONFLICT);
			case FolderResult.NotFound notFound -> throw GrantedRepository.notFound();
		};
	}

	private GrantedRepository writable(final String repository, final Principal principal) {
		final GrantedRepository granted = GrantedRepository.find(registry, repository, principal);
		if (granted.hasProblems()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return granted;
	}

	/** Only inside a granted folder, never hidden; everything else is the same 404 as unknown (D2). */
	private static void checkWritable(final GrantedRepository granted, final List<String> segments) {
		if (segments.isEmpty() || !granted.access().canWriteIn(segments.subList(0, segments.size() - 1))) {
			throw GrantedRepository.notFound();
		}
	}

	private static boolean hasBody(final HttpServletRequest request, final InputStream body) throws IOException {
		return request.getContentLengthLong() > 0 || body.read() >= 0;
	}

	private static URI location(final GrantedRepository granted, final List<String> segments, final boolean folder) {
		return URI.create(RepositoryUrls.url(granted.config().name(), segments, folder));
	}

	private static ResponseEntity<Void> status(final HttpStatus status) {
		return ResponseEntity.status(status).build();
	}
}
