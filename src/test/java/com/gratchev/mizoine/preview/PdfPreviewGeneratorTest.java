package com.gratchev.mizoine.preview;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.preview.AttachmentPreviewGenerator.AttachmentPreview;
import com.gratchev.mizoine.repository.TempRepositoryUtils;
import com.gratchev.utils.FileUtils;
import com.gratchev.utils.PDFBoxTest;

public class PdfPreviewGeneratorTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfPreviewGeneratorTest.class);
	
	@Test
	public void generatePdfPreview() throws IOException {
		final PdfPreviewGenerator generator = new PdfPreviewGenerator();

		final File tempDir = Files.createTempDirectory("test-pdf-out").toFile();
		LOGGER.info("Generating previews into:  " +  tempDir.getAbsolutePath());
		try {
			final File samplePdfFile = FileUtils.urlToFile(PDFBoxTest.class.getResource(PDFBoxTest.APPLE_PDF));
			final AttachmentPreview ap = generator.generatePreviews(samplePdfFile, tempDir);
			
			assertNotNull(ap);
			
			assertEquals(6, ap.previews.size());
			assertEquals(6, ap.thumbnails.size());

			assertImageHasSize(ap.preview);
			for (final File f : ap.previews) {
				assertImageHasSize(f);
			}
			assertImageHasSize(ap.thumbnail);
			for (final File f : ap.thumbnails) {
				assertImageHasSize(f);
			}
			
		} finally {
			TempRepositoryUtils.printDirectory(tempDir);
			TempRepositoryUtils.removeDirectory(tempDir);
		}
	}

	public static void assertImageHasSize(final File f) throws IOException {
		LOGGER.info("Preview image: " + f.getAbsolutePath());
		final BufferedImage image = ImageIO.read(f);
		LOGGER.info("Size: " + image.getWidth() + "x" + image.getHeight());
		
		assertTrue(image.getWidth() > 10);
		assertTrue(image.getHeight() > 10);
	}

}
