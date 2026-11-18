package com.gratchev.mizoine.repositories;

import java.util.List;

/**
 * The subfolders of a repository folder that the user may see.
 *
 * @param repository repository name
 * @param path the folder, from the repository root, with leading and trailing slash ("/" = root)
 * @param folders names of the visible subfolders, sorted; no files, no hidden folders
 */
public record FolderListing(String repository, String path, List<String> folders) {
}
