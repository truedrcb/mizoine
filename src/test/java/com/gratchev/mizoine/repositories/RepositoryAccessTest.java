package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		return new RepositoryConfig("notes", Path.of("/data/notes"), grants, Set.of("editor", "git-only"), List.of());
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

	// files

	@Test
	void filesInsideAGrantedFolderAreVisible() {
		final RepositoryAccess access = access(repository("guest", "projects/shared"), "guest");

		assertThat(access.visibleFiles(folder("projects/shared"), List.of("a.md", "b.md"))).containsExactly("a.md", "b.md");
		assertThat(access.visibleFiles(folder("projects/shared/deep"), List.of("c.md"))).containsExactly("c.md");
	}

	@Test
	void filesAboveAGrantedFolderAreNotVisible() {
		// only the way down is shown there (D4)
		final RepositoryAccess access = access(repository("guest", "projects/shared"), "guest");

		assertThat(access.visibleFiles(ROOT, List.of("readme.md"))).isEmpty();
		assertThat(access.visibleFiles(folder("projects"), List.of("plan.md"))).isEmpty();
	}

	@Test
	void filesOutsideTheGrantsAreNotVisible() {
		final RepositoryAccess access = access(repository("guest", "public"), "guest");

		assertThat(access.visibleFiles(folder("private"), List.of("secret.md"))).isEmpty();
	}

	@Test
	void fileIsReadableWhenItWouldBeListed() {
		final RepositoryAccess access = access(repository("guest", "projects/shared"), "guest");

		assertThat(access.canReadFile(folder("projects/shared/a.md"))).isTrue();
		assertThat(access.canReadFile(folder("projects/plan.md"))).isFalse();
		assertThat(access.canReadFile(folder("projects/shared/.hidden"))).isFalse();
		assertThat(access.canReadFile(ROOT)).isFalse();
	}

	// writing: inside a granted folder only (folders above a grant stay read-only, D4)

	@Test
	void writableInsideAGrantedFolder() {
		final RepositoryAccess access = access(repository("guest", "projects/shared"), "guest");

		assertThat(access.canWriteIn(folder("projects/shared"))).isTrue();
		assertThat(access.canWriteIn(folder("projects/shared/deep/er"))).isTrue();
		assertThat(access.canWriteIn(folder("projects"))).isFalse();
		assertThat(access.canWriteIn(ROOT)).isFalse();
		assertThat(access.canWriteIn(folder("private"))).isFalse();
		assertThat(access.canWriteIn(folder("projects/shared/.git"))).isFalse();
	}

	// Git access (step 43, D33)

	@Test
	void gitAccessComesWithTheWholeRepositoryOrItsOwnGrant() {
		final RepositoryConfig config = repository("artem", "/", "editor", "public", "guest", "public", "git-only", "");

		assertThat(access(config, "artem").canUseGit()).isTrue();
		assertThat(access(config, "editor").canUseGit()).isTrue();
		assertThat(access(config, "guest").canUseGit()).isFalse();
		assertThat(access(config, "nobody").canUseGit()).isFalse();
		assertThat(access(config, null).canUseGit()).isFalse();
	}

	@Test
	void withGitAccessAloneOnlyTheWayToGitIsOpen() {
		// like above a granted folder (D4): the root may be entered, it shows nothing
		final RepositoryAccess access = access(repository("git-only", ""), "git-only");

		assertThat(access.hasAccess()).isTrue();
		assertThat(access.canEnter(ROOT)).isTrue();
		assertThat(access.visibleChildren(ROOT, List.of("public", ".git"))).isEmpty();
		assertThat(access.visibleFiles(ROOT, List.of("README.md"))).isEmpty();
		assertThat(access.canEnter(folder("public"))).isFalse();
		assertThat(access.canEnter(folder(".git"))).isFalse();
		assertThat(access.canWriteIn(ROOT)).isFalse();
	}

	@Test
	void entriesMayBeChangedInWritableFoldersOnly() {
		final RepositoryAccess access = access(repository("guest", "projects/shared"), "guest");

		assertThat(access.canChange(folder("projects/shared/plan.md"))).isTrue();
		assertThat(access.canChange(folder("projects/shared"))).isFalse();
		assertThat(access.canChange(ROOT)).isFalse();
		assertThat(access.canChange(folder("private/plan.md"))).isFalse();
	}

	@Test
	void theTopOfAGrantIsKnown() {
		// such a folder cannot be deleted by the user: it would remove the grant's place (step 32)
		final RepositoryAccess access = access(repository("guest", "projects/shared"), "guest");

		assertThat(access.isGrantedFolder(folder("projects/shared"))).isTrue();
		assertThat(access.isGrantedFolder(folder("projects/shared/deep"))).isFalse();
		assertThat(access(repository("artem", "/"), "artem").isGrantedFolder(ROOT)).isTrue();
	}

	@Test
	void wholeRepositoryIsWritableExceptHiddenFolders() {
		final RepositoryAccess access = access(repository("artem", "/"), "artem");

		assertThat(access.canWriteIn(ROOT)).isTrue();
		assertThat(access.canWriteIn(folder(".git"))).isFalse();
		assertThat(access.canWriteIn(folder("a/100%"))).isFalse();
	}

	@Test
	void hiddenFilesAreNeverVisible() {
		final RepositoryAccess access = access(repository("artem", "/"), "artem");

		assertThat(access.visibleFiles(ROOT, List.of(".gitignore", "notes.md", "100%.md"))).containsExactly("notes.md");
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
