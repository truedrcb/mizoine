package com.gratchev.mizoine.repositories;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @param grants username → granted folders; every user named in {@code access}, also one with Git access only
 * @param gitUsers users with the grant {@code .git} (step 43, D33); grant {@code /} includes Git access too
 */
public record RepositoryConfig(String name, Path root, Map<String, List<FolderGrant>> grants, Set<String> gitUsers,
		List<String> problems) {

	public RepositoryConfig {
		gitUsers = Set.copyOf(gitUsers);
	}
}
