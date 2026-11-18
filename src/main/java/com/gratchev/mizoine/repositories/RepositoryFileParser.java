package com.gratchev.mizoine.repositories;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.config.YamlFiles;

import tools.jackson.databind.JsonNode;

/**
 * Reads one repository file, {@code <name>.yaml}.
 * <p>
 * Two levels of failure (D8): if the permissions cannot be determined (malformed YAML, invalid name,
 * broken {@code access} section) a {@link RepositoryFileException} is thrown. Otherwise the config is
 * returned, possibly with {@link RepositoryConfig#problems() problems} (e.g. a wrong root folder).
 */
public class RepositoryFileParser {

	private static final Logger log = LoggerFactory.getLogger(RepositoryFileParser.class);

	private static final String EXTENSION = ".yaml";
	private static final String PATH = "path";
	private static final String ACCESS = "access";
	private static final Set<String> FIELDS = Set.of(PATH, ACCESS);

	public RepositoryConfig parse(final Path file) {
		final String name = repositoryName(file);
		final JsonNode root = readTree(file);
		if (root == null || !root.isObject()) {
			throw new RepositoryFileException("Repository file " + file + " must contain 'path' and 'access'");
		}
		root.propertyNames().stream()
				.filter(field -> !FIELDS.contains(field))
				.forEach(field -> log.warn("Ignoring unknown field '{}' in repository file {}", field, file));

		final Map<String, List<FolderGrant>> grants = grants(root.get(ACCESS), file);
		if (grants.isEmpty()) {
			log.warn("Repository file {} gives access to nobody", file);
		}
		final List<String> problems = new ArrayList<>();
		final Path rootFolder = rootFolder(root.get(PATH), file, problems);
		return new RepositoryConfig(name, rootFolder, grants, List.copyOf(problems));
	}

	private static String repositoryName(final Path file) {
		final String fileName = file.getFileName().toString();
		final String name = fileName.endsWith(EXTENSION) ? fileName.substring(0, fileName.length() - EXTENSION.length()) : null;
		if (!RepositoryNames.isValid(name)) {
			throw new RepositoryFileException("Repository file " + file + ": '" + fileName
					+ "' does not give a valid repository name (<name>.yaml; letters, digits, '.', '_', '-';"
					+ " not starting with '.'; not login, logout, error)");
		}
		return name;
	}

	private static JsonNode readTree(final Path file) {
		return YamlFiles.readTree(file, "Repository file", RepositoryFileException::new);
	}

	private static Map<String, List<FolderGrant>> grants(final JsonNode access, final Path file) {
		if (access == null || access.isNull()) {
			return Map.of();
		}
		if (!access.isObject()) {
			throw new RepositoryFileException("'" + ACCESS + "' in repository file " + file
					+ " must map usernames to folder lists, e.g. 'artem: [\"/\"]'");
		}
		final Map<String, List<FolderGrant>> grants = new LinkedHashMap<>();
		for (final Map.Entry<String, JsonNode> entry : access.properties()) {
			final String username = entry.getKey();
			if (username.isBlank()) {
				throw new RepositoryFileException("'" + ACCESS + "' in repository file " + file + " has an empty username");
			}
			grants.put(username, folders(username, entry.getValue(), file));
		}
		return Collections.unmodifiableMap(grants);
	}

	private static List<FolderGrant> folders(final String username, final JsonNode folders, final Path file) {
		if (!folders.isArray()) {
			throw new RepositoryFileException("Folders of user '" + username + "' in repository file " + file
					+ " must be a list, e.g. [\"/\"] or [public, projects/shared]");
		}
		final List<FolderGrant> grants = new ArrayList<>();
		for (final JsonNode folder : folders) {
			if (!folder.isString()) {
				throw new RepositoryFileException("Folders of user '" + username + "' in repository file " + file
						+ " must be texts");
			}
			try {
				grants.add(FolderGrant.of(folder.asString()));
			} catch (final IllegalArgumentException e) {
				throw new RepositoryFileException("User '" + username + "' in repository file " + file + ": " + e.getMessage());
			}
		}
		return List.copyOf(grants);
	}

	private static Path rootFolder(final JsonNode path, final Path file, final List<String> problems) {
		if (path == null || path.isNull()) {
			problems.add("no '" + PATH + "' set: the root folder of the repository");
			return null;
		}
		if (!path.isString()) {
			problems.add("'" + PATH + "' must be a text: the root folder of the repository");
			return null;
		}
		final Path folder = file.toAbsolutePath().getParent().resolve(path.asString()).normalize();
		if (!Files.exists(folder)) {
			problems.add("root folder " + folder + " does not exist");
		} else if (!Files.isDirectory(folder)) {
			problems.add("root folder " + folder + " is not a folder");
		}
		return folder;
	}
}
