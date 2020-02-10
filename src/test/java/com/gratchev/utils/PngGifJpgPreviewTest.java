package com.gratchev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.preview.ImagePreviewGenerator;

public class PngGifJpgPreviewTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(PngGifJpgPreviewTest.class);

	public static final String GIF_DEVCALC = "DevCalc-2.02.gif";
	public static final String JPG_GUITAR = "20190310_174614.jpg";
	public static final String JPG_CMYK_LOGO = "Logo MaQua Neckarstadt-West 2017.jpg";
	public static final String PNG_SCREENSHOT = "Eclipse-Maven-dependencies.png";
	public static final String PNG_FLYER = "FlyerBear3web.png";
	public static final String PNG_TRANSPARENT = "Mizoine-logo-transparent.png";
	public static final String SVG_LOGO = "icon-spring-boot.svg";
	public static final String SVG_DEVCALC_LOGO = "V.svg";
	public static final String WMF_LOGO = "V.wmf";
	private static File tempDir;
	
	@BeforeAll
	public static void setupClass() throws IOException {
		tempDir = Files.createTempDirectory(new File("target").toPath(), "PngGifJpgPreviewTest-").toFile();

		LOGGER.info("Output directory: " + tempDir.getAbsolutePath());
	}

	@Test
	public void testBigJpgToPng() throws IOException {
		assertExactImageConversionToPng(JPG_GUITAR, 4032, 2268);
		assertImageConversionToPng(JPG_GUITAR, 403, 226);
		assertImageConversionToPng(JPG_GUITAR, 804, 452);
	}

	@Test
	public void testBigJpgToJpg() throws IOException {
		assertImageConversionToJpg(JPG_GUITAR, 403, 226);
		assertImageConversionToJpg(JPG_GUITAR, 804, 452);
	}

	@Test
	@Disabled("https://stackoverflow.com/questions/7177655/java-imageio-iioexception-unsupported-image-type")
	public void testCmykJpgToPng() throws IOException {
		assertExactImageConversionToPng(JPG_CMYK_LOGO, 4032, 2268);
		assertImageConversionToPng(JPG_CMYK_LOGO, 403, 226);
		assertImageConversionToPng(JPG_CMYK_LOGO, 804, 452);
	}

	@Test
	public void testGifToPng() throws IOException {
		assertExactImageConversionToPng(GIF_DEVCALC, 444, 116);
		assertImageConversionToPng(GIF_DEVCALC, 384, 100);
	}

	public void testGifToJpg() throws IOException {
		assertImageConversionToJpg(GIF_DEVCALC, 384, 100);
	}

	@Test
	public void testPngToPng() throws IOException {
		assertExactImageConversionToPng(PNG_FLYER, 452, 640);
		assertImageConversionToPng(PNG_FLYER, 452/3, 212);
		assertExactImageConversionToPng(PNG_SCREENSHOT, 854, 751);
		assertImageConversionToPng(PNG_SCREENSHOT, 854/2, 751/2);
		assertExactImageConversionToPng(PNG_TRANSPARENT, 210, 210);
		assertImageConversionToPng(PNG_TRANSPARENT, 133, 133);
	}
	
	@Test
	public void testPngToJpg() throws IOException {
		assertImageConversionToJpg(PNG_FLYER, 452/3, 212);
		assertImageConversionToJpg(PNG_SCREENSHOT, 854/2, 751/2);
		assertImageConversionToJpg(PNG_TRANSPARENT, 133, 133);
	}
	
	private void assertExactImageConversionToPng(final String resourceFileName, 
		final int expectedWidth,
		final int expectedHeight) throws IOException {
		// https://docs.oracle.com/javase/tutorial/2d/images/loadimage.html
		final BufferedImage bi = ImageIO.read(PngGifJpgPreviewTest.class.getResourceAsStream(resourceFileName));
				
		LOGGER.info("Image: " + bi.getWidth() + "x" + bi.getHeight());
		
		assertThat(bi.getWidth()).isEqualTo(expectedWidth);
		assertThat(bi.getHeight()).isEqualTo(expectedHeight);
		
		final BufferedImage biCopy = new BufferedImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_ARGB);
		
		final Graphics g = biCopy.getGraphics();
		
		try {
			g.drawImage(bi, 0, 0, expectedWidth, expectedHeight, null);
		} finally {
			g.dispose();
		}
		
		// https://docs.oracle.com/javase/tutorial/2d/images/saveimage.html
		final File outputFile = new File(tempDir, resourceFileName + ".png");
		ImageIO.write(biCopy, "png", outputFile);
		LOGGER.info("Saved to: " + outputFile.getAbsolutePath());
		
		// Read image back
		final BufferedImage bi2 = ImageIO.read(outputFile);
		LOGGER.info("Image.png: " + bi2.getWidth() + "x" + bi2.getHeight());
		
		assertThat(bi2.getWidth()).isEqualTo(expectedWidth);
		assertThat(bi2.getHeight()).isEqualTo(expectedHeight);
	}

	private void assertImageConversionToPng(final String resourceFileName, final int targetWidth,
			final int targetHeight) throws IOException {
		
		final File outputFile = new File(tempDir, resourceFileName + "_" + targetWidth + "x" + targetHeight + ".png");
		ImagePreviewGenerator.convertToPng(PngGifJpgPreviewTest.class.getResourceAsStream(resourceFileName), targetWidth, targetHeight, outputFile);

		// Read image back
		final BufferedImage bi2 = ImageIO.read(outputFile);
		LOGGER.info("Image.png: " + bi2.getWidth() + "x" + bi2.getHeight());

		assertThat(bi2.getWidth()).isEqualTo(targetWidth);
		assertThat(bi2.getHeight()).isEqualTo(targetHeight);
	}

	private void assertImageConversionToJpg(final String resourceFileName, final int targetWidth,
			final int targetHeight) throws IOException {
		
		final File outputFile = new File(tempDir, resourceFileName + "_" + targetWidth + "x" + targetHeight + ".jpg");
		ImagePreviewGenerator.convertToJpg(PngGifJpgPreviewTest.class.getResourceAsStream(resourceFileName), targetWidth, targetHeight, outputFile);

		// Read image back
		final BufferedImage bi2 = ImageIO.read(outputFile);
		LOGGER.info("Image.jpg: " + bi2.getWidth() + "x" + bi2.getHeight());

		assertThat(bi2.getWidth()).isEqualTo(targetWidth);
		assertThat(bi2.getHeight()).isEqualTo(targetHeight);
	}
}
