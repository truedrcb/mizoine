package com.gratchev.mizoine.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.TextFragment;

/**
 * Up to 3 passages of a found text with the found words marked (step 57). The text is read from the file when the
 * result is shown, after it was checked (D54): the index keeps no content.
 */
final class Snippets {

	private static final int MAX = 3;
	private static final int PASSAGE = 160;
	/** Characters of Unicode's private use, never shown: they mark found words inside the highlighter's text. */
	private static final char START = '';
	private static final char END = '';

	private final SearchAnalysis analysis;

	Snippets(final SearchAnalysis analysis) {
		this.analysis = analysis;
	}

	/** In the words as written first; else in a language's word forms, then in whole tokens (emails, names). */
	List<Snippet> of(final Query query, final String content) {
		final String text = content.replace(START, ' ').replace(END, ' ');
		return Stream.concat(analysis.textFields().stream(), Stream.of(SearchAnalysis.TOKENS))
				.map(field -> of(query, field, text))
				.filter(found -> !found.isEmpty())
				.findFirst()
				.orElse(List.of());
	}

	private List<Snippet> of(final Query query, final String field, final String text) {
		final Highlighter highlighter = new Highlighter(
				(original, group) -> group.getTotalScore() > 0 ? START + original + END : original,
				new QueryScorer(query, field));
		highlighter.setTextFragmenter(new SimpleFragmenter(PASSAGE));
		highlighter.setMaxDocCharsToAnalyze(text.length());
		try {
			return Arrays.stream(highlighter.getBestTextFragments(analysis.analyzer().tokenStream(field, text), text,
							false, MAX))
					.filter(fragment -> fragment.getScore() > 0)
					.map(TextFragment::toString)
					.map(Snippets::parts)
					.toList();
		} catch (final IOException | InvalidTokenOffsetsException e) {
			// a String is read without I/O, and the offsets are the analyzer's own
			return List.of();
		}
	}

	private static Snippet parts(final String marked) {
		final List<Snippet.Part> parts = new ArrayList<>();
		int from = 0;
		for (int start = marked.indexOf(START); start >= 0; start = marked.indexOf(START, from)) {
			if (start > from) {
				parts.add(new Snippet.Part(marked.substring(from, start), false));
			}
			final int end = marked.indexOf(END, start);
			parts.add(new Snippet.Part(marked.substring(start + 1, end), true));
			from = end + 1;
		}
		if (from < marked.length()) {
			parts.add(new Snippet.Part(marked.substring(from), false));
		}
		return new Snippet(parts);
	}
}
