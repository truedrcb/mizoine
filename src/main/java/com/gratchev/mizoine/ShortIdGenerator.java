package com.gratchev.mizoine;

import java.util.Random;
import java.util.Set;

public class ShortIdGenerator {

	final Random random = new Random();
	final static String allChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-";
	
	public static String intToCode(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 6; i++, n = n >> 6) {
			sb.append(allChars.charAt(n & 63));
		}
		return sb.toString();
	}

	public String createId(String string) {
		
		random.setSeed(string.hashCode());
		
		return intToCode(random.nextInt());
	}

	public String createId(String string, Set<String> usedIds) {
		
		random.setSeed(string.hashCode());
		
		for(;;) {
			final String id = intToCode(random.nextInt());
			if (usedIds.contains(id)) {
				continue;
			}
			return id;
		}
	}
}
