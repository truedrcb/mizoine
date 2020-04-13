package com.gratchev.mizoine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.Set;

public class ShortIdGenerator {
	final Random random = new Random();
	public static final String ALL_64_CHARS_SORTED = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";
	public static final String ALL_36_CHARS_SORTED = "0123456789abcdefghijklmnopqrstuvwxyz";
	/**
	 * Mizone was introduced in 2017. Assume 2017-01-01 00:00 UTC as zero Id: 00000.
	 */
	public static final ZonedDateTime MIZ_EPOCH_START = ZonedDateTime.of(LocalDate.of(2017, 1, 1), LocalTime.of(0, 0),
			ZoneOffset.UTC);
	/**
	 * Maximum input for MixCode generation 60466175 = (36 ^ 5 - 1)
	 */
	public static long MIZ_CODE_MAX = 60466175;

	public static String intToCode(int n) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++, n = n >> 6) {
			sb.append(ALL_64_CHARS_SORTED.charAt(n & 63));
		}
		return sb.toString();
	}

	/**
	 * Convert positive integer to an unique identifier with maximum 5 positions.
	 * Almost the same as {@link Integer#toString(int, int)} with radix 36. Each
	 * number is associated to own identifier. Possible number of identifiers is
	 * 60466176 (from 00000 to zzzzz). See {@link #ALL_36_CHARS_SORTED}.<br>
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
	 * @param n Positive integer in range 0 to 60466175 (36 ^ 5 - 1) (see
	 *          {@link #MIZ_CODE_MAX}). If numbering minutes, it is enough for 115
	 *          years.
	 * @return Identifier string of exactly 5 characters.
	 */
	public static String intToMizCode(int n) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 5; i++, n /= 36) {
			sb.insert(0, ALL_36_CHARS_SORTED.charAt(n % 36));
		}
		return sb.toString();
	}

	public String createId(final String string) {
		random.setSeed(string.hashCode());
		return intToCode(random.nextInt());
	}

	public String createId(final String string, final Set<String> usedIds) {
		random.setSeed(string.hashCode());
		for (;;) {
			final String id = intToCode(random.nextInt());
			if (usedIds.contains(id)) {
				continue;
			}
			return id;
		}
	}

	/**
	 * Generate MizCode for given date/time
	 * 
	 * @param dateTime Absolute time (will be truncated to minutes). For example:
	 *                 <code>ZonedDateTime.now()</code>
	 * @return MizCode for given minute
	 */
	public static String mizCodeFor(final ZonedDateTime dateTime) {
		final long codeNow = ChronoUnit.MINUTES.between(MIZ_EPOCH_START, dateTime);
		if (codeNow < 0 || codeNow > MIZ_CODE_MAX) {
			throw new RuntimeException("mizCode overflow for date/time: " + dateTime);
		}
		return intToMizCode((int) codeNow);
	}
}
