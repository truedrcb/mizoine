package com.gratchev.mizoine.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gratchev.mizoine.repositories.RepositoryWriter.DeleteResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.FolderResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.MoveResult;
import com.gratchev.mizoine.repositories.RepositoryWriter.Precondition;
import com.gratchev.mizoine.repositories.RepositoryWriter.Result;

/**
 * Security: a user creates, changes, moves and deletes only inside the folders granted to it. Every write
 * operation against every kind of forbidden place, through {@link RepositoryFiles}, the one layer the HTTP file API
 * and the MCP tools both use; paths are parsed as the MCP tools parse them ({@link RepositoryFiles#segments}).
 * <p>
 * A refusal counts only if <em>nothing</em> changed: a snapshot of the whole tree, and of the folder outside the
 * repository, is compared before and after. Allowed operations are checked too, so the matrix cannot pass by
 * refusing everything.
 * <p>
 * guest's grants: {@code granted} and {@code deep/granted}, so {@code deep} is a folder above a grant (D4).
 */
class WritePermissionMatrixTest {

	private static final String USER = "guest";
	private static final long LIMIT = 1024;

	private final Path base;
	private final RepositoryFiles files;

	WritePermissionMatrixTest(@TempDir final Path base) throws IOException {
		this.base = base;
		for (final String folder : new String[] {"tree/granted/sub", "tree/granted/empty", "tree/granted/.hidden",
				"tree/deep/granted", "tree/private/folder", "tree/.git", "outside/folder", "elsewhere/inside"}) {
			Files.createDirectories(base.resolve(folder));
		}
		for (final String file : new String[] {"tree/granted/file.md", "tree/granted/sub/x.md", "tree/granted/.hidden-file",
				"tree/granted/.hidden/h.md", "tree/deep/above.md", "tree/deep/granted/d.md", "tree/private/secret.md",
				"tree/private/folder/f.md", "tree/root.md", "tree/.git/config", "outside/o.md", "elsewhere/inside/e.md"}) {
			Files.writeString(base.resolve(file), "content of " + file);
		}
		Files.createSymbolicLink(base.resolve("tree/granted/link-out"), base.resolve("outside"));
		Files.createSymbolicLink(base.resolve("tree/granted/link-file"), base.resolve("outside/o.md"));
		Files.createSymbolicLink(base.resolve("tree/granted/link-private"), base.resolve("tree/private"));
		Files.createSymbolicLink(base.resolve("tree/granted/link-secret"), base.resolve("tree/private/secret.md"));
		Files.writeString(base.resolve("tree.yaml"), "path: tree\naccess:\n  guest: [granted, deep/granted]\n");
		Files.writeString(base.resolve("elsewhere.yaml"), "path: elsewhere\naccess:\n  artem: [\"/\"]\n");
		this.files = new RepositoryFiles(new RepositoryRegistry(base, new RepositoryFileParser(), user -> true, null),
				new RepositoryWriter());
	}

	/** Every entry below the base folder: type, link target or content hash. */
	private Map<String, String> snapshot() {
		try (Stream<Path> paths = Files.walk(base)) {
			final Map<String, String> entries = new TreeMap<>();
			paths.forEach(path -> entries.put(base.relativize(path).toString(), describe(path)));
			return entries;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String describe(final Path path) {
		try {
			if (Files.isSymbolicLink(path)) {
				return "link to " + Files.readSymbolicLink(path);
			}
			return Files.isDirectory(path) ? "folder" : "file "
					+ HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
		} catch (final IOException | java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The operation is refused (not visible, or a refusing result) and nothing anywhere changed. */
	private void assertRefusedWithoutChange(final String what, final Supplier<Object> operation) {
		final Map<String, String> before = snapshot();
		Object result;
		try {
			result = operation.get();
		} catch (final RepositoryFiles.NotVisibleException e) {
			result = "not visible";
		}
		assertThat(result).as(what).isNotIn(new Result.Created(""), new FolderResult.Created(), new DeleteResult.Deleted(),
				new MoveResult.Moved());
		assertThat(result).as(what).isNotInstanceOfAny(Result.Created.class, Result.Replaced.class);
		assertThat(snapshot()).as(what + ": nothing may change").isEqualTo(before);
	}

	private Object write(final String repository, final String path) {
		return files.write(USER, repository, RepositoryFiles.segments(path),
				new ByteArrayInputStream("intruder".getBytes(StandardCharsets.UTF_8)), LIMIT, Precondition.NONE);
	}

	private Object createFolder(final String repository, final String path) {
		return files.createFolder(USER, repository, RepositoryFiles.segments(path), false);
	}

	private Object delete(final String repository, final String path) {
		return files.delete(USER, repository, RepositoryFiles.segments(path), RepositoryFiles.isFolder(path), Optional.empty());
	}

	private Object move(final String from, final String to) {
		return files.move(USER, "tree", RepositoryFiles.segments(from), RepositoryFiles.segments(to),
				RepositoryFiles.isFolder(from), Optional.empty());
	}

	// controls: inside the grants everything works, so the refusals below mean something

	@Test
	void insideTheGrantsEverythingIsAllowed() throws IOException {
		assertThat(write("tree", "granted/new.md")).isInstanceOf(Result.Created.class);
		assertThat(write("tree", "deep/granted/new.md")).isInstanceOf(Result.Created.class);
		assertThat(write("tree", "granted/file.md")).isInstanceOf(Result.Replaced.class);
		assertThat(createFolder("tree", "granted/made/")).isEqualTo(new FolderResult.Created());
		assertThat(move("granted/sub/x.md", "deep/granted/x.md")).isEqualTo(new MoveResult.Moved());
		assertThat(move("granted/sub/", "deep/granted/sub/")).isEqualTo(new MoveResult.Moved());
		assertThat(delete("tree", "granted/empty/")).isEqualTo(new DeleteResult.Deleted());
		assertThat(delete("tree", "granted/new.md")).isEqualTo(new DeleteResult.Deleted());
		assertThat(Files.readString(base.resolve("tree/granted/file.md"))).isEqualTo("intruder");
	}

	// creating and replacing files

	@ParameterizedTest
	@ValueSource(strings = {"private/new.md", "private/secret.md", "deep/new.md", "deep/above.md", "root.md", "new.md",
			".git/config", ".git/new", "granted/.hidden/new.md", "granted/.hidden-file", "granted/.new.md",
			"granted/link-out/new.md", "granted/link-out/o.md", "granted/link-file", "granted/link-private/secret.md",
			"granted/link-secret", "granted/../private/new.md", "../outside/new.md", "../tree.yaml", "granted/%2e%2e/private/new.md",
			"granted/100%.md", "granted\\\\..\\\\private\\\\new.md", "granted/", "/"})
	void noFileIsWrittenOutsideTheGrants(final String path) {
		assertRefusedWithoutChange("write " + path, () -> write("tree", path));
	}

	@Test
	void noFileIsWrittenInARepositoryWithoutGrant() {
		assertRefusedWithoutChange("write elsewhere/inside/new.md", () -> write("elsewhere", "inside/new.md"));
		assertRefusedWithoutChange("write unknown repository", () -> write("nothing", "new.md"));
	}

	// creating folders

	@ParameterizedTest
	@ValueSource(strings = {"private/new/", "deep/new/", "new/", ".git/new/", "granted/.hidden/new/", "granted/.new/",
			"granted/link-out/new/", "granted/link-private/new/", "granted/../private/new/", "../outside/new/", "/"})
	void noFolderIsCreatedOutsideTheGrants(final String path) {
		assertRefusedWithoutChange("create folder " + path, () -> createFolder("tree", path));
	}

	@Test
	void noFolderIsCreatedInARepositoryWithoutGrant() {
		assertRefusedWithoutChange("create folder elsewhere", () -> createFolder("elsewhere", "inside/new/"));
	}

	// deleting

	@ParameterizedTest
	@ValueSource(strings = {"private/secret.md", "private/folder/", "private/", "deep/above.md", "deep/", "deep/granted/",
			"granted/", "root.md", "/", ".git/config", ".git/", "granted/.hidden-file", "granted/.hidden/",
			"granted/.hidden/h.md", "granted/link-out/o.md", "granted/link-private/secret.md", "granted/../private/secret.md",
			"../outside/o.md", "../tree.yaml", "granted/sub/"})
	void nothingIsDeletedOutsideTheGrantsNorGrantedFoldersNorFoldersWithContent(final String path) {
		assertRefusedWithoutChange("delete " + path, () -> delete("tree", path));
	}

	@Test
	void nothingIsDeletedInARepositoryWithoutGrant() {
		assertRefusedWithoutChange("delete elsewhere", () -> delete("elsewhere", "inside/e.md"));
	}

	// moving: neither out of nor into forbidden places, in both directions

	@ParameterizedTest
	@ValueSource(strings = {
			// out of the grants into them
			"private/secret.md>granted/secret.md", "private/folder/>granted/folder/", "deep/above.md>granted/above.md",
			"root.md>granted/root.md", ".git/config>granted/config", "granted/.hidden-file>granted/visible",
			"granted/link-out/o.md>granted/o.md", "../outside/o.md>granted/o.md",
			// from the grants out of them
			"granted/file.md>private/file.md", "granted/file.md>deep/file.md", "granted/file.md>file.md",
			"granted/file.md>.git/file.md", "granted/file.md>granted/.hidden.md", "granted/file.md>granted/.hidden/file.md",
			"granted/file.md>granted/link-out/file.md", "granted/file.md>granted/link-private/file.md",
			"granted/file.md>../outside/file.md", "granted/file.md>granted/../private/file.md", "granted/sub/>private/sub/",
			// the grants themselves, the root, into itself
			"granted/>private/granted/", "deep/granted/>granted/deep/", "/>granted/root/", "granted/sub/>granted/sub/deeper/"})
	void nothingIsMovedOutOfOrIntoForbiddenPlaces(final String fromAndTo) {
		final String[] parts = fromAndTo.split(">");
		assertRefusedWithoutChange("move " + fromAndTo, () -> move(parts[0], parts[1]));
	}

	@Test
	void aGrantedFolderReachedThroughALinkIsNeitherDeletedNorMoved() throws IOException {
		// a grant inside a grant: its parent is writable, but the grant itself must stay (step 32)
		Files.writeString(base.resolve("tree.yaml"), "path: tree\naccess:\n  guest: [granted, granted/empty]\n");
		Files.createSymbolicLink(base.resolve("tree/granted/link-here"), base.resolve("tree/granted"));

		assertRefusedWithoutChange("delete through a link", () -> delete("tree", "granted/link-here/empty/"));
		assertRefusedWithoutChange("move through a link", () -> move("granted/link-here/empty/", "granted/moved/"));
	}

	// reading through a link: the same rule (found by this matrix: grants were checked on the requested path only)

	@ParameterizedTest
	@ValueSource(strings = {"granted/link-private/secret.md", "granted/link-private/folder/f.md", "granted/link-secret"})
	void noFileIsReadThroughALinkToAFolderOutsideTheGrants(final String path) {
		// a read changes nothing, so "refused" must be the exception itself
		assertThatThrownBy(() -> files.readableFile(USER, "tree", RepositoryFiles.segments(path)))
				.as(path).isInstanceOf(RepositoryFiles.NotVisibleException.class);
	}

	@Test
	void noFolderIsListedThroughALinkToAFolderOutsideTheGrants() {
		assertThatThrownBy(() -> files.listFolder(USER, "tree", RepositoryFiles.segments("granted/link-private/")))
				.isInstanceOf(RepositoryFiles.NotVisibleException.class);
	}

	@Test
	void linksToPlacesOutsideTheGrantsAreNotListed() {
		final FolderListing listing = files.listFolder(USER, "tree", RepositoryFiles.segments("granted/"));

		assertThat(listing.folders()).containsExactly("empty", "sub");
		assertThat(listing.files()).containsExactly("file.md");
	}

	@Test
	void aLinkToAGrantedFolderStillWorks() throws IOException {
		Files.createSymbolicLink(base.resolve("tree/granted/link-deep"), base.resolve("tree/deep/granted"));

		assertThat(files.readableFile(USER, "tree", RepositoryFiles.segments("granted/link-deep/d.md"))).isNotNull();
		assertThat(write("tree", "granted/link-deep/via-link.md")).isInstanceOf(Result.Created.class);
		assertThat(base.resolve("tree/deep/granted/via-link.md")).exists();
	}
}
