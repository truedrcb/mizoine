package com.gratchev.mizoine.search;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.LengthFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.pattern.PatternReplaceFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.ElisionFilter;

/**
 * How text becomes searchable words (step 55, D51, D63). One analysis per field:
 * <ul>
 * <li>{@link #EXACT}: words, lower case, every spelling of a letter folded to one: German {@code ä}/{@code ae} →
 * {@code a}, {@code ß} → {@code ss}; accents ({@code é} → {@code e}, {@code œ} → {@code oe}); French elision
 * ({@code l'école} → {@code école}); Russian {@code ё} → {@code е}.</li>
 * <li>one field per {@link Language}: the same, plus that language's word forms.</li>
 * <li>{@link #TOKENS}: words as written between spaces, punctuation at their ends removed, so email addresses,
 * URLs and file names stay whole.</li>
 * <li>{@link #NAME}: a file or folder name whole; {@link #NAME_PARTS}: in its parts (at {@code -}, {@code _},
 * {@code .}, spaces, camelCase, digits).</li>
 * <li>{@link #TITLE}: as {@link #EXACT}, for its own weight.</li>
 * <li>{@link #TAG}: each tag of a folder whole, folded as a name (version 2).</li>
 * </ul>
 */
public final class SearchAnalysis {

	/**
	 * Raised with every change of the fields or of their analysis: an index of another version is built anew (D58).
	 * {@code SearchAnalysisTest.theAnalysisOfThisVersion} fails as a reminder.
	 */
	public static final int ANALYSIS_VERSION = 2;

	public static final String EXACT = "exact";
	public static final String TOKENS = "tokens";
	public static final String NAME = "name";
	/** A folder's tags (step 65), each whole and folded like a name. */
	public static final String TAG = "tag";
	public static final String NAME_PARTS = "nameparts";
	public static final String TITLE = "title";

	/** French articles and pronouns before an apostrophe, as in Lucene's French analyzer. */
	private static final CharArraySet ELISIONS = new CharArraySet(
			List.of("l", "m", "t", "qu", "n", "s", "j", "d", "c", "jusqu", "quoiqu", "lorsqu", "puisqu"), true);
	private static final NormalizeCharMap RUSSIAN_LETTERS = russianLetters();
	/** Punctuation at the start or end of a word: brackets, quotes, full stops, commas. */
	private static final Pattern PUNCTUATION_AT_ENDS = Pattern.compile("^\\p{P}+|\\p{P}+$");
	private static final int WORD_PARTS = WordDelimiterGraphFilter.GENERATE_WORD_PARTS
			| WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS | WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
			| WordDelimiterGraphFilter.SPLIT_ON_NUMERICS;

	private final List<Language> languages;
	private final Analyzer analyzer;

	private SearchAnalysis(final List<Language> languages) {
		this.languages = languages;
		final Map<String, Analyzer> fields = new HashMap<>();
		fields.put(EXACT, analyzer(StandardTokenizer::new, SearchAnalysis::words));
		fields.put(TITLE, analyzer(StandardTokenizer::new, SearchAnalysis::words));
		languages.forEach(language -> fields.put(language.code(),
				analyzer(StandardTokenizer::new, stream -> language.stem(words(stream)))));
		fields.put(TOKENS, analyzer(WhitespaceTokenizer::new, stream -> folded(new LengthFilter(
				new PatternReplaceFilter(stream, PUNCTUATION_AT_ENDS, "", true), 1, Integer.MAX_VALUE))));
		fields.put(NAME, analyzer(KeywordTokenizer::new, SearchAnalysis::folded));
		fields.put(TAG, analyzer(KeywordTokenizer::new, SearchAnalysis::folded));
		fields.put(NAME_PARTS, analyzer(WhitespaceTokenizer::new,
				stream -> folded(new FlattenGraphFilter(new WordDelimiterGraphFilter(stream, WORD_PARTS, null)))));
		this.analyzer = new PerFieldAnalyzerWrapper(fields.get(EXACT), fields);
	}

	public static SearchAnalysis of(final List<Language> languages) {
		return new SearchAnalysis(List.copyOf(languages));
	}

	/** The analysis of every field, for writing the index and for queries. */
	public Analyzer analyzer() {
		return analyzer;
	}

	/** The fields a document's text goes into: {@link #EXACT}, then one per language, in the configured order. */
	public List<String> textFields() {
		return Stream.concat(Stream.of(EXACT), languages.stream().map(Language::code)).toList();
	}

	/** This analysis as part of an index's format, e.g. {@code a1-de-en-fr-ru}: another one needs a new index. */
	public String format() {
		return "a" + ANALYSIS_VERSION + "-" + languages.stream().map(Language::code).sorted().collect(Collectors.joining("-"));
	}

	/** The words of a text as one field indexes them, in order. */
	public List<String> terms(final String field, final String text) {
		final List<String> terms = new ArrayList<>();
		try (TokenStream stream = analyzer.tokenStream(field, text)) {
			final CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
			stream.reset();
			while (stream.incrementToken()) {
				terms.add(term.toString());
			}
			stream.end();
		} catch (final IOException e) {
			// a String is read without I/O
			throw new UncheckedIOException(e);
		}
		return terms;
	}

	/** Words: French elision removed, then folded. */
	private static TokenStream words(final TokenStream stream) {
		return folded(new ElisionFilter(stream, ELISIONS));
	}

	/** Lower case, accents and German spellings folded to one letter. */
	private static TokenStream folded(final TokenStream stream) {
		return new GermanNormalizationFilter(new ASCIIFoldingFilter(new LowerCaseFilter(stream)));
	}

	private static Analyzer analyzer(final java.util.function.Supplier<Tokenizer> tokenizer,
			final Function<TokenStream, TokenStream> filters) {
		return new Analyzer() {
			@Override
			protected TokenStreamComponents createComponents(final String fieldName) {
				final Tokenizer source = tokenizer.get();
				return new TokenStreamComponents(source, filters.apply(source));
			}

			@Override
			protected Reader initReader(final String fieldName, final Reader reader) {
				return new MappingCharFilter(RUSSIAN_LETTERS, reader);
			}
		};
	}

	private static NormalizeCharMap russianLetters() {
		final NormalizeCharMap.Builder letters = new NormalizeCharMap.Builder();
		letters.add("ё", "е");
		letters.add("Ё", "Е");
		return letters.build();
	}
}
