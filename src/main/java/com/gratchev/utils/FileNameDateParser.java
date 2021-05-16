package com.gratchev.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileNameDateParser {
	private final List<Pattern> patterns = new ArrayList<>();
	public void addTemplate(final String template) {
		patterns.add(Pattern.compile(template));
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
