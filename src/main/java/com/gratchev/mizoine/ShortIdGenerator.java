package com.gratchev.mizoine;

import java.util.Random;
import java.util.Set;

public class ShortIdGenerator {
	final Random random = new Random();
	public final static String ALL_64_CHARS_SORTED = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";
	public final static String ALL_36_CHARS_SORTED = "0123456789abcdefghijklmnopqrstuvwxyz";

	public static String intToCode(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++, n = n >> 6) {
			sb.append(ALL_64_CHARS_SORTED.charAt(n & 63));
		}
		return sb.toString();
	}

	/**
	 * Convert positive integer to an unique identifier with maximum 5 positions.
	 * Almost the same as {@link Integer#toString(int, int)} with radix 36. Each
	 * number is associated to own identifier. Possible number of identifiers is 60466176
	 * (from 00000 to zzzzz).
	 * See {@link #ALL_36_CHARS_SORTED}.<br>
	 * Generated id has following properties:
	 * <ul>
	 * <li>Exactly 5 characters long
	 * <li>Contains only latin small letters and numbers. No spaces, no special
	 * characters.
	 * <li>Compatible with file names (can be used as file name)
	 * <li>Case insensitive (can be encoded wigh capital letters as well)
	 * <li>Sortable (as simple string sort) in same order as source integer (see
	 * parameter 'n')
	 * </ul>
	 * 
	 * @param n Positive integer in range 0 to 60466175 (36 ^ 5 - 1). If numbering
	 *          minutes, it is enough for 115 years.
	 * @return Identifier string of exactly 5 characters.
	 */
	public static String intToMizCode(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 5; i++, n /= 36) {
			sb.insert(0, ALL_36_CHARS_SORTED.charAt(n % 36));
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
