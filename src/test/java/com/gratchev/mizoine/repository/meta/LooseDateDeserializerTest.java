package com.gratchev.mizoine.repository.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class LooseDateDeserializerTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void testParseSimpleDate() throws JsonProcessingException {
		final BaseMeta m1 = objectMapper.readValue("{\"creationDate\":\"01-02-2003\"}", BaseMeta.class);
		assertThat(m1.creationDate).hasDayOfMonth(1).hasMonth(2).hasYear(2003);
	}

	@Test
	void testParseDates() throws JsonProcessingException {
		assertThat(parseDate("02/03/1904")).hasDayOfMonth(2).hasMonth(3).hasYear(1904);
		assertThat(parseDate("05-06-1907")).hasDayOfMonth(5).hasMonth(6).hasYear(1907);
	}

	@Test
	void testParseDateTimes() throws JsonProcessingException {
		assertThat(parseDate("08-09-1980 10:11")).hasDayOfMonth(8).hasMonth(9).hasYear(1980).hasHourOfDay(10).hasMinute(11);
		assertThat(parseDate("12/11/1955 13:14")).hasDayOfMonth(12).hasMonth(11).hasYear(1955).hasHourOfDay(13).hasMinute(14);
	}

	@Test
	void testParseDateTimeSeconds() throws JsonProcessingException {
		assertThat(parseDate("15-10-1966 17:18:19")).hasDayOfMonth(15).hasMonth(10).hasYear(1966).hasHourOfDay(17).hasMinute(18).hasSecond(19);
		assertThat(parseDate("20/03/1974 21:22:23")).hasDayOfMonth(20).hasMonth(3).hasYear(1974).hasHourOfDay(21).hasMinute(22).hasSecond(23);
	}

	@Test
	void testParseDateTimeZone() throws JsonProcessingException {
		assertThat(parseDate("15-10-1966 17:18:19+0100")).hasDayOfMonth(15).hasMonth(10).hasYear(1966).hasHourOfDay(17).hasMinute(18).hasSecond(19);
		assertThat(parseDate("20/03/1974 21:22:23+0530")).hasDayOfMonth(20).hasMonth(3).hasYear(1974).hasHourOfDay(16).hasMinute(52).hasSecond(23);
	}

	private Date parseDate(final String formattedDate) throws JsonProcessingException {
		return objectMapper.readValue("{\"creationDate\":\"" + formattedDate + "\"}", BaseMeta.class).creationDate;
	}
}
