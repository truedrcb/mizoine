package com.gratchev.mizoine.uploads;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Step 75 (D83, D86): a temporary permission to upload files into one folder, as the user who made it.
 *
 * @param token the secret part of the link's URL (D88): never logged, see {@link #toString()}
 * @param id a short id for logs, not part of the token
 * @param name the one name the file gets, if the link was made for one file
 * @param filesLeft how many more files may be uploaded with it
 */
public record UploadLink(String token, String id, String user, String repository, List<String> folder,
		Optional<String> name, Instant expires, int filesLeft) {

	UploadLink withFilesLeft(final int files) {
		return new UploadLink(token, id, user, repository, folder, name, expires, files);
	}

	/** Without the token, so that a log of the link is no permission. */
	@Override
	public String toString() {
		return "upload link " + id + " of " + user + " to " + repository + "/" + String.join("/", folder) + "/"
				+ name.orElse("") + ", until " + expires + ", " + filesLeft + " files left";
	}
}
