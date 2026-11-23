package com.gratchev.mizoine.repositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.files.FileVersion;

/**
 * Finds repositories by name, reading {@code <repositories-dir>/<name>.yaml} on request — never at startup.
 * <p>
 * A file is read again only when its modification time or size changed. An unreadable file makes the
 * repository unknown (an older valid version is not kept, D7); its error is logged once per file version.
 * Names are checked before any file access. Only names with an existing file are cached.
 * The users file is never a repository, even when it lies in the repositories folder.
 */
public class RepositoryRegistry {

	private static final Logger log = LoggerFactory.getLogger(RepositoryRegistry.class);

	private static final String EXTENSION = ".yaml";

	/** A read file: its config, or empty if it was unreadable. */
	private record Entry(FileVersion version, Optional<RepositoryConfig> config) {
	}

	private final Path repositoriesDir;
	private final RepositoryFileParser parser;
	private final Predicate<String> userExists;
	private final Path usersFile;
	private final Map<String, Entry> cache = new ConcurrentHashMap<>();

	/**
	 * @param repositoriesDir folder with the repository files; {@code null} = no repositories
	 * @param userExists used only for a warning about usernames that are not in the users file
	 * @param usersFile never treated as a repository file; may be {@code null}
	 */
	public RepositoryRegistry(final Path repositoriesDir, final RepositoryFileParser parser,
			final Predicate<String> userExists, final Path usersFile) {
		this.repositoriesDir = repositoriesDir;
		this.parser = parser;
		this.userExists = userExists;
		this.usersFile = usersFile == null ? null : canonical(usersFile);
	}

	public Optional<RepositoryConfig> find(final String name) {
		if (repositoriesDir == null || !RepositoryNames.isValid(name)) {
			return Optional.empty();
		}
		final Path file = repositoriesDir.resolve(name + EXTENSION);
		if (canonical(file).equals(usersFile)) {
			return Optional.empty();
		}
		final Optional<FileVersion> version = FileVersion.of(file);
		if (version.isEmpty()) {
			cache.remove(name);
			return Optional.empty();
		}
		return cache.compute(name, (key, entry) -> entry != null && entry.version().equals(version.get())
				? entry
				: new Entry(version.get(), read(file))).config();
	}

	/** All repositories with a readable file, sorted by name. */
	public List<RepositoryConfig> all() {
		if (repositoriesDir == null || !Files.isDirectory(repositoriesDir)) {
			return List.of();
		}
		final Set<String> names;
		try (Stream<Path> files = Files.list(repositoriesDir)) {
			names = files.map(file -> file.getFileName().toString())
					.filter(fileName -> fileName.endsWith(EXTENSION))
					.map(fileName -> fileName.substring(0, fileName.length() - EXTENSION.length()))
					.filter(RepositoryNames::isValid)
					.collect(Collectors.toSet());
		} catch (final IOException e) {
			log.error("Cannot list repositories folder {}: {}", repositoriesDir, e.getMessage());
			return List.of();
		}
		cache.keySet().retainAll(names);
		return names.stream()
				.map(this::find)
				.flatMap(Optional::stream)
				.sorted(Comparator.comparing(RepositoryConfig::name))
				.toList();
	}

	int cachedRepositories() {
		return cache.size();
	}

	private Optional<RepositoryConfig> read(final Path file) {
		final RepositoryConfig config;
		try {
			config = parser.parse(file);
		} catch (final RepositoryFileException e) {
			log.error("Repository file cannot be used, the repository is not available: {}", e.getMessage());
			return Optional.empty();
		}
		if (!config.problems().isEmpty()) {
			log.error("Repository file {} has problems: {}", file, String.join("; ", config.problems()));
		}
		config.grants().keySet().stream()
				.filter(username -> !userExists.test(username))
				.forEach(username -> log.warn("Repository '{}' ({}) gives access to '{}', who is not in the users file",
						config.name(), file, username));
		return Optional.of(config);
	}

	private static Path canonical(final Path file) {
		return file.toAbsolutePath().normalize();
	}
}
