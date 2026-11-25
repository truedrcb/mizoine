package com.gratchev.mizoine.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Step 56: the index of one repository, on real files. "Found" asks the index for one word of one field, as the
 * search of step 57 will.
 */
class RepositoryIndexTest {

	private static final SearchAnalysis ANALYSIS = SearchAnalysis.of(Language.parse("en,de"));

	private final Path root;
	private final Path indexes;
	private final List<RepositoryIndex> opened = new ArrayList<>();

	RepositoryIndexTest(@TempDir final Path dir) throws IOException {
		this.root = Files.createDirectories(dir.resolve("notes"));
		this.indexes = dir.resolve("search-index").resolve("notes");
		write("projects/plan.md", "# Garden plan\n\nApples and pears.");
		write("projects/budget.xlsx", "\u0000binary");
		Files.createDirectories(root.resolve("projects/archive"));
		write(".hidden/secret.md", "apples");
		write("projects/.draft.md", "apples");
	}

	@AfterEach
	void close() {
		opened.forEach(RepositoryIndex::close);
	}

	private RepositoryIndex open(final SearchAnalysis analysis) {
		final RepositoryIndex index = RepositoryIndex.open("notes", indexes, analysis);
		opened.add(index);
		return index;
	}

	private RepositoryIndex indexed() {
		final RepositoryIndex index = open(ANALYSIS);
		index.reindex(root);
		return index;
	}

	private void write(final String path, final String content) throws IOException {
		final Path file = root.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	/** Paths of the entries with this word in this field; empty when the index cannot be searched. */
	private static List<String> found(final RepositoryIndex index, final String field, final String word) {
		try (IndexLease lease = index.lease().orElse(null)) {
			if (lease == null) {
				return List.of();
			}
			final IndexSearcher searcher = new IndexSearcher(lease.reader());
			final StoredFields stored = searcher.storedFields();
			final List<String> paths = new ArrayList<>();
			for (final ScoreDoc hit : searcher.search(new TermQuery(new Term(field,
					ANALYSIS.terms(field, word).getFirst())), 100).scoreDocs) {
				paths.add(stored.document(hit.doc).get(IndexDocuments.PATH));
			}
			return paths.stream().sorted().toList();
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void noIndexYet() {
		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.state().status()).isEqualTo(IndexStatus.NOT_INDEXED);
		assertThat(index.lease()).isEmpty();
		assertThat(index.details().files()).isZero();
	}

	@Test
	void reindexingFindsTextFilesNamesAndFoldersNeverHiddenOnes() {
		final RepositoryIndex index = indexed();

		assertThat(index.state().status()).isEqualTo(IndexStatus.INDEXED);
		assertThat(found(index, SearchAnalysis.EXACT, "apples")).containsExactly("projects/plan.md");
		assertThat(found(index, SearchAnalysis.TITLE, "garden")).containsExactly("projects/plan.md");
		// other files and folders by their names only
		assertThat(found(index, SearchAnalysis.NAME_PARTS, "budget")).containsExactly("projects/budget.xlsx");
		assertThat(found(index, SearchAnalysis.EXACT, "binary")).isEmpty();
		assertThat(found(index, SearchAnalysis.NAME, "archive")).containsExactly("projects/archive/");
		assertThat(found(index, SearchAnalysis.NAME_PARTS, "secret")).isEmpty();
		assertThat(found(index, SearchAnalysis.NAME_PARTS, "draft")).isEmpty();
	}

	@Test
	void linksAreNotFollowed() throws IOException {
		final Path outside = Files.createDirectories(root.getParent().resolve("outside"));
		Files.writeString(outside.resolve("other.md"), "elsewhere");
		Files.createSymbolicLink(root.resolve("link"), outside);
		Files.createSymbolicLink(root.resolve("linked.md"), root.resolve("projects/plan.md"));

		final RepositoryIndex index = indexed();

		assertThat(found(index, SearchAnalysis.EXACT, "elsewhere")).isEmpty();
		assertThat(found(index, SearchAnalysis.NAME_PARTS, "linked")).isEmpty();
	}

	@Test
	void aRootThatIsALinkIsIndexedWhereItLeads() throws IOException {
		final Path linked = Files.createSymbolicLink(root.getParent().resolve("linked-root"), root);
		final RepositoryIndex index = open(ANALYSIS);

		index.reindex(linked);

		assertThat(found(index, SearchAnalysis.EXACT, "apples")).containsExactly("projects/plan.md");
	}

	@Test
	void changesOfHiddenPlacesOrBehindLinksAreNotIndexed() throws IOException {
		final RepositoryIndex index = indexed();
		final Path outside = Files.createDirectories(root.getParent().resolve("outside"));
		Files.writeString(outside.resolve("other.md"), "elsewhere");
		Files.createSymbolicLink(root.resolve("link"), outside);

		index.changed(root, List.of(".hidden", "secret.md"));
		index.changed(root, List.of("link", "other.md"));

		assertThat(found(index, SearchAnalysis.EXACT, "elsewhere")).isEmpty();
		assertThat(found(index, SearchAnalysis.NAME_PARTS, "secret")).isEmpty();
	}

	@Test
	void aChangedFolderLeavesFoldersOfSimilarNamesAlone() throws IOException {
		write("projects-old/old.md", "cherries");
		final RepositoryIndex index = indexed();

		Files.move(root.resolve("projects"), root.resolve("work"));
		index.changed(root, List.of("projects"));

		assertThat(found(index, SearchAnalysis.EXACT, "cherries")).containsExactly("projects-old/old.md");
	}

	@Test
	void anUpdateRemovesAWholeFolderGoneByHand() throws IOException {
		final RepositoryIndex index = indexed();
		try (var files = Files.walk(root.resolve("projects"))) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}

		index.update(root);

		assertThat(found(index, SearchAnalysis.NAME, "projects")).isEmpty();
		assertThat(found(index, SearchAnalysis.NAME, "archive")).isEmpty();
		assertThat(found(index, SearchAnalysis.EXACT, "apples")).isEmpty();
	}

	@Test
	void anUnreadableOlderIndexIsNotSearched() throws IOException {
		final Path older = Files.createDirectories(indexes.resolve(IndexFormat.of(SearchAnalysis.of(Language.parse("en"))).folder()));
		Files.writeString(older.resolve("segments_1"), "garbage");

		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.state()).isEqualTo(new IndexState(IndexStatus.NEEDS_REINDEXING, Optional.of("format"), false));
		assertThat(index.lease()).isEmpty();
	}

	@Test
	void aFailedFirstReindexingHasNothingToSearch() throws IOException {
		Files.createDirectories(indexes);
		Files.setPosixFilePermissions(indexes, PosixFilePermissions.fromString("r-xr-xr-x"));
		try {
			final RepositoryIndex index = open(ANALYSIS);
			index.reindex(root);

			assertThat(index.state().status()).isEqualTo(IndexStatus.FAILED);
			assertThat(index.state().searchable()).isFalse();
		} finally {
			Files.setPosixFilePermissions(indexes, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
	}

	@Test
	void aBuildLeftOverFromACrashIsNoIndex() throws IOException {
		Files.createDirectories(indexes.resolve(IndexFormat.of(ANALYSIS).folder() + ".building"));

		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.state().status()).isEqualTo(IndexStatus.NOT_INDEXED);
		index.reindex(root);
		assertThat(indexes.toFile().list()).containsExactly(IndexFormat.of(ANALYSIS).folder());
	}

	@Test
	void anUnreadableFolderIsLeftOutTheRestIsIndexed() throws IOException {
		write("locked/inside.md", "cherries");
		Files.setPosixFilePermissions(root.resolve("locked"), PosixFilePermissions.fromString("---------"));
		try {
			final RepositoryIndex index = indexed();

			assertThat(index.state().status()).isEqualTo(IndexStatus.INDEXED);
			assertThat(found(index, SearchAnalysis.EXACT, "apples")).containsExactly("projects/plan.md");
			assertThat(found(index, SearchAnalysis.EXACT, "cherries")).isEmpty();
		} finally {
			Files.setPosixFilePermissions(root.resolve("locked"), PosixFilePermissions.fromString("rwxr-xr-x"));
		}
	}

	@Test
	void aTextFileOtherThanMarkdownIsTitledByItsName() throws IOException {
		write("projects/notes.txt", "# Not a heading here\ncherries");

		final RepositoryIndex index = indexed();

		assertThat(found(index, SearchAnalysis.TITLE, "notes.txt")).containsExactly("projects/notes.txt");
		assertThat(found(index, SearchAnalysis.TITLE, "heading")).isEmpty();
	}

	@Test
	void aReindexingStartsOnceAndSaysHowFarItIs() {
		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.begin()).isTrue();
		assertThat(index.begin()).isFalse();
		assertThat(index.state().status()).isEqualTo(IndexStatus.REINDEXING);
		assertThat(index.details().progress()).contains(new IndexDetails.Progress(0, 0));

		index.build(root);

		assertThat(index.state().status()).isEqualTo(IndexStatus.INDEXED);
		assertThat(index.details().progress()).isEmpty();
		// projects/, plan.md, budget.xlsx, archive/
		assertThat(index.details().files()).isEqualTo(4);
		// a new reindexing counts from 0 again
		index.begin();
		assertThat(index.details().progress()).contains(new IndexDetails.Progress(0, 0));
		index.build(root);
	}

	@Test
	void anUpdateStartsOnlyOnAnIndexedIndexAndOnce() {
		final RepositoryIndex index = open(ANALYSIS);
		assertThat(index.beginUpdate()).isEqualTo(RepositoryIndex.Start.NEEDS_REINDEXING);

		index.reindex(root);

		assertThat(index.beginUpdate()).isEqualTo(RepositoryIndex.Start.STARTED);
		assertThat(index.beginUpdate()).isEqualTo(RepositoryIndex.Start.RUNNING);
		index.runUpdate(root);
		assertThat(index.beginUpdate()).isEqualTo(RepositoryIndex.Start.STARTED);
		index.runUpdate(root);
	}

	@Test
	void theIndexKnowsWhenItWasBuiltAndLastChanged() throws IOException {
		final Instant before = Instant.now().minusSeconds(1);
		final RepositoryIndex index = indexed();
		final IndexDetails built = index.details();

		assertThat(built.indexedAt()).hasValueSatisfying(at -> assertThat(at).isAfter(before));
		assertThat(built.updatedAt()).isEqualTo(built.indexedAt());

		write("projects/new.md", "cherries");
		index.changed(root, List.of("projects", "new.md"));

		assertThat(index.details().indexedAt()).isEqualTo(built.indexedAt());
		assertThat(index.details().updatedAt().orElseThrow()).isAfterOrEqualTo(built.indexedAt().orElseThrow());
		// an update is no reindexing either
		index.update(root);
		assertThat(index.details().indexedAt()).isEqualTo(built.indexedAt());
		index.close();
		final IndexDetails again = open(ANALYSIS).details();
		assertThat(again.indexedAt()).isEqualTo(built.indexedAt());
		assertThat(again.files()).isEqualTo(5);
	}

	@Test
	void anUpdateReadsOnlyChangedFiles() throws IOException {
		final RepositoryIndex index = indexed();
		// unchanged in size and time, but no longer readable: an update that read it again would lose its words
		Files.setPosixFilePermissions(root.resolve("projects/plan.md"), PosixFilePermissions.fromString("---------"));
		try {
			write("projects/new.md", "cherries");

			index.update(root);

			assertThat(found(index, SearchAnalysis.EXACT, "apples")).containsExactly("projects/plan.md");
			assertThat(found(index, SearchAnalysis.EXACT, "cherries")).containsExactly("projects/new.md");
		} finally {
			Files.setPosixFilePermissions(root.resolve("projects/plan.md"), PosixFilePermissions.fromString("rw-r--r--"));
		}
	}

	@Test
	void unreadableFoldersAreNamed() throws IOException {
		write("locked/inside.md", "cherries");
		Files.setPosixFilePermissions(root.resolve("locked"), PosixFilePermissions.fromString("---------"));
		try {
			final RepositoryIndex index = indexed();

			assertThat(index.details().unreadable()).containsExactly("locked/");
		} finally {
			Files.setPosixFilePermissions(root.resolve("locked"), PosixFilePermissions.fromString("rwxr-xr-x"));
		}
	}

	@Test
	void theIndexIsKeptAcrossARestart() {
		indexed().close();

		final RepositoryIndex again = open(ANALYSIS);

		assertThat(again.state().status()).isEqualTo(IndexStatus.INDEXED);
		assertThat(found(again, SearchAnalysis.EXACT, "apples")).containsExactly("projects/plan.md");
	}

	@Test
	void changesByHandWaitForAnUpdate() throws IOException {
		final RepositoryIndex index = indexed();
		write("projects/new.md", "cherries");
		write("projects/plan.md", "# Garden plan\n\nPlums only.");
		Files.setLastModifiedTime(root.resolve("projects/plan.md"), FileTime.from(Instant.now().plusSeconds(5)));
		Files.delete(root.resolve("projects/budget.xlsx"));

		assertThat(found(index, SearchAnalysis.EXACT, "cherries")).isEmpty();

		index.update(root);
		// again: the entries deleted before count no more
		index.update(root);

		assertThat(found(index, SearchAnalysis.EXACT, "cherries")).containsExactly("projects/new.md");
		assertThat(found(index, SearchAnalysis.EXACT, "plums")).containsExactly("projects/plan.md");
		assertThat(found(index, SearchAnalysis.EXACT, "apples")).isEmpty();
		assertThat(found(index, SearchAnalysis.NAME_PARTS, "budget")).isEmpty();
		assertThat(index.state().status()).isEqualTo(IndexStatus.INDEXED);
	}

	@Test
	void mizoinesOwnChangesAreFoundAtOnce() throws IOException {
		final RepositoryIndex index = indexed();

		write("projects/new.md", "cherries");
		index.changed(root, List.of("projects", "new.md"));
		assertThat(found(index, SearchAnalysis.EXACT, "cherries")).containsExactly("projects/new.md");

		Files.move(root.resolve("projects"), root.resolve("work"));
		index.changed(root, List.of("projects"));
		index.changed(root, List.of("work"));
		assertThat(found(index, SearchAnalysis.EXACT, "cherries")).containsExactly("work/new.md");
		assertThat(found(index, SearchAnalysis.NAME, "archive")).containsExactly("work/archive/");

		Files.delete(root.resolve("work/new.md"));
		index.changed(root, List.of("work", "new.md"));
		assertThat(found(index, SearchAnalysis.EXACT, "cherries")).isEmpty();
	}

	@Test
	void changesWithoutAnIndexWaitForTheReindexing() throws IOException {
		final RepositoryIndex index = open(ANALYSIS);
		write("projects/new.md", "cherries");

		index.changed(root, List.of("projects", "new.md"));
		index.update(root);

		assertThat(index.state().status()).isEqualTo(IndexStatus.NOT_INDEXED);
	}

	@Test
	void anIndexOfAnotherAnalysisNeedsReindexingAndStillAnswers() {
		final RepositoryIndex old = open(SearchAnalysis.of(Language.parse("en")));
		old.reindex(root);
		old.close();

		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.state()).isEqualTo(new IndexState(IndexStatus.NEEDS_REINDEXING, Optional.of("format"), true));
		assertThat(found(index, SearchAnalysis.EXACT, "apples")).containsExactly("projects/plan.md");

		index.reindex(root);

		assertThat(index.state()).isEqualTo(new IndexState(IndexStatus.INDEXED, Optional.empty(), true));
		// the old format's folder is gone
		assertThat(indexes.toFile().list()).containsExactly(IndexFormat.of(ANALYSIS).folder());
	}

	@Test
	void anIndexOfAnotherLuceneMajorIsNeverOpened() throws IOException {
		Files.createDirectories(indexes.resolve("lucene9-" + ANALYSIS.format()));
		Files.writeString(indexes.resolve("lucene9-" + ANALYSIS.format()).resolve("segments_1"), "not ours to read");

		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.state()).isEqualTo(new IndexState(IndexStatus.NEEDS_REINDEXING, Optional.of("format"), false));
	}

	@Test
	void aFolderWhoseIndexSaysAnotherFormatNeedsReindexing() throws IOException {
		final SearchAnalysis english = SearchAnalysis.of(Language.parse("en"));
		final RepositoryIndex old = open(english);
		old.reindex(root);
		old.close();
		Files.move(indexes.resolve(IndexFormat.of(english).folder()), indexes.resolve(IndexFormat.of(ANALYSIS).folder()));

		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.state()).isEqualTo(new IndexState(IndexStatus.NEEDS_REINDEXING, Optional.of("format"), false));
	}

	@Test
	void aDamagedIndexNeedsReindexing() throws IOException {
		final Path folder = Files.createDirectories(indexes.resolve(IndexFormat.of(ANALYSIS).folder()));
		Files.writeString(folder.resolve("segments_2"), "garbage");

		final RepositoryIndex index = open(ANALYSIS);

		assertThat(index.state()).isEqualTo(new IndexState(IndexStatus.NEEDS_REINDEXING, Optional.of("damaged"), false));
		index.reindex(root);
		assertThat(index.state().status()).isEqualTo(IndexStatus.INDEXED);
	}

	@Test
	void aDeletedIndexIsNotIndexed() throws IOException {
		indexed().close();
		try (var files = Files.walk(indexes)) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}

		assertThat(open(ANALYSIS).state().status()).isEqualTo(IndexStatus.NOT_INDEXED);
	}

	@Test
	void aFailedReindexingKeepsTheOldIndex() throws IOException {
		final RepositoryIndex index = indexed();
		Files.setPosixFilePermissions(indexes, PosixFilePermissions.fromString("r-xr-xr-x"));
		try {
			index.reindex(root);

			assertThat(index.state().status()).isEqualTo(IndexStatus.FAILED);
			assertThat(index.state().reason()).isPresent();
			assertThat(index.state().searchable()).isTrue();
			assertThat(found(index, SearchAnalysis.EXACT, "apples")).containsExactly("projects/plan.md");
		} finally {
			Files.setPosixFilePermissions(indexes, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
	}
}
