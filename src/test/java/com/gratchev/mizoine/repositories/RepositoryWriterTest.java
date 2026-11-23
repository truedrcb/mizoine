package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;

/** Step 30: writing a file — atomic, size-limited, with preconditions, never outside the root. */
class RepositoryWriterTest {

	private static final long LIMIT = 100;

	private final Path root;
	private final Path outside;
	private final RepositoryWriter writer = new RepositoryWriter();

	RepositoryWriterTest(@TempDir final Path dir) throws IOException {
		this.root = Files.createDirectories(dir.resolve("repo"));
		Files.createDirectories(root.resolve("a/sub"));
		Files.createDirectories(root.resolve(".git"));
		Files.writeString(root.resolve("a/old.md"), "old");
		this.outside = Files.writeString(dir.resolve("outside.txt"), "outside");
		Files.createSymbolicLink(root.resolve("a/leak.txt"), outside);
	}

	private Result write(final String path, final String content, final Precondition precondition) {
		return writer.write(root, List.of(path.split("/")), body(content), LIMIT, precondition);
	}

	private static InputStream body(final String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	private String etagOf(final String path) throws IOException {
		return FileDownload.etag(Files.readAttributes(root.resolve(path), BasicFileAttributes.class));
	}

	private List<String> namesIn(final String folder) throws IOException {
		try (Stream<Path> entries = Files.list(root.resolve(folder))) {
			return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
		}
	}

	// 1. create and replace

	@Test
	void newFileIsCreatedWithItsEtag() throws IOException {
		final Result result = write("a/new.md", "# Ü\r\n", Precondition.NONE);

		assertThat(Files.readString(root.resolve("a/new.md"))).isEqualTo("# Ü\r\n");
		assertThat(result).isEqualTo(new Result.Created(etagOf("a/new.md")));
	}

	@Test
	void existingFileIsReplaced() throws IOException {
		final Result result = write("a/old.md", "new", Precondition.NONE);

		assertThat(Files.readString(root.resolve("a/old.md"))).isEqualTo("new");
		assertThat(result).isEqualTo(new Result.Replaced(etagOf("a/old.md")));
	}

	@Test
	void writingTheSameTwiceEndsInTheSameContent() throws IOException {
		write("a/twice.md", "same", Precondition.NONE);
		write("a/twice.md", "same", Precondition.NONE);

		assertThat(Files.readString(root.resolve("a/twice.md"))).isEqualTo("same");
	}

	// 2. preconditions (D13)

	@Test
	void ifMatchWithTheCurrentEtagWrites() throws IOException {
		final Result result = write("a/old.md", "new", Precondition.ifMatch(etagOf("a/old.md")));

		assertThat(result).isInstanceOf(Result.Replaced.class);
	}

	/**
	 * Compressing proxies and CDNs (Cloudflare, nginx with gzip) turn the strong ETag into a weak one on the way
	 * (bug 2026-11-23: behind Cloudflare every save was a conflict). The tag value still names the version.
	 */
	@Test
	void ifMatchWithTheCurrentEtagMadeWeakByAProxyWrites() throws IOException {
		final Result result = write("a/old.md", "new", Precondition.ifMatch("W/" + etagOf("a/old.md")));

		assertThat(result).isInstanceOf(Result.Replaced.class);
	}

	@Test
	void anOldEtagMadeWeakStillChangesNothing() throws IOException {
		assertThat(write("a/old.md", "new", Precondition.ifMatch("W/\"outdated\""))).isEqualTo(new Result.PreconditionFailed());
		assertThat(Files.readString(root.resolve("a/old.md"))).isEqualTo("old");
	}

	@Test
	void deleteWithTheCurrentEtagMadeWeakByAProxyDeletes() throws IOException {
		assertThat(delete("a/old.md", false, Optional.of("W/" + etagOf("a/old.md")))).isEqualTo(new DeleteResult.Deleted());
	}

	@Test
	void ifMatchWithAnOldEtagChangesNothing() throws IOException {
		final Result result = write("a/old.md", "new", Precondition.ifMatch("\"outdated\""));

		assertThat(result).isEqualTo(new Result.PreconditionFailed());
		assertThat(Files.readString(root.resolve("a/old.md"))).isEqualTo("old");
	}

	@Test
	void ifMatchOnAMissingFileFails() {
		assertThat(write("a/missing.md", "x", Precondition.ifMatch("\"any\""))).isEqualTo(new Result.PreconditionFailed());
		assertThat(Files.exists(root.resolve("a/missing.md"))).isFalse();
	}

	@Test
	void createOnlyFailsWhenTheFileExists() throws IOException {
		assertThat(write("a/old.md", "new", Precondition.CREATE_ONLY)).isEqualTo(new Result.PreconditionFailed());
		assertThat(Files.readString(root.resolve("a/old.md"))).isEqualTo("old");
		assertThat(write("a/fresh.md", "x", Precondition.CREATE_ONLY)).isInstanceOf(Result.Created.class);
	}

	// 3. atomic, size-limited

	@Test
	void tooLargeWritesNothingAndLeavesNoTemporaryFile() throws IOException {
		final Result result = write("a/old.md", "x".repeat((int) LIMIT + 1), Precondition.NONE);

		assertThat(result).isEqualTo(new Result.TooLarge());
		assertThat(Files.readString(root.resolve("a/old.md"))).isEqualTo("old");
		assertThat(namesIn("a")).containsExactly("leak.txt", "old.md", "sub");
	}

	@Test
	void exactlyTheLimitIsAllowed() {
		assertThat(write("a/full.md", "x".repeat((int) LIMIT), Precondition.NONE)).isInstanceOf(Result.Created.class);
	}

	@Test
	void failingUploadLeavesTheOldContentAndNoTemporaryFile() throws IOException {
		final InputStream broken = new InputStream() {
			private int left = 10;

			@Override
			public int read() throws IOException {
				if (left <= 0) {
					throw new IOException("connection lost");
				}
				left--;
				return 'x';
			}
		};

		assertThatThrownBy(() -> writer.write(root, List.of("a", "old.md"), broken, LIMIT, Precondition.NONE))
				.isInstanceOf(UncheckedIOException.class);
		assertThat(Files.readString(root.resolve("a/old.md"))).isEqualTo("old");
		assertThat(namesIn("a")).containsExactly("leak.txt", "old.md", "sub");
	}

	@Test
	void successfulWriteLeavesNoTemporaryFile() throws IOException {
		write("a/new.md", "x", Precondition.NONE);

		assertThat(namesIn("a")).containsExactly("leak.txt", "new.md", "old.md", "sub");
	}

	// 4. where it cannot write

	@Test
	void missingParentFolderIsAConflict() {
		assertThat(write("a/nothing/new.md", "x", Precondition.NONE)).isEqualTo(new Result.ParentMissing());
		assertThat(Files.exists(root.resolve("a/nothing"))).isFalse();
	}

	@Test
	void folderAtThePathIsAConflict() {
		assertThat(write("a/sub", "x", Precondition.NONE)).isEqualTo(new Result.IsFolder());
	}

	@Test
	void nothingOutsideTheRootOrHidden() throws IOException {
		assertThat(write("a/leak.txt", "x", Precondition.NONE)).isEqualTo(new Result.NotFound());
		assertThat(Files.readString(outside)).isEqualTo("outside");
		assertThat(write(".git/config", "x", Precondition.NONE)).isEqualTo(new Result.NotFound());
		assertThat(write("a/.hidden", "x", Precondition.NONE)).isEqualTo(new Result.NotFound());
		assertThat(write("a/100%.md", "x", Precondition.NONE)).isEqualTo(new Result.NotFound());
		assertThat(writer.write(root, List.of("..", "outside.txt"), body("x"), LIMIT, Precondition.NONE))
				.isEqualTo(new Result.NotFound());
		assertThat(writer.write(root, List.of(), body("x"), LIMIT, Precondition.NONE)).isEqualTo(new Result.NotFound());
	}

	// step 31: folders (D14: only explicitly)

	private FolderResult createFolder(final String path, final boolean createOnly) {
		return writer.createFolder(root, List.of(path.split("/")), createOnly);
	}

	@Test
	void folderIsCreated() {
		assertThat(createFolder("a/new", false)).isEqualTo(new FolderResult.Created());
		assertThat(root.resolve("a/new")).isDirectory();
	}

	@Test
	void existingFolderChangesNothingUnlessCreateOnly() {
		assertThat(createFolder("a/sub", false)).isEqualTo(new FolderResult.Exists());
		assertThat(createFolder("a/sub", true)).isEqualTo(new FolderResult.PreconditionFailed());
	}

	@Test
	void folderNeedsItsParent() {
		assertThat(createFolder("a/x/y", false)).isEqualTo(new FolderResult.ParentMissing());
		assertThat(root.resolve("a/x")).doesNotExist();
	}

	@Test
	void fileInTheWayIsAConflict() {
		assertThat(createFolder("a/old.md", false)).isEqualTo(new FolderResult.FileInTheWay());
	}

	@Test
	void noHiddenFoldersAndNothingOutside() {
		assertThat(createFolder("a/.hidden", false)).isEqualTo(new FolderResult.NotFound());
		assertThat(createFolder(".git/x", false)).isEqualTo(new FolderResult.NotFound());
		assertThat(createFolder("a/leak.txt", false)).isEqualTo(new FolderResult.NotFound());
		assertThat(writer.createFolder(root, List.of(), false)).isEqualTo(new FolderResult.NotFound());
		assertThat(root.resolve("a/.hidden")).doesNotExist();
	}

	// step 32: delete (D15: folders only when empty)

	private DeleteResult delete(final String path, final boolean folder, final Optional<String> ifMatch) {
		return writer.delete(root, List.of(path.split("/")), folder, ifMatch);
	}

	@Test
	void fileIsDeleted() {
		assertThat(delete("a/old.md", false, Optional.empty())).isEqualTo(new DeleteResult.Deleted());
		assertThat(root.resolve("a/old.md")).doesNotExist();
	}

	@Test
	void fileIsDeletedOnlyIfUnchanged() throws IOException {
		assertThat(delete("a/old.md", false, Optional.of("\"outdated\""))).isEqualTo(new DeleteResult.PreconditionFailed());
		assertThat(root.resolve("a/old.md")).exists();
		assertThat(delete("a/old.md", false, Optional.of(etagOf("a/old.md")))).isEqualTo(new DeleteResult.Deleted());
	}

	@Test
	void emptyFolderIsDeleted() throws IOException {
		Files.createDirectories(root.resolve("a/empty"));

		assertThat(delete("a/empty", true, Optional.empty())).isEqualTo(new DeleteResult.Deleted());
		assertThat(root.resolve("a/empty")).doesNotExist();
	}

	@Test
	void folderWithContentIsNotDeleted() {
		assertThat(delete("a", true, Optional.empty())).isEqualTo(new DeleteResult.NotEmpty());
		assertThat(root.resolve("a/old.md")).exists();
	}

	@Test
	void hiddenContentCountsToo() throws IOException {
		// the user cannot see what would be lost
		Files.createDirectories(root.resolve("a/only-hidden"));
		Files.writeString(root.resolve("a/only-hidden/.gitkeep"), "");

		assertThat(delete("a/only-hidden", true, Optional.empty())).isEqualTo(new DeleteResult.NotEmpty());
	}

	@Test
	void kindMustMatchTheUrl() {
		assertThat(delete("a/old.md", true, Optional.empty())).isEqualTo(new DeleteResult.NotFound());
		assertThat(delete("a/sub", false, Optional.empty())).isEqualTo(new DeleteResult.NotFound());
		assertThat(root.resolve("a/old.md")).exists();
		assertThat(root.resolve("a/sub")).isDirectory();
	}

	@Test
	void deletingALinkKeepsItsTarget() throws IOException {
		Files.createSymbolicLink(root.resolve("a/alias.md"), root.resolve("a/old.md"));

		assertThat(delete("a/alias.md", false, Optional.empty())).isEqualTo(new DeleteResult.Deleted());
		assertThat(root.resolve("a/alias.md")).doesNotExist();
		assertThat(Files.readString(root.resolve("a/old.md"))).isEqualTo("old");
	}

	@Test
	void deletingALinkToAFolderKeepsTheFolder() throws IOException {
		Files.createSymbolicLink(root.resolve("a/shortcut"), root.resolve("a/sub"));
		Files.writeString(root.resolve("a/sub/kept.md"), "x");

		assertThat(delete("a/shortcut", true, Optional.empty())).isEqualTo(new DeleteResult.Deleted());
		assertThat(root.resolve("a/sub/kept.md")).exists();
	}

	@Test
	void underALinkLeadingOutsideNothingIsWrittenAndItIsNotAConflict() {
		assertThat(write("a/leak.txt/new.md", "x", Precondition.NONE)).isEqualTo(new Result.NotFound());
		assertThat(createFolder("a/leak.txt/new", false)).isEqualTo(new FolderResult.NotFound());
	}

	@Test
	void nothingHiddenMissingOrOutsideIsDeleted() throws IOException {
		Files.writeString(root.resolve("a/.hidden"), "x");

		assertThat(delete("a/.hidden", false, Optional.empty())).isEqualTo(new DeleteResult.NotFound());
		assertThat(delete("a/missing.md", false, Optional.empty())).isEqualTo(new DeleteResult.NotFound());
		assertThat(delete("a/leak.txt", false, Optional.empty())).isEqualTo(new DeleteResult.NotFound());
		assertThat(delete(".git", true, Optional.empty())).isEqualTo(new DeleteResult.NotFound());
		assertThat(writer.delete(root, List.of(), true, Optional.empty())).isEqualTo(new DeleteResult.NotFound());
		assertThat(root.resolve("a/.hidden")).exists();
		assertThat(outside).exists();
	}
}
