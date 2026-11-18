package com.gratchev.mizoine.repositories;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record RepositoryConfig(String name, Path root, Map<String, List<FolderGrant>> grants, List<String> problems) {
}
