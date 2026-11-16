package com.gratchev.mizoine.users;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Users from the hand-editable users file. The file is read once, on construction,
 * and every password hash is checked to be one {@link PasswordHashing} supports.
 * Usernames are case-sensitive. Every user has role {@code USER}.
 */
public class FileUserDetailsService implements UserDetailsService {

	private static final Logger log = LoggerFactory.getLogger(FileUserDetailsService.class);

	private static final String ROLE = "USER";

	private final Map<String, UserEntry> users;

	public FileUserDetailsService(final Path usersFile, final UsersFileParser parser) {
		final List<UserEntry> entries = parser.parse(usersFile);
		for (final UserEntry entry : entries) {
			if (!PasswordHashing.isSupported(entry.passwordHash())) {
				// never include the value: it may be a mistyped plain-text password
				throw new UsersFileException("User '" + entry.username() + "' in users file " + usersFile
						+ " has an unsupported or broken password hash, expected " + PasswordHashing.EXAMPLE);
			}
		}
		this.users = entries.stream()
				.collect(Collectors.toUnmodifiableMap(UserEntry::username, Function.identity()));
		if (users.isEmpty()) {
			log.warn("Users file {} has no users: nobody can log in", usersFile);
		}
	}

	@Override
	public UserDetails loadUserByUsername(final String username) {
		final UserEntry entry = users.get(username);
		if (entry == null) {
			throw new UsernameNotFoundException("Unknown user");
		}
		// a new instance on every call: Spring Security erases credentials after login
		return User.withUsername(entry.username())
				.password(entry.passwordHash())
				.roles(ROLE)
				.build();
	}
}
