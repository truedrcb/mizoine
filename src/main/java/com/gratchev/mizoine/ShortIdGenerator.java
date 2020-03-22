package com.gratchev.mizoine;

import java.util.Random;
import java.util.Set;

public class ShortIdGenerator {
	final Random random = new Random();
	public final static String ALL_64_CHARS_SORTED = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";
	public final static String ALL_32_CHARS_SORTED = "0123456789abcdefghijklmnopqrstuv";

	public static String intToCode(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++, n = n >> 6) {
			sb.append(ALL_64_CHARS_SORTED.charAt(n & 63));
		}
		return sb.toString();
	}

	/**
	 * Convert positive integer to an unique identifier with maximum 5 positions.
	 * Almost the same as {@link Integer#toString(int, int)} with radix 32. Each
	 * number is associated to own identifier. Possible number of identifiers is
	 * 33554432 (2 ^ 25) since string contains 5 positions, each from 32 variants.
	 * See {@link #ALL_32_CHARS_SORTED}.<br>
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
	 * @param n Positive integer in range 0 to 33554431 (2 ^ 25 - 1). If numbering
	 *          minutes, it is enough for 63 years.
	 * @return Identifier string of exactly 5 characters.
	 */
	public static String intToMizCode(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 5; i++, n = n >> 5) {
			sb.insert(0, ALL_32_CHARS_SORTED.charAt(n & 31));
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
