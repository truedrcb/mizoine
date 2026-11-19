package com.gratchev.mizoine.repositories;

import java.util.List;

/**
 * The subfolders and files of a repository folder that the user may see.
 *
 * @param repository repository name
 * @param path the folder, from the repository root, with leading and trailing slash ("/" = root)
 * @param folders names of the visible subfolders, sorted; no hidden folders
 * @param files names of the visible files, sorted; no hidden files; none above a granted folder (D4)
 */
public record FolderListing(String repository, String path, List<String> folders, List<String> files) {
}
