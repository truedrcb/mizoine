package com.gratchev.mizoine;

import static org.junit.Assert.*;

import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import com.gratchev.mizoine.ShortIdGenerator;

public class ShortIdGeneratorTest {

	@Test
	public void test() {
		ShortIdGenerator cut = new ShortIdGenerator();

		System.out.println(ShortIdGenerator.intToCode(1000));
		System.out.println(ShortIdGenerator.intToCode(0));
		System.out.println(ShortIdGenerator.intToCode(-1));
		System.out.println(ShortIdGenerator.intToCode(-100));
		System.out.println(ShortIdGenerator.intToCode(-99999));

		System.out.println(cut.createId("2017-11-28-133544a"));
		System.out.println(cut.createId("2017-11-28-133544b"));
		System.out.println(cut.createId("2017-11-29-133544a"));
		System.out.println(cut.createId("2017-11-28-133544c"));
		System.out.println(cut.createId("2017-11-28-133544d"));
		System.out.println(cut.createId("2017-11-28-133545a"));
		System.out.println(cut.createId("2015-01-01-230112z"));
		
		assertEquals(cut.createId("2017-11-28-133544a"), cut.createId("2017-11-28-133544a"));
		assertNotEquals(cut.createId("2017-11-28-133544a"), cut.createId("2017-11-28-133544b"));
		assertNotEquals(cut.createId("2017-11-28-133544a"), cut.createId("1017-11-28-133544a"));
	}

	@Test
	public void idCollision() {
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
}
