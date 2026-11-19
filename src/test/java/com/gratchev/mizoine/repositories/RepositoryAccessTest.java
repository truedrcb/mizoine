package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Step 17: who may enter which folder, and which subfolders they see. */
class RepositoryAccessTest {

	private static final List<String> ROOT = List.of();

	private static List<String> folder(final String path) {
		return Arrays.stream(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
	}

	private static RepositoryConfig repository(final String... userAndFolders) {
		final Map<String, List<FolderGrant>> grants = new LinkedHashMap<>();
		for (int i = 0; i < userAndFolders.length; i += 2) {
			grants.put(userAndFolders[i], Arrays.stream(userAndFolders[i + 1].split(","))
					.filter(folder -> !folder.isEmpty())
					.map(FolderGrant::of)
					.toList());
		}
		return new RepositoryConfig("notes", Path.of("/data/notes"), grants, List.of());
	}

	private static RepositoryAccess access(final RepositoryConfig config, final String user) {
		return RepositoryAccess.of(config, user);
	}

	// 1. whole repository

	@Test
	void wholeRepositoryGrantEntersEverything() {
		final RepositoryAccess access = access(repository("artem", "/"), "artem");

		assertThat(access.hasAccess()).isTrue();
		assertThat(access.isWholeRepository()).isTrue();
		assertThat(access.canEnter(ROOT)).isTrue();
		assertThat(access.canEnter(folder("a/b/c"))).isTrue();
		assertThat(access.visibleChildren(ROOT, List.of("a", "b"))).containsExactly("a", "b");
	}

	// 2. one folder

	@Test
	void folderGrantIncludesEverythingBelow() {
		final RepositoryAccess access = access(repository("guest", "public"), "guest");

		assertThat(access.canEnter(folder("public"))).isTrue();
		assertThat(access.canEnter(folder("public/a/b"))).isTrue();
		assertThat(access.visibleChildren(folder("public/a"), List.of("x", "y"))).containsExactly("x", "y");
	}

	@Test
	void folderGrantDoesNotIncludeSimilarOrOtherFolders() {
		final RepositoryAccess access = access(repository("guest", "public"), "guest");

		assertThat(access.canEnter(folder("publicity"))).isFalse();
		assertThat(access.canEnter(folder("private"))).isFalse();
		assertThat(access.canEnter(folder("private/public"))).isFalse();
		assertThat(access.isWholeRepository()).isFalse();
	}

	// 3. folders above a granted one (D4)

	@Test
	void foldersAboveAGrantCanBeEnteredButShowOnlyTheWayDown() {
		final RepositoryAccess access = access(repository("guest", "projects/shared"), "guest");

		assertThat(access.canEnter(ROOT)).isTrue();
		assertThat(access.canEnter(folder("projects"))).isTrue();
		assertThat(access.canEnter(folder("projects/other"))).isFalse();
		assertThat(access.visibleChildren(ROOT, List.of("notes", "projects", "public"))).containsExactly("projects");
		assertThat(access.visibleChildren(folder("projects"), List.of("other", "shared"))).containsExactly("shared");
		assertThat(access.visibleChildren(folder("projects/shared"), List.of("a", "b"))).containsExactly("a", "b");
	}

	@Test
	void severalGrantsAddUp() {
		final RepositoryAccess access = access(repository("guest", "public,projects/shared"), "guest");

		assertThat(access.visibleChildren(ROOT, List.of("notes", "projects", "public"))).containsExactly("projects", "public");
		assertThat(access.canEnter(folder("public/x"))).isTrue();
		assertThat(access.canEnter(folder("projects/shared/x"))).isTrue();
		assertThat(access.canEnter(folder("projects/other"))).isFalse();
	}

	@Test
	void folderThatCannotBeEnteredShowsNothing() {
		final RepositoryAccess access = access(repository("guest", "public"), "guest");

		assertThat(access.visibleChildren(folder("private"), List.of("a"))).isEmpty();
	}

	// 4. no grant

	@Test
	void userWithoutGrantHasNoAccessAtAll() {
		final RepositoryConfig config = repository("artem", "/");

		final RepositoryAccess access = access(config, "guest");

		assertThat(access.hasAccess()).isFalse();
		assertThat(access.canEnter(ROOT)).isFalse();
		assertThat(access.visibleChildren(ROOT, List.of("a"))).isEmpty();
	}

	@Test
	void userWithEmptyFolderListHasNoAccess() {
		final RepositoryAccess access = access(repository("guest", ""), "guest");

		assertThat(access.hasAccess()).isFalse();
		assertThat(access.canEnter(ROOT)).isFalse();
	}

	@Test
	void anonymousHasNoAccess() {
		assertThat(access(repository("artem", "/"), null).hasAccess()).isFalse();
	}

	// 5. case-sensitive

	@Test
	void usernamesAreCaseSensitive() {
		assertThat(access(repository("artem", "/"), "Artem").hasAccess()).isFalse();
	}

	@Test
	void folderNamesAreCaseSensitive() {
		final RepositoryAccess access = access(repository("guest", "public"), "guest");

		assertThat(access.canEnter(folder("Public"))).isFalse();
	}

	// hidden folders (D3)

	@Test
	void hiddenFoldersAreNeverVisibleNorEnterable() {
		final RepositoryAccess access = access(repository("artem", "/"), "artem");

		assertThat(access.visibleChildren(ROOT, List.of(".git", "notes", ".mizoine"))).containsExactly("notes");
		assertThat(access.canEnter(folder(".git"))).isFalse();
		assertThat(access.canEnter(folder("notes/.hidden/x"))).isFalse();
	}

	@Test
	void foldersWithPercentAreHiddenToo() {
		// same rule as RepositoryPaths: the firewall rejects an encoded '%', so such folders could not be opened
		final RepositoryAccess access = access(repository("artem", "/"), "artem");

		assertThat(access.visibleChildren(ROOT, List.of("100%", "notes", "a%20b"))).containsExactly("notes");
		assertThat(access.canEnter(folder("100%"))).isFalse();
		assertThat(access.canEnter(folder("notes/a%b/x"))).isFalse();
	}
}
