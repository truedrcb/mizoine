package com.gratchev.mizoine;

import java.util.Random;
import java.util.Set;

public class ShortIdGenerator {
	final Random random = new Random();
	public final static String ALL_CHARS_SORTED = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

	public static String intToCode(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++, n = n >> 6) {
			sb.append(ALL_CHARS_SORTED.charAt(n & 63));
		}
		return sb.toString();
	}

	/**
	 * Convert positive integer to an unique identifier with maximum 5 positions. Each number is associated to own identifier.
	 * Possible number of identifiers is 1073741824 (2 ^ 30) since string contains 5 positions, each from 64 variants. See {@link #ALL_CHARS_SORTED}.<br>
	 * Generated id has following properties:
	 * <ul>
	 * <li>Exactly 5 characters long
	 * <li>Contains only latin letters, numbers, minus and underscore. No spaces, no special characters.
	 * <li>Compatible with file names (can be used as file name)
	 * <li>Sortable (as simple string sort) in same order as source integer (see parameter 'n')
	 * </ul>
	 * @param n Positive integer in range 0 to 1073741823 (2 ^ 30 - 1)
	 * @return Identifier string of exactly 5 characters.
	 */
	public static String intToSortableCode5(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++, n = n >> 6) {
			sb.append(ALL_CHARS_SORTED.charAt(n & 63));
		}
		return sb.toString();
	}

	public String createId(String string) {
		random.setSeed(string.hashCode());
		return intToCode(random.nextInt());
	}

	public String createId(String string, Set<String> usedIds) {
		random.setSeed(string.hashCode());
		for (;;) {
			final String id = intToCode(random.nextInt());
			if (usedIds.contains(id)) {
				continue;
			}
			return id;
		}
	}
}
