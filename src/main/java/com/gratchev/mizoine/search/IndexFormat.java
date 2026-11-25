package com.gratchev.mizoine.search;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.util.Version;

/**
 * What an index was made by (D58): the Lucene major version and Mizoine's analysis. Each format has its own folder,
 * e.g. {@code lucene10-a1-de-en-fr-ru}; an index is opened only by the Mizoine of its format, or read meanwhile
 * when only the analysis differs (D57).
 */
record IndexFormat(int luceneMajor, String analysis) {

	private static final Pattern FOLDER = Pattern.compile("lucene(\\d{1,4})-(a\\d+(?:-[a-z]{2})+)");

	static IndexFormat of(final SearchAnalysis analysis) {
		return new IndexFormat(Version.LATEST.major, analysis.format());
	}

	/** @return the format of an index folder's name; empty for any other name (e.g. one being built) */
	static Optional<IndexFormat> parse(final String folder) {
		final Matcher matcher = FOLDER.matcher(folder);
		return matcher.matches()
				? Optional.of(new IndexFormat(Integer.parseInt(matcher.group(1)), matcher.group(2)))
				: Optional.empty();
	}

	String folder() {
		return "lucene" + luceneMajor + "-" + analysis;
	}
}
