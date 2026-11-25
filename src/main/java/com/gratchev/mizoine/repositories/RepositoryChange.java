package com.gratchev.mizoine.repositories;

import java.nio.file.Path;
import java.util.List;

/**
 * Mizoine changed something at this path: a write, a new folder, a delete, either side of a move (step 56). For
 * layers that keep something derived from the files, such as the search index; the files layer does not know them.
 *
 * @param path the real place below the root (links resolved), as segments
 */
public record RepositoryChange(String repository, Path root, List<String> path) {
}
