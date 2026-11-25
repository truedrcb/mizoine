package com.gratchev.mizoine.search;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.es.SpanishLightStemFilter;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.it.ItalianLightStemFilter;
import org.apache.lucene.analysis.pt.PortugueseLightStemFilter;
import org.apache.lucene.analysis.ru.RussianLightStemFilter;
import org.apache.lucene.analysis.sv.SwedishLightStemFilter;

/**
 * A language whose word forms search knows (step 55, D51): its code is the name of its index field. English,
 * German, French and Russian by default ({@code mizoine.search.languages}); the others can be added.
 */
public enum Language {
	EN(PorterStemFilter::new),
	DE(GermanLightStemFilter::new),
	FR(FrenchLightStemFilter::new),
	RU(RussianLightStemFilter::new),
	ES(SpanishLightStemFilter::new),
	IT(ItalianLightStemFilter::new),
	PT(PortugueseLightStemFilter::new),
	SV(SwedishLightStemFilter::new);

	private final UnaryOperator<TokenStream> stemmer;

	Language(final UnaryOperator<TokenStream> stemmer) {
		this.stemmer = stemmer;
	}

	/** @return the language's code, e.g. {@code de}: the name of its field */
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	TokenStream stem(final TokenStream words) {
		return stemmer.apply(words);
	}

	/**
	 * @param codes e.g. {@code en,de,fr,ru}, as in {@code mizoine.search.languages}
	 * @throws IllegalArgumentException for an empty list or an unknown code, naming the known ones
	 */
	public static List<Language> parse(final String codes) {
		final List<Language> languages = Arrays.stream(codes.split(","))
				.map(String::strip)
				.filter(code -> !code.isEmpty())
				.map(Language::of)
				.distinct()
				.toList();
		if (languages.isEmpty()) {
			throw new IllegalArgumentException("mizoine.search.languages names no language; known: " + known());
		}
		return languages;
	}

	private static Language of(final String code) {
		return Arrays.stream(values()).filter(language -> language.code().equals(code.toLowerCase(Locale.ROOT)))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Unknown language '" + code + "' in mizoine.search.languages; known: " + known()));
	}

	private static String known() {
		return Arrays.stream(values()).map(Language::code).collect(Collectors.joining(", "));
	}
}
