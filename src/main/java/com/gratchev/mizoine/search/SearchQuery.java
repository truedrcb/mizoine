package com.gratchev.mizoine.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A search as the user wrote it, read once where it comes in (step 57, D53): words, {@code "a phrase"},
 * {@code -without}, {@code prefix*}. No other syntax: everything else is a word.
 *
 * @param items the parts, in order; at least one is not left out
 */
public record SearchQuery(String text, List<Item> items) {

	public static final int MAX_LENGTH = 200;

	/** A quoted phrase or a word, each perhaps with a leading {@code -}. */
	private static final Pattern PARTS = Pattern.compile("(-?)(?:\"([^\"]*)\"?|(\\S+))");

	/**
	 * One part of the query.
	 *
	 * @param text the word or the phrase, without quotes, {@code -} and {@code *}
	 * @param left whether it is left out ({@code -word})
	 */
	public record Item(String text, Kind kind, boolean left) {
	}

	public enum Kind {
		WORD, PHRASE, PREFIX
	}

	/** @throws IllegalArgumentException with a message for the user: empty, too long, or only left-out parts */
	public static SearchQuery parse(final String text) {
		final String query = text == null ? "" : text.strip();
		if (query.isEmpty()) {
			throw new IllegalArgumentException("Write what to search for.");
		}
		if (query.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("A search can be at most " + MAX_LENGTH + " characters long.");
		}
		final List<Item> items = new ArrayList<>();
		final Matcher parts = PARTS.matcher(query);
		while (parts.find()) {
			final boolean left = !parts.group(1).isEmpty();
			if (parts.group(2) != null) {
				if (!parts.group(2).isBlank()) {
					items.add(new Item(parts.group(2).strip(), Kind.PHRASE, left));
				}
			} else {
				items.add(word(parts.group(3), left));
			}
		}
		if (items.stream().allMatch(Item::left)) {
			throw new IllegalArgumentException("Write what to search for, not only what to leave out.");
		}
		return new SearchQuery(query, List.copyOf(items));
	}

	private static Item word(final String word, final boolean left) {
		// a lone "*" is a word: as an empty prefix it would find everything
		return word.length() > 1 && word.endsWith("*")
				? new Item(word.substring(0, word.length() - 1), Kind.PREFIX, left)
				: new Item(word, Kind.WORD, left);
	}
}
