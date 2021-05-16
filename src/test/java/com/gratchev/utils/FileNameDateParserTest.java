package com.gratchev.utils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class FileNameDateParserTest {
	@Test
	void extractSimpleDate() throws ParseException {
		final FileNameDateParser parser = new FileNameDateParser();
		// https://stackoverflow.com/questions/415580/regex-named-groups-in-java
		// https://docs.oracle.com/javase/tutorial/i18n/format/simpleDateFormat.html
		parser.addTemplate(".*(?<yyyy>....)_(?<MM>..)_(?<dd>..).*");
		Date d = parser.parse("Kontoauszug_1013332455_Nr_2021_001_per_2021_01_04.pdf");
		assertThat(d).hasYear(2021).hasMonth(1).hasDayOfMonth(4);
	}
}
