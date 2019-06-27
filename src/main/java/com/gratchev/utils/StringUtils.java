package com.gratchev.utils;

public class StringUtils {

	/**
	 * 
	 * Quick way to check if string represents a number
	 * 
	 * @param str
	 * @return
	 * @see <a href=https://stackoverflow.com/questions/237159/whats-the-best-way-to-check-if-a-string-represents-an-integer-in-java>whats-the-best-way-to-check-if-a-string-represents-an-integer-in-java</a>
	 */
	public static boolean isInteger(String str) {
		if (str == null) {
			return false;
		}
		int length = str.length();
		if (length == 0) {
			return false;
		}
		int i = 0;
		if (str.charAt(0) == '-') {
			if (length == 1) {
				return false;
			}
			i = 1;
		}
		for (; i < length; i++) {
			char c = str.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	/**
	 * Same as {@link#isInteger} but without possible +/-
	 * @param str
	 * @return
	 */
	public static boolean isUnsignedInteger(String str) {
		if (str == null) {
			return false;
		}
		int length = str.length();
		if (length == 0) {
			return false;
		}
		int i = 0;
		for (; i < length; i++) {
			char c = str.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}
}
