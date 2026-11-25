package com.gratchev.mizoine.users;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.gratchev.mizoine.files.FileVersion;

/**
 * Users from the hand-editable users file. Usernames are case-sensitive. Every user has role {@code USER}.
 * <p>
 * The file is read on construction; a missing or invalid file fails there (application does not start).
 * Later, every lookup checks the file's modification time and size, and re-reads it only when they changed.
 * If the changed file is missing or invalid, the last valid users stay active and an error is logged once.
 * <p>
 * Every password hash is checked to be one {@link PasswordHashing} supports.
 */
public class FileUserDetailsService implements UserDetailsService, UserEmails {

	private static final Logger log = LoggerFactory.getLogger(FileUserDetailsService.class);

	private static final String ROLE = "USER";

	private final Path usersFile;
	private final UsersFileParser parser;
	private volatile Map<String, UserEntry> users;
	/** Empty while the file cannot be read. */
	private Optional<FileVersion> loadedVersion;

	public FileUserDetailsService(final Path usersFile, final UsersFileParser parser) {
		this.usersFile = usersFile;
		this.parser = parser;
		this.loadedVersion = FileVersion.of(usersFile);
		this.users = load();
	}

	@Override
	public UserDetails loadUserByUsername(final String username) {
		reloadIfChanged();
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

	@Override
	public Optional<String> email(final String username) {
		reloadIfChanged();
		return Optional.ofNullable(users.get(username)).flatMap(UserEntry::email);
	}

	private synchronized void reloadIfChanged() {
		// read the version before the content: a change during loading is picked up next time
		final Optional<FileVersion> version = FileVersion.of(usersFile);
		if (version.equals(loadedVersion)) {
			return;
		}
		loadedVersion = version;
		try {
			users = load();
			log.info("Users file {} reloaded: {} users", usersFile, users.size());
		} catch (final UsersFileException e) {
			log.error("Users file {} changed but cannot be used, keeping the previous {} users. {}",
					usersFile, users.size(), e.getMessage());
		}
	}

	private Map<String, UserEntry> load() {
		final List<UserEntry> entries = parser.parse(usersFile);
		for (final UserEntry entry : entries) {
			if (!PasswordHashing.isSupported(entry.passwordHash())) {
				// never include the value: it may be a mistyped plain-text password
				throw new UsersFileException("User '" + entry.username() + "' in users file " + usersFile
						+ " has an unsupported or broken password hash, expected " + PasswordHashing.EXAMPLE);
			}
		}
		final Map<String, UserEntry> loaded = entries.stream()
				.collect(Collectors.toUnmodifiableMap(UserEntry::username, Function.identity()));
		if (loaded.isEmpty()) {
			log.warn("Users file {} has no users: nobody can log in", usersFile);
		}
		return loaded;
	}
}
