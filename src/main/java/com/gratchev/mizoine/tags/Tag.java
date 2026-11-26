package com.gratchev.mizoine.tags;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A tag of a folder (step 61, D66, D67): a word of letters of any alphabet, digits, {@code -}, {@code _} and
 * {@code /}, with at most one {@code :} between a key and a value ({@code status:open}). At most 50 characters.
 * Written as it was given; compared without case. A leading {@code #} is the hashtag form of the same tag.
 */
public final class Tag {

	public static final int MAX_LENGTH = 50;

	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_/-]+(:[\\p{L}\\p{N}_/-]+)?");

	private final String text;
	private final String folded;

	private Tag(final String text) {
		this.text = text;
		this.folded = text.toLowerCase(Locale.ROOT);
	}

	/** @throws IllegalArgumentException with a message for the user, naming the tag */
	public static Tag of(final String written) {
		final String text = written.startsWith("#") ? written.substring(1) : written;
		if (text.isEmpty()) {
			throw new IllegalArgumentException("A tag is empty.");
		}
		if (text.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("A tag can be at most " + MAX_LENGTH + " characters long: " + text);
		}
		if (!WORD.matcher(text).matches()) {
			throw new IllegalArgumentException("Not a tag: \"" + text + "\" (letters, digits, -, _, / and one : only).");
		}
		return new Tag(text);
	}

	public String text() {
		return text;
	}

	/** @return the key of a {@code key:value} tag, in lower case: a folder has one value per key */
	public Optional<String> key() {
		final int colon = folded.indexOf(':');
		return colon < 0 ? Optional.empty() : Optional.of(folded.substring(0, colon));
	}

	/** The tag without case, as compared and as searched. */
	public String folded() {
		return folded;
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof final Tag tag && tag.folded.equals(folded);
	}

	@Override
	public int hashCode() {
		return folded.hashCode();
	}

	@Override
	public String toString() {
		return text;
	}
}
