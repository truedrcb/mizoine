package com.gratchev.mizoine;

import static com.gratchev.mizoine.ShortIdGenerator.intToMizCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

		LOGGER.info(cut.createId("2017-11-28-133544a"));
		LOGGER.info(cut.createId("2017-11-28-133544b"));
		LOGGER.info(cut.createId("2017-11-29-133544a"));
		LOGGER.info(cut.createId("2017-11-28-133544c"));
		LOGGER.info(cut.createId("2017-11-28-133544d"));
		LOGGER.info(cut.createId("2017-11-28-133545a"));
		LOGGER.info(cut.createId("2015-01-01-230112z"));

		assertEquals(cut.createId("2017-11-28-133544a"), cut.createId("2017-11-28-133544a"));
		assertNotEquals(cut.createId("2017-11-28-133544a"), cut.createId("2017-11-28-133544b"));
		assertNotEquals(cut.createId("2017-11-28-133544a"), cut.createId("1017-11-28-133544a"));
	}

	@Test
	void idCollision() {
		final Set<String> usedIds = new TreeSet<String>();
		final ShortIdGenerator cut = new ShortIdGenerator();

		final String id1 = cut.createId("Artem");

		assertEquals(id1, cut.createId("Artem"));
		assertEquals(id1, cut.createId("Artem", usedIds));

		usedIds.add(id1);
		final String id2 = cut.createId("Artem", usedIds);

		assertNotEquals(id1, id2);

		assertEquals(id2, cut.createId("Artem", usedIds));

		usedIds.add(id2);
		final String id3 = cut.createId("Artem", usedIds);

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
}
