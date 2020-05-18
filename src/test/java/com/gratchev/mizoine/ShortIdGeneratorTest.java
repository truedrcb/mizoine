package com.gratchev.mizoine;

import static com.gratchev.mizoine.ShortIdGenerator.intToMizCode;
import static com.gratchev.mizoine.ShortIdGenerator.mizCodeFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShortIdGeneratorTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShortIdGeneratorTest.class);
	final static int MINUTES_IN_YEAR = 365 * 24 * 60;

	@Test
	void test() {
		ShortIdGenerator cut = new ShortIdGenerator();

		LOGGER.info(ShortIdGenerator.intToCode(1000));
		LOGGER.info(ShortIdGenerator.intToCode(0));
		LOGGER.info(ShortIdGenerator.intToCode(-1));
		LOGGER.info(ShortIdGenerator.intToCode(-100));
		LOGGER.info(ShortIdGenerator.intToCode(-99999));

		LOGGER.info(cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 35), ZoneOffset.UTC)));
		LOGGER.info(cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 29), LocalTime.of(13, 35), ZoneOffset.UTC)));
		LOGGER.info(cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 36), ZoneOffset.UTC)));
		LOGGER.info(cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 37), ZoneOffset.UTC)));
		LOGGER.info(cut.createId(ZonedDateTime.of(LocalDate.of(2015, 1, 1), LocalTime.of(23, 1), ZoneOffset.UTC)));

		assertEquals(cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 35), ZoneOffset.UTC)), cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 35), ZoneOffset.UTC)));
		assertNotEquals(cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 35), ZoneOffset.UTC)), cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 36), ZoneOffset.UTC)));
		assertNotEquals(cut.createId(ZonedDateTime.of(LocalDate.of(2017, 11, 28), LocalTime.of(13, 35), ZoneOffset.UTC)), cut.createId(ZonedDateTime.of(LocalDate.of(2015, 11, 28), LocalTime.of(13, 35), ZoneOffset.UTC)));
	}

	@Test
	void idCollision() {
		final Set<String> usedIds = new TreeSet<String>();
		final ShortIdGenerator cut = new ShortIdGenerator();

		final ZonedDateTime now = ZonedDateTime.now();
		final String id1 = cut.createId(now);

		assertEquals(id1, cut.createId(now));
		assertEquals(id1, cut.createId(now, usedIds));

		usedIds.add(id1);
		final String id2 = cut.createId(now, usedIds);

		assertNotEquals(id1, id2);

		assertEquals(id2, cut.createId(now, usedIds));

		usedIds.add(id2);
		final String id3 = cut.createId(now, usedIds);

		assertNotEquals(id1, id3);
		assertNotEquals(id2, id3);
	}

	@Test
	void minuteBasedId32and64() {
		LOGGER.info("Number of years, until minute-based Ids overflow: {}", Integer.MAX_VALUE / MINUTES_IN_YEAR);
		assertThat(ShortIdGenerator.ALL_64_CHARS_SORTED.chars()).hasSize(64).isSorted();
		for (long i = 1, max = 64; i <= 6; i++, max *= 64) {
			LOGGER.info("Id64 positions: {}. Max combinations: {}. Years to overflow: {}", i, max,
					max / MINUTES_IN_YEAR);
		}
		LOGGER.info("Radix 32. Positions, needed to encode max int {}", 32 / 5 + 1);
		for (long i = 1, max = 32; i <= 7; i++, max *= 32) {
			LOGGER.info("Id32 positions: {}. Max combinations: {}. Years to overflow: {}", i, max,
					max / MINUTES_IN_YEAR);
		}
	}

	@Test
	void minuteBasedId36() {
		LOGGER.info("Number of years, until minute-based Ids overflow: {}", Integer.MAX_VALUE / MINUTES_IN_YEAR);
		assertThat(ShortIdGenerator.ALL_36_CHARS_SORTED.chars()).hasSize(36).isSorted();
		final String maxIntRadix36 = Integer.toString(Integer.MAX_VALUE, 36);
		LOGGER.info("Max int, radix 36 {}, size {}", maxIntRadix36, maxIntRadix36.length());
		for (long i = 1, max = 36; i <= maxIntRadix36.length(); i++, max *= 36) {
			LOGGER.info("Id36 positions: {}. Max combinations: {}. Years to overflow: {}", i, max,
					max / MINUTES_IN_YEAR);
		}
		assertThat(List.of(0, 1, 2, 3, 10, 11, 31, 33, 60, 63, 65, 110, 111, 112, 256, 2222, 2232, 2242, 44444, 44544,
				33554431)).isSorted().extracting(n -> intToMizCode(n)).isSorted();
		assertThat(intToMizCode(0)).isEqualTo("00000");
		assertThat(intToMizCode(256)).isEqualTo("00074");
		assertThat(intToMizCode(60466175)).isEqualTo("zzzzz");
	}
	
	@Test
	void mizoineEpochBasedId36() {
		// Mizone was introduced in 2017.
		// Assume 2017-01-01 00:00 UTC as zero Id: 00000.
		
		final ZonedDateTime mizEpochStart = ZonedDateTime.of(LocalDate.of(2017, 1, 1), LocalTime.of(0, 0), ZoneOffset.UTC);
		LOGGER.info("Mizoine Epoch Start: " + mizEpochStart);
		LOGGER.info("Now UTC: " + ZonedDateTime.now(ZoneOffset.UTC));
		LOGGER.info("Now local:" + ZonedDateTime.now());
		LOGGER.info("Now UTC (truncated to nearest minute): " + ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES));
		final long codeNow = ChronoUnit.MINUTES.between(mizEpochStart, ZonedDateTime.now(ZoneOffset.UTC));
		assertThat(codeNow).isLessThan(Integer.MAX_VALUE);
		LOGGER.info("Now UTC (minutes from Epoch Start): " + codeNow + " Id36: " + intToMizCode((int)codeNow));
		
		assertThat(mizCodeFor(mizEpochStart)).isEqualTo("00000");
		final ZonedDateTime code5EpochEnd = mizEpochStart.plus(60466175, ChronoUnit.MINUTES);
		LOGGER.info("Mizoine Epoch End of 5 positions code: " + code5EpochEnd);
		assertThat(mizCodeFor(code5EpochEnd)).isEqualTo("zzzzz");
		
		assertThat(mizCodeFor(mizEpochStart.minus(1, ChronoUnit.MINUTES))).isEqualTo("-00001");
		assertThat(mizCodeFor(mizEpochStart.minus(60466175, ChronoUnit.MINUTES))).isEqualTo("-zzzzz");
		
		assertThrows(RuntimeException.class, () -> mizCodeFor(mizEpochStart.minus(60466176, ChronoUnit.MINUTES)));
		assertThrows(RuntimeException.class, () -> mizCodeFor(mizEpochStart.plus(60466176, ChronoUnit.MINUTES)));

		assertThat(mizCodeFor(ZonedDateTime.now(ZoneOffset.UTC))).isEqualTo(mizCodeFor(ZonedDateTime.now()));
	}
}
