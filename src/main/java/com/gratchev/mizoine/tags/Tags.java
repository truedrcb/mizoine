package com.gratchev.mizoine.tags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The tags of a folder (step 61, D67): each once, in their order, and at most one value per key: a later
 * {@code status:done} replaces an earlier {@code status:open}. At most 30.
 */
public final class Tags {

	public static final int MAX = 30;
	public static final Tags NONE = new Tags(List.of(), List.of());

	private final List<Tag> list;
	/** What a hand-edited file had that is no tag; empty for tags given over the API, which are refused instead. */
	private final List<String> problems;

	private Tags(final List<Tag> list, final List<String> problems) {
		this.list = list;
		this.problems = problems;
	}

	/** @throws IllegalArgumentException for a word that is no tag, or more than {@link #MAX} tags */
	public static Tags of(final List<String> written) {
		return of(written.stream().map(Tag::of).toList(), List.of());
	}

	/**
	 * The plain format of a {@code .tags} file (D65): tags separated by spaces, commas or line breaks. Lenient, as
	 * the file may be edited by hand: words that are no tags are left out and named in {@link #problems()}.
	 */
	public static Tags read(final String text) {
		final List<Tag> tags = new ArrayList<>();
		final List<String> problems = new ArrayList<>();
		for (final String word : text.split("[\\s,]+")) {
			if (!word.isEmpty()) {
				try {
					tags.add(Tag.of(word));
				} catch (final IllegalArgumentException e) {
					problems.add(e.getMessage());
				}
			}
		}
		return of(tags, problems);
	}

	private static Tags of(final List<Tag> given, final List<String> problems) {
		final Map<Object, Tag> tags = new LinkedHashMap<>();
		for (final Tag tag : given) {
			// a key:value tag takes the place of its key, a plain tag its own; the first spelling is kept
			final Object place = tag.key().<Object>map(key -> new Key(key)).orElse(tag);
			if (tag.key().isPresent()) {
				tags.remove(place);
			}
			tags.putIfAbsent(place, tag);
		}
		if (tags.size() > MAX) {
			throw new IllegalArgumentException("A folder can have at most " + MAX + " tags.");
		}
		return new Tags(List.copyOf(tags.values()), List.copyOf(problems));
	}

	/** The place of a {@code key:value} tag: one per key. */
	private record Key(String key) {
	}

	public List<Tag> list() {
		return list;
	}

	/** The tags as written, in their order. */
	public List<String> texts() {
		return list.stream().map(Tag::text).toList();
	}

	public List<String> problems() {
		return problems;
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}

	/** As Mizoine writes the file: one tag per line. */
	public String format() {
		return list.stream().map(tag -> tag.text() + "\n").collect(Collectors.joining());
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof final Tags tags && tags.list.equals(list);
	}

	@Override
	public int hashCode() {
		return list.hashCode();
	}

	@Override
	public String toString() {
		return list.toString();
	}
}
