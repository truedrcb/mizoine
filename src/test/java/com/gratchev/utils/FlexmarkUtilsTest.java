package com.gratchev.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import com.gratchev.mizoine.FlexmarkUtils;

public class FlexmarkUtilsTest {

	@Test
	public void testQuotationEscape() {
		final String escaped = FlexmarkUtils.escapeQuotation("test \"fancy\"");
		assertEquals("test \\\"fancy\\\"", escaped);
		
		assertNull(FlexmarkUtils.escapeQuotation(null));
	}

}
