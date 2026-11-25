package com.gratchev.mizoine.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.QueryBuilder;

import com.gratchev.mizoine.repositories.RepositoryFiles.SearchArea;
import com.gratchev.mizoine.search.SearchQuery.Item;
import com.gratchev.mizoine.search.SearchQuery.Kind;

/**
 * Lucene queries for a search (step 57): one per level of strictness (D53), and the filter of what the user may read
 * (D54). A whole name ranks above its parts, a name or title above the text, the words as written above their forms.
 */
final class SearchQueries {

	/** Shorter words are not spelled loosely: too many weak results. */
	private static final int SIMILAR_FROM = 4;
	/** From this length two letters may differ, below one. */
	private static final int TWO_EDITS_FROM = 8;

	private final SearchAnalysis analysis;
	private final QueryBuilder builder;
	private final Map<String, Float> weights = new LinkedHashMap<>();

	SearchQueries(final SearchAnalysis analysis) {
		this.analysis = analysis;
		this.builder = new QueryBuilder(analysis.analyzer());
		weights.put(SearchAnalysis.NAME, 8f);
		weights.put(SearchAnalysis.NAME_PARTS, 4f);
		weights.put(SearchAnalysis.TITLE, 4f);
		weights.put(SearchAnalysis.TOKENS, 3f);
		weights.put(SearchAnalysis.EXACT, 2f);
		analysis.textFields().stream().skip(1).forEach(language -> weights.put(language, 1f));
	}

	/** @return the query of this level; one that asks nothing finds nothing (e.g. no word to spell loosely) */
	Query query(final SearchQuery query, final Strictness strictness) {
		final BooleanQuery.Builder all = new BooleanQuery.Builder();
		for (final Item item : query.items()) {
			final Optional<Query> match = strictness == Strictness.SIMILAR_SPELLING && !item.left()
					? similar(item) : match(item);
			if (match.isEmpty()) {
				continue;
			}
			if (item.left()) {
				all.add(match.get(), Occur.MUST_NOT);
			} else {
				all.add(match.get(), strictness == Strictness.ALL_WORDS ? Occur.MUST : Occur.SHOULD);
			}
		}
		return all.build();
	}

	/** One part in every field, each with its weight. */
	private Optional<Query> match(final Item item) {
		return any(weights.entrySet().stream()
				.map(field -> field(field.getKey(), item).map(query -> (Query) new BoostQuery(query, field.getValue())))
				.flatMap(Optional::stream));
	}

	private Optional<Query> field(final String field, final Item item) {
		return switch (item.kind()) {
			case WORD -> Optional.ofNullable(builder.createBooleanQuery(field, item.text(), Occur.MUST));
			case PHRASE -> Optional.ofNullable(SearchAnalysis.NAME.equals(field)
					? builder.createBooleanQuery(field, item.text(), Occur.MUST)
					: builder.createPhraseQuery(field, item.text()));
			case PREFIX -> single(field, item.text()).map(term -> new PrefixQuery(new Term(field, term)));
		};
	}

	/** A word spelled a little differently, in the words as written and in names. */
	private Optional<Query> similar(final Item item) {
		if (item.kind() != Kind.WORD) {
			return Optional.empty();
		}
		return any(Stream.of(SearchAnalysis.EXACT, SearchAnalysis.NAME_PARTS)
				.flatMap(field -> single(field, item.text()).stream()
						.filter(term -> term.length() >= SIMILAR_FROM)
						.map(term -> (Query) new FuzzyQuery(new Term(field, term),
								term.length() >= TWO_EDITS_FROM ? 2 : 1, 1))));
	}

	/** The word as this field keeps it, if it is one word there. */
	private Optional<String> single(final String field, final String word) {
		final List<String> terms = analysis.terms(field, word);
		return terms.size() == 1 ? Optional.of(terms.getFirst()) : Optional.empty();
	}

	private static Optional<Query> any(final Stream<Query> queries) {
		final List<Query> list = queries.toList();
		if (list.isEmpty()) {
			return Optional.empty();
		}
		final BooleanQuery.Builder any = new BooleanQuery.Builder();
		list.forEach(query -> any.add(query, Occur.SHOULD));
		return Optional.of(any.build());
	}

	/** Only inside the granted folders of each searched repository (D54): results and their number alike. */
	static Query readable(final List<SearchArea> areas) {
		final BooleanQuery.Builder any = new BooleanQuery.Builder();
		for (final SearchArea area : areas) {
			final BooleanQuery.Builder inArea = new BooleanQuery.Builder()
					.add(new TermQuery(new Term(IndexDocuments.REPOSITORY, area.repository())), Occur.FILTER);
			if (!area.wholeRepository()) {
				final BooleanQuery.Builder folders = new BooleanQuery.Builder();
				area.folders().forEach(folder -> folders.add(new PrefixQuery(
						new Term(IndexDocuments.PATH, IndexDocuments.path(folder, true))), Occur.SHOULD));
				inArea.add(folders.build(), Occur.FILTER);
			}
			any.add(inArea.build(), Occur.SHOULD);
		}
		return any.build();
	}
}
