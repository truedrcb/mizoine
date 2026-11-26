package com.gratchev.mizoine.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gratchev.mizoine.repositories.RepositoryFileParser;
import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryRegistry;
import com.gratchev.mizoine.repositories.RepositoryWriter;

/**
 * Step 57: searching as a user, on real repository files: only what the user may read (D54), ranked, strict
 * before loose (D53).
 */
class SearchTest {

	private static final SearchAnalysis ANALYSIS = SearchAnalysis.of(Language.parse("en,de"));
	private static final String NOTES = "path: notes\naccess:\n  artem: [\"/\"]\n  guest: [public]\n";

	private final Path repositories;
	private final SearchIndexes indexes;
	private final Search search;

	SearchTest(@TempDir final Path dir) throws IOException {
		this.repositories = Files.createDirectories(dir.resolve("repositories"));
		repository("notes", NOTES);
		repository("private", "path: private\naccess:\n  artem: [\"/\"]\n");
		write("notes/public/plan.md", "# Garden plan\n\nApples and pears in the garden.");
		write("notes/public/shopping.md", "Buy apples, write to artem@example.org.");
		write("notes/public/bericht-2026.pdf", "\u0000binary");
		write("notes/private/apples.md", "Apples for artem only.");
		write("notes/.hidden/apples.md", "Hidden apples.");
		write("private/apples.md", "Private apples.");
		// a grant "public" is no grant for a folder whose name only starts the same
		write("notes/publicity/apples.md", "Publicity apples.");
		Files.createSymbolicLink(repositories.resolve("notes/public/link.md"), repositories.resolve("notes/private/apples.md"));
		final RepositoryRegistry registry = new RepositoryRegistry(repositories, new RepositoryFileParser(), name -> true, null);
		this.indexes = new SearchIndexes(dir.resolve("search-index"), ANALYSIS);
		this.search = new Search(new RepositoryFiles(registry, new RepositoryWriter()), indexes);
	}

	@AfterEach
	void close() {
		indexes.close();
	}

	private void repository(final String name, final String yaml) throws IOException {
		final Path file = repositories.resolve(name + ".yaml");
		final boolean replaced = Files.exists(file);
		Files.writeString(file, yaml);
		// a new version also within the file system's time resolution
		Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(replaced ? 2 : 0)));
	}

	private void write(final String path, final String content) throws IOException {
		final Path file = repositories.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	private void reindex(final String repository) {
		indexes.index(repository).reindex(repositories.resolve(repository));
	}

	private SearchResults inNotes(final String user, final String query) {
		return search.search(user, Optional.of("notes"), SearchQuery.parse(query), Page.FIRST);
	}

	private static List<String> paths(final SearchResults results) {
		return results.hits().stream().map(hit -> hit.repository() + "/" + hit.path()).toList();
	}

	@Test
	void onlyWhatTheUserMayReadNotEvenInTheCount() {
		reindex("notes");
		reindex("private");

		final SearchResults guest = search.search("guest", Optional.empty(), SearchQuery.parse("apples"), Page.FIRST);
		// not the other folder, not the hidden one, not through the link, not the repository without a grant
		assertThat(paths(guest)).containsExactlyInAnyOrder("notes/public/plan.md", "notes/public/shopping.md");
		assertThat(guest.total()).isEqualTo(2);

		final SearchResults artem = search.search("artem", Optional.empty(), SearchQuery.parse("apples"), Page.FIRST);
		assertThat(paths(artem)).containsExactlyInAnyOrder("notes/public/plan.md", "notes/public/shopping.md",
				"notes/private/apples.md", "notes/publicity/apples.md", "private/apples.md");
	}

	@Test
	void aRepositoryWithoutAGrantIsNotFound() {
		reindex("private");

		assertThatThrownBy(() -> search.search("guest", Optional.of("private"), SearchQuery.parse("apples"), Page.FIRST))
				.isInstanceOf(RepositoryFiles.NotVisibleException.class);
	}

	@Test
	void aGrantRemovedWhileRunningCountsAtOnce() throws IOException {
		reindex("notes");
		assertThat(inNotes("guest", "apples").total()).isEqualTo(2);

		repository("notes", "path: notes\naccess:\n  artem: [\"/\"]\n  guest: [elsewhere]\n");

		assertThat(inNotes("guest", "apples").hits()).isEmpty();
		assertThat(inNotes("guest", "apples").total()).isZero();
	}

	@Test
	void aFileGoneSinceTheIndexingIsNotShown() throws IOException {
		reindex("notes");
		// by hand: the index still has it until an update
		Files.delete(repositories.resolve("notes/public/shopping.md"));

		assertThat(paths(inNotes("guest", "apples"))).containsExactly("notes/public/plan.md");
	}

	@Test
	void foldersAndFilesFoundByName() {
		reindex("notes");

		assertThat(paths(inNotes("artem", "bericht-2026.pdf"))).containsExactly("notes/public/bericht-2026.pdf");
		assertThat(paths(inNotes("artem", "bericht"))).containsExactly("notes/public/bericht-2026.pdf");
		assertThat(paths(inNotes("artem", "artem@example.org"))).containsExactly("notes/public/shopping.md");
		assertThat(paths(inNotes("guest", "public"))).containsExactly("notes/public/");
		assertThat(inNotes("guest", "public").hits().getFirst().kind()).isEqualTo("folder");
		assertThat(inNotes("guest", "public").hits().getFirst().url()).isEqualTo("/notes/public/");
	}

	@Test
	void aMatchInTheTitleRanksAboveOneInTheText() throws IOException {
		write("notes/public/garden.md", "# Kitchen\n\nThe garden is small.");
		reindex("notes");

		final SearchResults results = inNotes("guest", "garden");

		assertThat(paths(results).getFirst()).isEqualTo("notes/public/plan.md");
		assertThat(results.hits().getFirst().title()).isEqualTo("Garden plan");
	}

	@Test
	void strictFirstThenAnyWordThenSimilarSpelling() {
		reindex("notes");

		final SearchResults all = inNotes("guest", "apples pears");
		assertThat(all.strictness()).isEqualTo(Strictness.ALL_WORDS);
		assertThat(paths(all)).containsExactly("notes/public/plan.md");

		final SearchResults any = inNotes("guest", "apples kiwis");
		assertThat(any.strictness()).isEqualTo(Strictness.ANY_WORD);
		assertThat(paths(any)).containsExactlyInAnyOrder("notes/public/plan.md", "notes/public/shopping.md");

		final SearchResults similar = inNotes("guest", "appels");
		assertThat(similar.strictness()).isEqualTo(Strictness.SIMILAR_SPELLING);
		assertThat(paths(similar)).containsExactlyInAnyOrder("notes/public/plan.md", "notes/public/shopping.md");

		final SearchResults none = inNotes("guest", "zebra");
		assertThat(none.strictness()).isEqualTo(Strictness.NONE);
		assertThat(none.hits()).isEmpty();
		// from 4 letters one may differ, from 8 letters two
		assertThat(paths(inNotes("guest", "plam"))).containsExactly("notes/public/plan.md");
		assertThat(paths(inNotes("guest", "shoxxing"))).containsExactly("notes/public/shopping.md");
		assertThat(inNotes("guest", "shoxxinx").strictness()).isEqualTo(Strictness.NONE);
		// the first letter is kept: fewer weak results
		assertThat(inNotes("guest", "xlan").strictness()).isEqualTo(Strictness.NONE);
		// short words are not spelled loosely: too many weak results
		assertThat(inNotes("guest", "pea").strictness()).isEqualTo(Strictness.NONE);
	}

	@Test
	void phrasesExclusionsAndPrefixes() {
		reindex("notes");

		assertThat(paths(inNotes("guest", "\"garden plan\""))).containsExactly("notes/public/plan.md");
		assertThat(paths(inNotes("guest", "\"plan garden\""))).isEmpty();
		assertThat(paths(inNotes("guest", "apples -pears"))).containsExactly("notes/public/shopping.md");
		assertThat(paths(inNotes("guest", "shop*"))).containsExactly("notes/public/shopping.md");
		// the prefix of a whole name, which no other field has as one word
		assertThat(paths(inNotes("guest", "bericht-20*"))).containsExactly("notes/public/bericht-2026.pdf");
		// a lone star asks nothing
		assertThat(inNotes("guest", "*").hits()).isEmpty();
	}

	@Test
	void theQueryIsCheckedWhereItComesIn() {
		assertThatThrownBy(() -> SearchQuery.parse("  ")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Write what to search for.");
		assertThatThrownBy(() -> SearchQuery.parse("x".repeat(201))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("A search can be at most 200 characters long.");
		assertThatThrownBy(() -> SearchQuery.parse("-pears")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Write what to search for, not only what to leave out.");
		assertThat(SearchQuery.parse("x".repeat(200)).text()).hasSize(200);
		assertThatThrownBy(() -> new Page(0, 101)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Page(-1, 20)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Page(0, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void snippetsMarkTheFoundWords() {
		reindex("notes");

		final List<Snippet> snippets = inNotes("guest", "pears").hits().getFirst().snippets();

		assertThat(snippets).containsExactly(new Snippet(List.of(
				new Snippet.Part("# Garden plan\n\nApples and ", false),
				new Snippet.Part("pears", true),
				new Snippet.Part(" in the garden.", false))));
		assertThat(inNotes("guest", "garden").hits().getFirst().snippets().getFirst().parts()).startsWith(
				new Snippet.Part("# ", false), new Snippet.Part("Garden", true));
	}

	@Test
	void snippetsWithFoundWordsAtTheirStartAndEnd() throws IOException {
		write("notes/public/fruit.md", "Pears first, then more pears");
		reindex("notes");

		assertThat(inNotes("guest", "pears").hits().stream().filter(hit -> hit.path().equals("public/fruit.md"))
				.findFirst().orElseThrow().snippets()).containsExactly(new Snippet(List.of(
						new Snippet.Part("Pears", true), new Snippet.Part(" first, then more ", false),
						new Snippet.Part("pears", true))));
	}

	@Test
	void neighbouringPassagesStayApart() {
		final String text = "pears" + " filler".repeat(30) + " pears";

		assertThat(new Snippets(ANALYSIS).of(new org.apache.lucene.search.TermQuery(
				new org.apache.lucene.index.Term(SearchAnalysis.EXACT, "pears")), text)).hasSize(2);
	}

	@Test
	void aTextWithoutTheWordsHasNoSnippet() {
		assertThat(new Snippets(ANALYSIS).of(new org.apache.lucene.search.TermQuery(
				new org.apache.lucene.index.Term(SearchAnalysis.EXACT, "zebra")), "Apples and pears.")).isEmpty();
	}

	@Test
	void snippetsFindWordFormsToo() throws IOException {
		write("notes/public/haus.md", "Die Häuser am See.");
		reindex("notes");

		final SearchResults results = inNotes("guest", "Haus");

		assertThat(paths(results)).contains("notes/public/haus.md");
		assertThat(results.hits().stream().filter(hit -> hit.path().equals("public/haus.md")).findFirst().orElseThrow()
				.snippets().getFirst().parts()).contains(new Snippet.Part("Häuser", true));
	}

	private void tags(final String folder, final String tags) throws IOException {
		Files.createDirectories(repositories.resolve(folder));
		write(folder + "/.tags", tags);
	}

	@Test
	void foldersByTheirTags() throws IOException {
		tags("notes/public/garden", "project status:open");
		tags("notes/public/kitchen", "project status:done");
		tags("notes/private/secret", "project status:open");
		reindex("notes");

		assertThat(paths(inNotes("guest", "#status:open"))).containsExactly("notes/public/garden/");
		assertThat(paths(inNotes("guest", "#project"))).containsExactlyInAnyOrder("notes/public/garden/",
				"notes/public/kitchen/");
		assertThat(inNotes("guest", "#project").total()).isEqualTo(2);
		assertThat(paths(inNotes("artem", "#project"))).hasSize(3);
		// a plain word finds tags too
		assertThat(paths(inNotes("guest", "status:done"))).containsExactly("notes/public/kitchen/");
		// the results show the tags
		assertThat(inNotes("guest", "#status:open").hits().getFirst().tags()).containsExactly("project", "status:open");
		// a tag is exact: no similar spelling
		assertThat(inNotes("guest", "#projekt").strictness()).isEqualTo(Strictness.NONE);
		assertThatThrownBy(() -> SearchQuery.parse("#two:colons:here")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Not a tag");
	}

	@Test
	void aTagFilterHoldsAtEveryLevel() throws IOException {
		tags("notes/public/garden", "project status:open");
		tags("notes/public/kitchen", "project status:done");
		write("notes/public/garden/beds.md", "Apples here.");
		reindex("notes");

		final SearchResults filtered = search.search("guest", Optional.of("notes"), SearchQuery.parse("garden"),
				Page.FIRST, List.of(com.gratchev.mizoine.tags.Tag.of("status:open")));
		assertThat(paths(filtered)).containsExactly("notes/public/garden/");
		// also when nothing has all words: the filter is never loosened
		assertThat(paths(search.search("guest", Optional.of("notes"), SearchQuery.parse("kitchen zebra"), Page.FIRST,
				List.of(com.gratchev.mizoine.tags.Tag.of("status:open"))))).isEmpty();
	}

	@Test
	void theTagsInUseWithHowOften() throws IOException {
		tags("notes/public/garden", "project status:open");
		tags("notes/public/kitchen", "Project status:done");
		tags("notes/private/secret", "secret status:open");
		reindex("notes");

		assertThat(search.vocabulary("guest", "notes").tags()).containsExactly(
				new TagCount("project", 2), new TagCount("status:done", 1), new TagCount("status:open", 1));
		assertThat(search.vocabulary("artem", "notes").tags()).containsExactly(
				new TagCount("project", 2), new TagCount("secret", 1), new TagCount("status:done", 1),
				new TagCount("status:open", 2));
	}

	@Test
	void theTagsInUseOnlyOfFoldersTheUserMayReadNow() throws IOException {
		tags("notes/public/garden", "project");
		tags("notes/public/moved", "secret-plan");
		reindex("notes");
		// by hand, out of guest's folder: the index still has it there until an update, as for a search hit
		Files.move(repositories.resolve("notes/public/moved"), repositories.resolve("notes/private/moved"));

		assertThat(search.vocabulary("guest", "notes").tags()).containsExactly(new TagCount("project", 1));
	}

	@Test
	void theVocabularyOfAnEmptyRepository() throws IOException {
		repository("empty", "path: empty\naccess:\n  guest: [\"/\"]\n");
		Files.createDirectories(repositories.resolve("empty"));
		reindex("empty");

		assertThat(search.vocabulary("guest", "empty").tags()).isEmpty();
	}

	@Test
	void aLoneHashIsAWordNotAnEmptyTag() {
		assertThat(SearchQuery.parse("#").items()).containsExactly(new SearchQuery.Item("#", SearchQuery.Kind.WORD, false));
	}

	@Test
	void theVocabularyNeedsAnIndex() {
		assertThatThrownBy(() -> search.vocabulary("guest", "notes")).isInstanceOf(NotSearchableException.class);
	}

	@Test
	void pages() {
		reindex("notes");

		final SearchResults first = search.search("guest", Optional.of("notes"), SearchQuery.parse("apples"), new Page(0, 1));
		final SearchResults second = search.search("guest", Optional.of("notes"), SearchQuery.parse("apples"), new Page(1, 1));

		assertThat(first.hits()).hasSize(1);
		assertThat(second.hits()).hasSize(1);
		assertThat(first.total()).isEqualTo(2);
		assertThat(paths(first)).doesNotContainAnyElementsOf(paths(second));
	}

	@Test
	void duringAReindexingTheOldIndexAnswersAndSaysSo() {
		reindex("notes");
		final RepositoryIndex index = indexes.index("notes");
		index.begin();

		final SearchResults results = inNotes("guest", "apples");

		assertThat(results.total()).isEqualTo(2);
		assertThat(results.indexes()).containsExactly(new IndexInfo("notes", IndexStatus.REINDEXING, Optional.empty(), true));
		index.build(repositories.resolve("notes"));
		assertThat(inNotes("guest", "apples").indexes().getFirst().status()).isEqualTo(IndexStatus.INDEXED);
	}

	@Test
	void withoutAnyIndexSearchCannotAnswer() {
		assertThatThrownBy(() -> inNotes("guest", "apples"))
				.isInstanceOfSatisfying(NotSearchableException.class, e -> assertThat(e.indexes())
						.containsExactly(new IndexInfo("notes", IndexStatus.NOT_INDEXED, Optional.empty(), false)));
	}

	@Test
	void answersFromTheIndexesThereAreAndSaysWhichAreMissing() {
		reindex("notes");

		final SearchResults results = search.search("artem", Optional.empty(), SearchQuery.parse("apples"), Page.FIRST);

		assertThat(paths(results)).doesNotContain("private/apples.md").contains("notes/private/apples.md");
		assertThat(results.indexes()).containsExactly(
				new IndexInfo("notes", IndexStatus.INDEXED, Optional.empty(), true),
				new IndexInfo("private", IndexStatus.NOT_INDEXED, Optional.empty(), false));
	}
}
