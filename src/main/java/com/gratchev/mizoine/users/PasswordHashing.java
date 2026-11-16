package com.gratchev.mizoine.users;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password hash formats accepted in the users file. Only BCrypt for now:
 * {@code {bcrypt}$2a$<cost>$<53 characters>}.
 */
public final class PasswordHashing {

	public static final String EXAMPLE = "{bcrypt}$2a$12$...";

	private static final String BCRYPT_ID = "bcrypt";
	private static final Pattern BCRYPT_HASH = Pattern.compile("^\\{bcrypt}\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}$");

	private PasswordHashing() {
	}

	public static PasswordEncoder encoder() {
		return new DelegatingPasswordEncoder(BCRYPT_ID, Map.of(BCRYPT_ID, new BCryptPasswordEncoder()));
	}

	public static boolean isSupported(final String hash) {
		return hash != null && BCRYPT_HASH.matcher(hash).matches();
	}
}
