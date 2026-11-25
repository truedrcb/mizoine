package com.gratchev.mizoine.users;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.config.YamlFiles;

import tools.jackson.databind.JsonNode;

/**
 * Reads the hand-editable users file (YAML).
 * <p>
 * Usernames are case-sensitive. Passwords must be stored as hashes with a
 * Spring Security {@code {id}} prefix; plain text and {@code {noop}} are rejected.
 * Unknown fields are ignored and logged as warnings.
 */
public class UsersFileParser {

	private static final Logger log = LoggerFactory.getLogger(UsersFileParser.class);

	private static final String USERS = "users";
	private static final String USERNAME = "username";
	private static final String PASSWORD = "password";
	private static final String EMAIL = "email";
	private static final Set<String> USER_FIELDS = Set.of(USERNAME, PASSWORD, EMAIL);

	private static final Pattern HASH_WITH_ID = Pattern.compile("^\\{([^}]+)}(.+)$");
	private static final String PLAIN_TEXT_ID = "noop";

	public List<UserEntry> parse(final Path file) {
		final JsonNode root = readTree(file);

		if (root == null || !root.isObject() || !root.has(USERS)) {
			throw new UsersFileException("Users file " + file + " must contain a '" + USERS + "' list");
		}
		root.propertyNames().stream()
				.filter(name -> !USERS.equals(name))
				.forEach(name -> log.warn("Ignoring unknown field '{}' in users file {}", name, file));

		final JsonNode usersNode = root.get(USERS);
		if (usersNode.isNull()) {
			return List.of();
		}
		if (!usersNode.isArray()) {
			throw new UsersFileException("'" + USERS + "' in users file " + file + " must be a list");
		}

		final List<UserEntry> users = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		int index = 0;
		for (final JsonNode userNode : usersNode) {
			index++;
			final UserEntry user = toUser(userNode, index, file);
			if (!seen.add(user.username())) {
				throw new UsersFileException("Users file " + file + " has duplicate username '" + user.username() + "'");
			}
			users.add(user);
		}
		return List.copyOf(users);
	}

	private static JsonNode readTree(final Path file) {
		return YamlFiles.readTree(file, "Users file", UsersFileException::new);
	}

	private UserEntry toUser(final JsonNode node, final int index, final Path file) {
		if (!node.isObject()) {
			throw new UsersFileException("User #" + index + " in users file " + file + " must be a mapping");
		}
		final String username = text(node, USERNAME);
		if (username == null || username.isBlank()) {
			throw new UsersFileException("User #" + index + " in users file " + file + " has no '" + USERNAME + "'");
		}
		node.propertyNames().stream()
				.filter(name -> !USER_FIELDS.contains(name))
				.forEach(name -> log.warn("Ignoring unknown field '{}' of user '{}' in users file {}", name, username, file));

		final String password = text(node, PASSWORD);
		if (password == null || password.isBlank()) {
			throw new UsersFileException("User '" + username + "' in users file " + file + " has no '" + PASSWORD + "'");
		}
		final Matcher matcher = HASH_WITH_ID.matcher(password);
		if (!matcher.matches() || PLAIN_TEXT_ID.equals(matcher.group(1))) {
			// never include the value: it may be a plain-text password
			throw new UsersFileException("User '" + username + "' in users file " + file
					+ " must have a hashed password like '{bcrypt}$2a$...', plain text is not allowed");
		}
		return new UserEntry(username, password, email(node, username, file));
	}

	/** D38: optional; the author address of the user's Git commits. */
	private static Optional<String> email(final JsonNode node, final String username, final Path file) {
		final JsonNode email = node.get(EMAIL);
		if (email != null && !email.isNull() && !email.isString()) {
			throw new UsersFileException("User '" + username + "' in users file " + file + ": '" + EMAIL + "' must be a text");
		}
		return Optional.ofNullable(text(node, EMAIL)).filter(text -> !text.isBlank());
	}

	private static String text(final JsonNode node, final String field) {
		final JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asString();
	}
}
