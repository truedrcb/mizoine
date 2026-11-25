package com.gratchev.mizoine.search;

/** How the results were found (step 57, D53): the strictest level that found anything. */
public enum Strictness implements ApiCode {
	/** Every word. */
	ALL_WORDS,
	/** Not every word: any of them. */
	ANY_WORD,
	/** None of the words as written: words of a similar spelling. */
	SIMILAR_SPELLING,
	/** Nothing found. */
	NONE
}
