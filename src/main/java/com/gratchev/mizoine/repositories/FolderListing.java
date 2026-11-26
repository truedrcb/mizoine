package com.gratchev.mizoine.repositories;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The subfolders and files of a repository folder that the user may see.
 *
 * @param repository repository name
 * @param path the folder, from the repository root, with leading and trailing slash ("/" = root)
 * @param folders names of the visible subfolders, sorted; no hidden folders
 * @param files names of the visible files, sorted; no hidden files; none above a granted folder (D4)
 * @param tags the folder's own tags (step 62); none for a folder only on the way to a grant
 * @param folderTags the tags of the subfolders that have any, by name
 * @param editable the files of a known text format, which open in the browser's editor (step 68, D73)
 * @param writable whether the user may write in this folder: create, replace, move and delete in it (step 68)
 */
@Schema(description = "The subfolders and files of a folder that the user may see")
public record FolderListing(
		@Schema(description = "Repository name") String repository,
		@Schema(description = "The folder, from the repository root, with leading and trailing '/' ('/' = the root)")
		String path,
		@Schema(description = "Names of the visible subfolders, sorted") List<String> folders,
		@Schema(description = "Names of the visible files, sorted; none in folders above a granted folder")
		List<String> files,
		@Schema(description = "The folder's own tags; none in folders above a granted folder") List<String> tags,
		@Schema(description = "The tags of each subfolder that has any, by its name") Map<String, List<String>> folderTags,
		@Schema(description = "The files of a known text format, which can be edited in the browser") List<String> editable,
		@Schema(description = "Whether the user may write in this folder: create, replace, move and delete files in it")
		boolean writable) {
}
