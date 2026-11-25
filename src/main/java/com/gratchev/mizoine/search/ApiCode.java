package com.gratchev.mizoine.search;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** An enum of the search API, named there in lower case with dashes: {@code NOT_INDEXED} → {@code not-indexed}. */
interface ApiCode {

	/** The enum constant's own name. */
	String name();

	/** @return the value as the API names it */
	@JsonValue
	default String code() {
		return name().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}
