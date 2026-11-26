package com.gratchev.mizoine.uploads;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;

/**
 * Steps 76–77 (D82–D89): making upload links where a user may write, and taking a file through one, as that user,
 * through the one write path ({@link RepositoryFiles#write}) with its checks.
 */
public class UploadLinkService {

	private static final Logger LOG = LoggerFactory.getLogger(UploadLinkService.class);
	/** Where the links live on this server: {@code /.upload/<token>/}. */
	public static final String PATH = "/.upload/";

	/** What making a link did. */
	public sealed interface Made {

		/** @param url the absolute address to give to the person */
		record Link(String url, Instant expires) implements Made {
		}

		record TooMany() implements Made {
		}

		/** No public address is set, so no absolute link can be made (D89). */
		record NoPublicUrl() implements Made {
		}
	}

	/** What an upload through a link did. */
	public sealed interface Uploaded {

		record Done(String name, String etag) implements Uploaded {
		}

		/** A file of that name exists: never overwritten (D83). */
		record Exists() implements Uploaded {
		}

		record TooLarge() implements Uploaded {
		}

		/** No such link (any more), a name it does not take, or its user may no longer write there: one answer. */
		record NotValid() implements Uploaded {
		}
	}

	private final RepositoryFiles files;
	private final UploadLinks links;
	private final Optional<String> publicUrl;
	private final long maxFileSize;

	public UploadLinkService(final RepositoryFiles files, final UploadLinks links, final Optional<String> publicUrl,
			final long maxFileSize) {
		this.files = files;
		this.links = links;
		this.publicUrl = publicUrl.map(url -> url.replaceFirst("/+$", ""));
		this.maxFileSize = maxFileSize;
	}

	/**
	 * @param name the one name the file gets, or empty: files keep their own names
	 * @throws RepositoryFiles.NotVisibleException where the user may not write
	 * @throws IllegalArgumentException for a name that is no plain file name
	 */
	public Made create(final String user, final String repository, final List<String> folder, final Optional<String> name) {
		name.filter(given -> !RepositoryFiles.isFileName(given)).ifPresent(given -> {
			throw new IllegalArgumentException("Not a file name: " + given);
		});
		files.checkWritableFolder(user, repository, folder);
		if (publicUrl.isEmpty()) {
			return new Made.NoPublicUrl();
		}
		return switch (links.create(user, repository, folder, name)) {
			case UploadLinks.Created created -> {
				LOG.info("Made {}", created.link());
				yield new Made.Link(publicUrl.get() + PATH + created.link().token() + "/", created.link().expires());
			}
			case UploadLinks.TooMany tooMany -> new Made.TooMany();
		};
	}

	/** @return the link of this token, while it is open */
	public Optional<UploadLink> find(final String token) {
		return links.find(token);
	}

	/** One file through a link: never over an existing file; a file not written gives its use back. */
	public Uploaded upload(final String token, final String name, final InputStream body) {
		final Optional<UploadLink> reserved = links.reserve(token);
		if (reserved.isEmpty()) {
			return new Uploaded.NotValid();
		}
		final UploadLink link = reserved.get();
		final Uploaded uploaded = write(link, name, body);
		if (uploaded instanceof Uploaded.Done) {
			LOG.info("Uploaded {} with upload link {}", name, link.id());
		} else {
			links.release(token);
		}
		return uploaded;
	}

	private Uploaded write(final UploadLink link, final String name, final InputStream body) {
		if (!takes(link, name)) {
			return new Uploaded.NotValid();
		}
		try {
			return switch (files.write(link.user(), link.repository(), child(link.folder(), name), body, maxFileSize,
					Precondition.CREATE_ONLY)) {
				case Result.Created created -> new Uploaded.Done(name, created.etag());
				// a file or a folder of that name
				case Result.PreconditionFailed failed -> new Uploaded.Exists();
				case Result.IsFolder folder -> new Uploaded.Exists();
				case Result.TooLarge large -> new Uploaded.TooLarge();
				// the folder gone; never a replace with create-only
				default -> new Uploaded.NotValid();
			};
		} catch (final RepositoryFiles.NotVisibleException e) {
			// the link's user may no longer write there (D86)
			return new Uploaded.NotValid();
		}
	}

	/** A plain file name, and the link's one name if it was made for one (D83). */
	private static boolean takes(final UploadLink link, final String name) {
		return RepositoryFiles.isFileName(name) && link.name().map(name::equals).orElse(true);
	}

	private static List<String> child(final List<String> folder, final String name) {
		return Stream.concat(folder.stream(), Stream.of(name)).toList();
	}
}
