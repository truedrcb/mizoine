package com.gratchev.mizoine;

/**
 * Texts for the API description (OpenAPI) that several endpoints share, so each case reads the same everywhere.
 * <p>
 * Pages that are HTML and JSON on one URL are one operation in the description: documented on the HTML method
 * with both media types; the JSON method is {@code @Hidden} (springdoc's own merging depends on method order).
 */
public final class ApiTexts {

	public static final String LOGIN_REDIRECT = "Browser not logged in: redirect to the login page";
	public static final String NOT_LOGGED_IN = "Not logged in (non-browser clients): HTTP Basic challenge";
	public static final String NOT_ACCEPTABLE = "Neither text/html nor application/json accepted";

	private ApiTexts() {
	}
}
