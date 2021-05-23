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
		parser.addTemplate(".*(?<yyyy>(20|19)..)_(?<MM>..)_(?<dd>..).*");
		Date d = parser.parse("Kontoauszug_1234567890_Nr_2021_001_per_2021_01_04.pdf");
		assertThat(d).hasYear(2021).hasMonth(1).hasDayOfMonth(4);
	}

	@Test
	void extractMultipleDateFormats() throws ParseException {
		final FileNameDateParser parser = new FileNameDateParser();
		parser.addTemplate(".*(?<yyyy>(20|19)..)_(?<MM>\\d\\d)_(?<dd>..).*");
		parser.addTemplate(".*_(?<dd>..)\\.(?<MM>..)\\.(?<yyyy>....)_.*");
		assertThat(parser.parse("Depotauszug_vom_01.04.2021_zu_Depot_1234567_-_202104020987JD65.pdf")).hasYear(2021).hasMonth(4).hasDayOfMonth(1);
		assertThat(parser.parse("Kreditkartenabrechnung_1234xxxxxxxx5678_per_2021_01_22.pdf")).hasYear(2021).hasMonth(1).hasDayOfMonth(22);
	}
}
