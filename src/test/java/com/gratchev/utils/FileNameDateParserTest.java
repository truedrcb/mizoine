package com.gratchev.utils;

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
		parser.addTemplate(".*{yyyy}_{MM}_{dd}.*");
		Date d = parser.parse("Kontoauszug_1234567890_Nr_2021_001_per_2021_01_04.pdf");
		assertThat(d).hasYear(2021).hasMonth(1).hasDayOfMonth(4);
	}

	@Test
	void extractMultipleDateFormats() throws ParseException {
		final FileNameDateParser parser = new FileNameDateParser();
		parser.addTemplate(".*{yyyy}_{MM}_{dd}.*");
		parser.addTemplate(".*_{dd}\\.{MM}\\.{yyyy}_.*");
		assertThat(parser.parse("Depotauszug_vom_01.04.2021_zu_Depot_1234567_-_202104020987JD65.pdf")).hasYear(2021).hasMonth(4).hasDayOfMonth(1);
		assertThat(parser.parse("Kreditkartenabrechnung_1234xxxxxxxx5678_per_2021_01_22.pdf")).hasYear(2021).hasMonth(1).hasDayOfMonth(22);
	}

	@Test
	void extractMultipleDateTimeFormats() throws ParseException {
		final FileNameDateParser parser = new FileNameDateParser();
		parser.addTemplate(".*{yyyy}-{MM}-{dd}_{hh}-{mm}.*");
		parser.addTemplate(".*{yyyy}-{MM}-{dd}.*");
		assertThat(parser.parse("scan_2023-07-28_20-49.pdf")).hasYear(2023).hasMonth(7).hasDayOfMonth(28).hasHourOfDay(20).hasMinute(49);
		assertThat(parser.parse("test_2023-07-28.pdf")).hasYear(2023).hasMonth(7).hasDayOfMonth(28).hasHourOfDay(0).hasMinute(0);
		assertThat(parser.parse("test_2023-07-28_66-33.pdf")).hasYear(2023).hasMonth(7).hasDayOfMonth(28).hasHourOfDay(0).hasMinute(0);
		assertThat(parser.parse("test_2023-07-28_23-59.jpg")).hasHourOfDay(23).hasMinute(59);
		assertThat(parser.parse("test_2023-07-28_23-60.jpg")).hasHourOfDay(0).hasMinute(0);
	}
}
