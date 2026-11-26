package com.gratchev.mizoine.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;

import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryFiles.SearchArea;
import com.gratchev.mizoine.repositories.TextFile;
import com.gratchev.mizoine.tags.Tag;

/**
 * Searching as a user (step 57): in one repository or in all the user may read in, ranked, strict before loose
 * (D53). Only what the user may read (D54): the query itself is limited to the user's granted folders, so neither the
 * results nor their number say anything else, and each result is checked again with the rules of reading before it
 * is shown. Grants are read per search, so a changed repository file counts at once.
 */
public class Search {

	private static final List<Strictness> LEVELS = List.of(Strictness.ALL_WORDS, Strictness.ANY_WORD,
			Strictness.SIMILAR_SPELLING);

	private final RepositoryFiles files;
	private final SearchIndexes indexes;
	private final SearchQueries queries;
	private final Snippets snippets;

	public Search(final RepositoryFiles files, final SearchIndexes indexes) {
		this.files = files;
		this.indexes = indexes;
		this.queries = new SearchQueries(indexes.analysis());
		this.snippets = new Snippets(indexes.analysis());
	}

	/**
	 * @param repository one repository, or empty for all the user may read in
	 * @throws RepositoryFiles.NotVisibleException for a repository the user has no grant in
	 * @throws NotSearchableException if none of the repositories has an index to search (D59)
	 */
	public SearchResults search(final String user, final Optional<String> repository, final SearchQuery query,
			final Page page) {
		return search(user, repository, query, page, List.of());
	}

	/** @param tags only folders with all these tags, at every level of strictness (step 65) */
	public SearchResults search(final String user, final Optional<String> repository, final SearchQuery query,
			final Page page, final List<Tag> tags) {
		final List<SearchArea> areas = repository.map(name -> List.of(files.searchArea(user, name)))
				.orElseGet(() -> files.searchAreas(user));
		return withIndexes(areas, (searcher, searched, infos) -> {
			final BooleanQuery.Builder filter = new BooleanQuery.Builder().add(SearchQueries.readable(searched), Occur.FILTER);
			tags.forEach(tag -> filter.add(queries.tag(tag.text()), Occur.FILTER));
			final Query readable = filter.build();
			for (final Strictness level : LEVELS) {
				final Query asked = queries.query(query, level);
				final Query limited = new BooleanQuery.Builder().add(asked, Occur.MUST).add(readable, Occur.FILTER).build();
				final int total = searcher.count(limited);
				if (total > 0) {
					return new SearchResults(query.text(), level, total, page.offset(), page.limit(),
							hits(user, searcher, limited, asked, page), infos);
				}
			}
			return new SearchResults(query.text(), Strictness.NONE, 0, page.offset(), page.limit(), List.of(), infos);
		});
	}

	/**
	 * Step 65, D71: the tags in use among the folders the user may read in a repository, with how often, from the
	 * index; in lower case, sorted.
	 *
	 * @throws NotSearchableException without an index to answer from
	 */
	public Vocabulary vocabulary(final String user, final String repository) {
		return withIndexes(List.of(files.searchArea(user, repository)), (searcher, searched, infos) -> {
			final Query tagged = new BooleanQuery.Builder()
					.add(new FieldExistsQuery(SearchAnalysis.TAG), Occur.MUST)
					.add(SearchQueries.readable(searched), Occur.FILTER).build();
			final Map<String, Integer> counts = new TreeMap<>();
			for (final ScoreDoc hit : searcher.search(tagged, Math.max(1, searcher.getIndexReader().maxDoc())).scoreDocs) {
				for (final String tag : searcher.storedFields().document(hit.doc).getValues(IndexDocuments.TAGS)) {
					counts.merge(Tag.of(tag).folded(), 1, Integer::sum);
				}
			}
			return new Vocabulary(repository, counts.entrySet().stream()
					.map(count -> new TagCount(count.getKey(), count.getValue())).toList(), infos.getFirst());
		});
	}

	/** A search over the indexes of these areas, those with an index to answer from. */
	@FunctionalInterface
	private interface IndexWork<T> {
		T apply(IndexSearcher searcher, List<SearchArea> searched, List<IndexInfo> infos) throws IOException;
	}

	private <T> T withIndexes(final List<SearchArea> areas, final IndexWork<T> work) {
		final List<IndexInfo> infos = new ArrayList<>();
		final List<SearchArea> searched = new ArrayList<>();
		final List<IndexLease> leases = new ArrayList<>();
		try {
			for (final SearchArea area : areas) {
				final RepositoryIndex index = indexes.index(area.repository());
				final Optional<IndexLease> lease = index.lease();
				infos.add(IndexInfo.of(area.repository(), index.state()));
				lease.ifPresent(found -> {
					leases.add(found);
					searched.add(area);
				});
			}
			if (leases.isEmpty()) {
				throw new NotSearchableException(infos);
			}
			try (MultiReader reader = new MultiReader(leases.stream().map(IndexLease::reader).toArray(IndexReader[]::new),
					false)) {
				return work.apply(new IndexSearcher(reader), searched, infos);
			}
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			leases.forEach(Search::release);
		}
	}

	private List<SearchResults.Hit> hits(final String user, final IndexSearcher searcher, final Query limited,
			final Query asked, final Page page) throws IOException {
		final ScoreDoc[] top = searcher.search(limited, page.offset() + page.limit()).scoreDocs;
		final List<SearchResults.Hit> hits = new ArrayList<>();
		for (int i = page.offset(); i < top.length; i++) {
			hit(user, searcher.storedFields().document(top[i].doc), asked).ifPresent(hits::add);
		}
		return hits;
	}

	/** A result, if the user may read it now; with passages of its text. */
	private Optional<SearchResults.Hit> hit(final String user, final Document document, final Query asked) {
		final String repository = document.get(IndexDocuments.REPOSITORY);
		final String path = document.get(IndexDocuments.PATH);
		final boolean folder = IndexDocuments.FOLDER.equals(document.get(IndexDocuments.KIND));
		final List<String> segments = RepositoryFiles.segments(path);
		return files.findable(user, repository, segments, folder).map(real -> new SearchResults.Hit(repository, path,
				RepositoryFiles.url(repository, segments, folder), document.get(IndexDocuments.KIND),
				document.get(SearchAnalysis.TITLE), folder ? List.of() : passages(asked, real),
				List.of(document.getValues(IndexDocuments.TAGS))));
	}

	private List<Snippet> passages(final Query asked, final Path file) {
		return TextFile.read(file).map(text -> snippets.of(asked, text)).orElse(List.of());
	}

	private static void release(final IndexLease lease) {
		try {
			lease.close();
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
