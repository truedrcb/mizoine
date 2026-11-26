package com.gratchev.mizoine.repositories;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.gratchev.mizoine.tags.Tag;
import com.gratchev.mizoine.tags.Tags;
import com.gratchev.mizoine.tags.TagsFile;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Steps 62–63: a folder's own tags at {@code ?tags} on its URL (D69), JSON only. Tags are for folders for now
 * (D72); the hidden {@code .tags} file itself is never reached as a file (D3).
 */
@Controller
class FolderTagsController {

	private static final String REPOSITORY = "repository";
	private static final String PATH = "path";

	private final RepositoryFiles files;

	FolderTagsController(final RepositoryFiles files) {
		this.files = files;
	}

	/** The tags of a folder. */
	record TagList(@Schema(description = "The folder's own tags, as written") List<String> tags) {
	}

	/** Why not. */
	record Refusal(String message) {
	}

	@GetMapping(value = "/{repository}/{*path}", params = "tags")
	@Hidden // one operation per path and method: described on the folder's GET (RepositoryController.folder)
	ResponseEntity<Object> tags(@PathVariable(REPOSITORY) final String repository,
			@PathVariable(PATH) final String path,
			final Principal principal) {
		if (!RepositoryFiles.isFolder(path)) {
			return json(HttpStatus.BAD_REQUEST).body(new Refusal(TagsFile.FOLDERS_ONLY));
		}
		final RepositoryFiles.FolderTags tags = files.folderTags(principal.getName(), repository, RepositoryFiles.segments(path));
		final ResponseEntity.BodyBuilder answer = json(HttpStatus.OK);
		tags.version().ifPresent(etag -> answer.header(HttpHeaders.ETAG, etag));
		return answer.body(new TagList(tags.tags().list().stream().map(Tag::text).toList()));
	}

	@PutMapping(value = "/{repository}/{*path}", params = "tags", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Hidden // one operation per path and method: described on the PUT of files and folders (FileWriteController.put)
	ResponseEntity<Object> setTags(@PathVariable(REPOSITORY) final String repository,
			@PathVariable(PATH) final String path,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) final String ifMatch,
			@RequestBody final List<String> tags, final Principal principal) {
		if (!RepositoryFiles.isFolder(path)) {
			return json(HttpStatus.BAD_REQUEST).body(new Refusal(TagsFile.FOLDERS_ONLY));
		}
		final Tags checked;
		try {
			checked = Tags.of(tags);
		} catch (final IllegalArgumentException e) {
			return json(HttpStatus.BAD_REQUEST).body(new Refusal(e.getMessage()));
		}
		return switch (files.setFolderTags(principal.getName(), repository, RepositoryFiles.segments(path), checked,
				Optional.ofNullable(ifMatch))) {
			case RepositoryFiles.TagsResult.Written written -> {
				final ResponseEntity.BodyBuilder answer = json(HttpStatus.OK);
				written.version().ifPresent(etag -> answer.header(HttpHeaders.ETAG, etag));
				yield answer.body(new TagList(written.tags().list().stream().map(Tag::text).toList()));
			}
			case RepositoryFiles.TagsResult.PreconditionFailed failed -> ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
		};
	}

	/** A body that is no JSON list of texts. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Object> unreadable(final HttpMessageNotReadableException e) {
		return json(HttpStatus.BAD_REQUEST).body(new Refusal(
				"The body must be a JSON list of tags, e.g. [\"project\", \"status:open\"]."));
	}

	private static ResponseEntity.BodyBuilder json(final HttpStatus status) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
	}
}
