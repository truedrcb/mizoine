package com.gratchev.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtilsTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileUtilsTest.class);

	@Test
	public void testOverwriteTextFile() throws IOException {
		final String testString = "Beste Grüße, Артём";
		LOGGER.info("Test string: '" + testString + "' length: " + testString.length());
		
		final File tempFile = File.createTempFile("utf-8-", "-test-overwrite");

		try {
			LOGGER.info("Temporary file: " + tempFile.getAbsolutePath());
			assertTrue(tempFile.exists());
			assertEquals(0, tempFile.length());
			
			FileUtils.overwriteTextFile(".", tempFile);
			assertEquals(1, tempFile.length());

			tempFile.delete();
			assertFalse(tempFile.exists());
		
			FileUtils.overwriteTextFile(testString, tempFile);
			
			assertTrue(tempFile.exists());
			
			assertEquals(25, tempFile.length());
			
			final String markdownText = FileUtils.readTextFile(tempFile);

			LOGGER.info("String from file: '" + markdownText + "' length: " + markdownText.length());
			
			assertEquals(testString, markdownText);

			
		} finally {
			if (tempFile.exists()) {
				LOGGER.info("Deleting temporary file: " + tempFile.getAbsolutePath());
				tempFile.delete();
			}
		}
	}

}
