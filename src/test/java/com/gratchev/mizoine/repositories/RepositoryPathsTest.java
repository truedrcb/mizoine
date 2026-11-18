package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Step 20: paths below a repository root never lead outside it. */
class RepositoryPathsTest {

	private final Path root;
	private final Path outside;

	RepositoryPathsTest(@TempDir final Path dir) throws IOException {
		this.root = Files.createDirectories(dir.resolve("repo"));
		this.outside = Files.createDirectories(dir.resolve("outside/secret"));
		Files.createDirectories(root.resolve("a/b"));
		Files.createDirectories(root.resolve(".git/objects"));
		Files.createDirectories(root.resolve("a/.hidden"));
		Files.writeString(root.resolve("a/file.txt"), "x");
	}

	private static List<String> segments(final String... segments) {
		return List.of(segments);
	}

	// 1. inside

	@Test
	void folderInsideTheRootResolves() throws IOException {
		assertThat(RepositoryPaths.resolveFolder(root, segments("a", "b"))).contains(root.resolve("a/b").toRealPath());
	}

	@Test
	void noSegmentsIsTheRoot() throws IOException {
		assertThat(RepositoryPaths.resolveFolder(root, segments())).contains(root.toRealPath());
	}

	// 2. unsafe segments — rejected even when the target exists

	@ParameterizedTest
	@ValueSource(strings = {"..", ".", "", "a/b", "a\\b", "a\0b"})
	void unsafeSegmentsAreRejected(final String segment) {
		assertThat(RepositoryPaths.resolveFolder(root, segments(segment))).isEmpty();
		assertThat(RepositoryPaths.resolveFolder(root, segments("a", segment))).isEmpty();
	}

	@Test
	void climbingOutWithSeveralSegmentsIsRejected() {
		assertThat(RepositoryPaths.resolveFolder(root, segments("a", "..", "..", "outside"))).isEmpty();
	}

	// 3. symbolic links

	@Test
	void symbolicLinkPointingOutsideIsTreatedAsNotExisting() throws IOException {
		Files.createSymbolicLink(root.resolve("escape"), outside);

		assertThat(RepositoryPaths.resolveFolder(root, segments("escape"))).isEmpty();
	}

	@Test
	void symbolicLinkFurtherUpThePathIsCaughtToo() throws IOException {
		Files.createSymbolicLink(root.resolve("a/escape"), outside.getParent());

		assertThat(RepositoryPaths.resolveFolder(root, segments("a", "escape", "secret"))).isEmpty();
	}

	@Test
	void symbolicLinkStayingInsideTheRootWorks() throws IOException {
		Files.createSymbolicLink(root.resolve("shortcut"), root.resolve("a/b"));

		assertThat(RepositoryPaths.resolveFolder(root, segments("shortcut"))).contains(root.resolve("a/b").toRealPath());
	}

	@Test
	void symbolicLinkToAHiddenFolderDoesNotMakeItReachable() throws IOException {
		Files.createSymbolicLink(root.resolve("git"), root.resolve(".git"));

		assertThat(RepositoryPaths.resolveFolder(root, segments("git"))).isEmpty();
		assertThat(RepositoryPaths.resolveFolder(root, segments("git", "objects"))).isEmpty();
		assertThat(RepositoryPaths.childFolders(root, segments())).doesNotContain("git");
	}

	@Test
	void rootThatIsASymbolicLinkWorks() throws IOException {
		final Path linkedRoot = Files.createSymbolicLink(root.resolveSibling("linked"), root);

		assertThat(RepositoryPaths.resolveFolder(linkedRoot, segments("a"))).contains(root.resolve("a").toRealPath());
	}

	// 4. hidden (D3)

	@Test
	void hiddenFoldersAreNotReachable() {
		assertThat(RepositoryPaths.resolveFolder(root, segments(".git"))).isEmpty();
		assertThat(RepositoryPaths.resolveFolder(root, segments(".git", "objects"))).isEmpty();
		assertThat(RepositoryPaths.resolveFolder(root, segments("a", ".hidden"))).isEmpty();
	}

	// 5. not a folder

	@Test
	void notExistingFolderIsEmpty() {
		assertThat(RepositoryPaths.resolveFolder(root, segments("nothing"))).isEmpty();
	}

	@Test
	void fileIsNotAFolder() {
		assertThat(RepositoryPaths.resolveFolder(root, segments("a", "file.txt"))).isEmpty();
	}

	@Test
	void missingRootGivesNothing() {
		assertThat(RepositoryPaths.resolveFolder(root.resolveSibling("gone"), segments())).isEmpty();
		assertThat(RepositoryPaths.childFolders(root.resolveSibling("gone"), segments())).isEmpty();
	}

	// child folders, with the same rules

	@Test
	void childFoldersAreOnlySafeVisibleFoldersSortedByName() throws IOException {
		Files.createDirectories(root.resolve("c"));
		Files.createDirectories(root.resolve("B"));
		Files.createSymbolicLink(root.resolve("escape"), outside);
		Files.createSymbolicLink(root.resolve("shortcut"), root.resolve("a/b"));
		Files.writeString(root.resolve("readme.md"), "x");

		assertThat(RepositoryPaths.childFolders(root, segments())).containsExactly("B", "a", "c", "shortcut");
	}

	@Test
	void childFoldersOfAnUnsafeOrMissingFolderAreEmpty() {
		assertThat(RepositoryPaths.childFolders(root, segments(".git"))).isEmpty();
		assertThat(RepositoryPaths.childFolders(root, segments(".."))).isEmpty();
		assertThat(RepositoryPaths.childFolders(root, segments("nothing"))).isEmpty();
	}

	@Test
	void childFoldersOfASubfolder() {
		assertThat(RepositoryPaths.childFolders(root, segments("a"))).containsExactly("b");
	}
}
