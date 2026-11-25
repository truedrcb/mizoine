package com.gratchev.mizoine.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;

import com.gratchev.mizoine.repositories.RepositoryFiles;
import com.gratchev.mizoine.repositories.RepositoryFiles.SearchArea;
import com.gratchev.mizoine.repositories.TextFile;

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
		final List<SearchArea> areas = repository.map(name -> List.of(files.searchArea(user, name)))
				.orElseGet(() -> files.searchAreas(user));
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
			return search(user, query, page, searched, leases, infos);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			leases.forEach(Search::release);
		}
	}

	private SearchResults search(final String user, final SearchQuery query, final Page page,
			final List<SearchArea> areas, final List<IndexLease> leases, final List<IndexInfo> infos) throws IOException {
		try (MultiReader reader = new MultiReader(leases.stream().map(IndexLease::reader).toArray(IndexReader[]::new),
				false)) {
			final IndexSearcher searcher = new IndexSearcher(reader);
			final Query readable = SearchQueries.readable(areas);
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
				document.get(SearchAnalysis.TITLE), folder ? List.of() : passages(asked, real)));
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
