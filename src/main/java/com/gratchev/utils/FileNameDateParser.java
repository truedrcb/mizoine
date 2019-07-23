package com.gratchev.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileNameDateParser {
	private static final Map<String, String> PLACEHOLDERS = Map.of(
			"{yyyy}", "(?<yyyy>(20|19)\\d\\d)",
			"{MM}", "(?<MM>(0|1)\\d)",
			"{dd}", "(?<dd>\\d\\d)"
	);
	private final List<Pattern> patterns = new ArrayList<>();

	/**
	 * Adds template (at the end of the list). Template is a regular expression with place holders. Currently supported:
	 * <ul>
	 *     <li><pre>{yyyy}</pre> - year (4 digits, starting with 19 or 20).</li>
	 *     <li><pre>{MM}</pre> - month (2 digits, from 00 to 12).</li>
	 *     <li><pre>{dd}</pre> - day of month (2 digits, starting with 0, 1, 2 or 3).</li>
	 * </ul>
	 * Note: Place holders are case-sensitive.
	 *
	 * @param template Template, based on regular expression
	 */
	public void addTemplate(final String template) {
		String regexTemplate = template;
		for (final Map.Entry<String, String> ph : PLACEHOLDERS.entrySet()) {
			regexTemplate = regexTemplate.replace(ph.getKey(), ph.getValue());
		}
		patterns.add(Pattern.compile(regexTemplate));
	}

	public Date parse(final String fileName) throws ParseException {
		for (final Pattern pattern : patterns) {
			final Matcher matcher = pattern.matcher(fileName);
			if (matcher.find()) {
				String y = matcher.group("yyyy");
				String m = matcher.group("MM");
				String d = matcher.group("dd");
				return new SimpleDateFormat("yyyyMMdd").parse(y + m + d);
			}
		}
		return null;
	}
}
