package com.gratchev.mizoine.uploads;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Step 75 (D84, D85): the open upload links, in memory, as the OAuth tokens (D18): a restart ends them. One writer:
 * every method is synchronized, so that two uploads at once cannot use one file of a link twice.
 */
public class UploadLinks {

	public static final Duration LIFETIME = Duration.ofMinutes(15);
	public static final int MAX_FILES = 10;
	public static final int MAX_OPEN_PER_USER = 20;
	private static final int TOKEN_BYTES = 32;
	private static final int ID_BYTES = 4;

	/** What making a link did. */
	public sealed interface Result {
	}

	public record Created(UploadLink link) implements Result {
	}

	/** The user has {@link #MAX_OPEN_PER_USER} open links already. */
	public record TooMany() implements Result {
	}

	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, UploadLink> links = new HashMap<>();

	public UploadLinks(final Clock clock) {
		this.clock = clock;
	}

	/** @param name the one name the file gets (then the link takes one file), or empty: files keep their names */
	public synchronized Result create(final String user, final String repository, final List<String> folder,
			final Optional<String> name) {
		forgetExpired();
		if (links.values().stream().filter(link -> link.user().equals(user) && link.filesLeft() > 0).count()
				>= MAX_OPEN_PER_USER) {
			return new TooMany();
		}
		final UploadLink link = new UploadLink(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(TOKEN_BYTES)),
				HexFormat.of().formatHex(bytes(ID_BYTES)), user, repository, List.copyOf(folder), name,
				clock.instant().plus(LIFETIME), name.isPresent() ? 1 : MAX_FILES);
		links.put(link.token(), link);
		return new Created(link);
	}

	/** @return the link of this token, if it is still open: not expired, files left */
	public synchronized Optional<UploadLink> find(final String token) {
		forgetExpired();
		return Optional.ofNullable(links.get(token)).filter(link -> link.filesLeft() > 0);
	}

	/** Takes one file of the link for an upload; give it back with {@link #release} if the file is not written. */
	public synchronized Optional<UploadLink> reserve(final String token) {
		final Optional<UploadLink> link = find(token);
		link.ifPresent(open -> links.put(token, open.withFilesLeft(open.filesLeft() - 1)));
		return link;
	}

	/** Gives back a file reserved for an upload that was not written; nothing after the link expired. */
	public synchronized void release(final String token) {
		Optional.ofNullable(links.get(token)).filter(this::notExpired)
				.ifPresent(link -> links.put(token, link.withFilesLeft(link.filesLeft() + 1)));
	}

	/** A used-up link stays until it expires: an upload that is refused gives its file back. */
	private void forgetExpired() {
		links.values().removeIf(link -> !notExpired(link));
	}

	private boolean notExpired(final UploadLink link) {
		return clock.instant().isBefore(link.expires());
	}

	private byte[] bytes(final int count) {
		final byte[] bytes = new byte[count];
		random.nextBytes(bytes);
		return bytes;
	}
}
