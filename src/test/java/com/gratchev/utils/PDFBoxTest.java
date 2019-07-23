package com.gratchev.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.PDFText2HTML;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PDFBoxTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(PDFBoxTest.class);

	public static final String INVOICE_PDF = "Invoice251217.pdf";
	public static final String APPLE_PDF = "Legal - Website Terms of Use - Apple.pdf";
	public static final String PRINTED_WEB_PAGE_PDF = "Aiptek MobileCinema Q20 Pico Projektor Produktbeschreibung.pdf";
	public static final String DATASHEET_PDF = "MobileCinema-Q20_Datasheet.pdf";

	private HTMLtoMarkdown htmLtoMarkdown = new HTMLtoMarkdown();

	private void logText(final PDDocument document) throws IOException {
		final PDFTextStripper stripper = new PDFTextStripper();
		stripper.setAddMoreFormatting(true);
		stripper.setLineSeparator("\n");
		
		LOGGER.info(stripper.getText(document));

		final String html = new PDFText2HTML().getText(document);
		LOGGER.info(html);
		
		final String md = htmLtoMarkdown.convert(Jsoup.parse(html));
		LOGGER.info(md);
	}



	@Test
	public void testRenderPdfToImage() throws InvalidPasswordException, IOException {
		// https://stackoverflow.com/questions/23326562/apache-pdfbox-convert-pdf-to-images

		try (final PDDocument document = Loader.loadPDF(PDFBoxTest.class.getResourceAsStream(INVOICE_PDF))) {
			
			assertEquals(1, document.getNumberOfPages());
			
			PDFRenderer pdfRenderer = new PDFRenderer(document);
			for (int page = 0; page < document.getNumberOfPages(); ++page)
			{ 
				final BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
				final File tempFile = File.createTempFile("pdfbox-test", "-" + (page+1) + ".png");
	
				// suffix in filename will be used as the file format
				ImageIOUtil.writeImage(bim, tempFile.getAbsolutePath(), 300);
				
				LOGGER.info("Image written to temp file: " + tempFile.getAbsolutePath());
			}
		}
	}

	@Test
	public void testParsePdfText() throws InvalidPasswordException, IOException {
		
		try (final PDDocument document = Loader.loadPDF(PDFBoxTest.class.getResourceAsStream(INVOICE_PDF))) {
			
			assertEquals(1, document.getNumberOfPages());
			
			logText(document);
		}
	}

	@Test
	public void testParseGermanPdfText() throws InvalidPasswordException, IOException {
		
		try (final PDDocument document = Loader.loadPDF(PDFBoxTest.class.getResourceAsStream(PRINTED_WEB_PAGE_PDF))) {
			
			assertEquals(1, document.getNumberOfPages());
			
			logText(document);
		}
	}

	@Test
	public void testParsePdfDatasheet() throws InvalidPasswordException, IOException {
		
		try (final PDDocument document = Loader.loadPDF(PDFBoxTest.class.getResourceAsStream(DATASHEET_PDF))) {
			
			assertEquals(2, document.getNumberOfPages());
			
			logText(document);
		}
	}

	@Test
	public void testParsePdfDatasheetOverHTML() throws InvalidPasswordException, IOException {
		
		try (final PDDocument document = Loader.loadPDF(PDFBoxTest.class.getResourceAsStream(DATASHEET_PDF))) {
			
			assertEquals(2, document.getNumberOfPages());
			
			logText(document);
		}
	}

	@Test
	public void testParseLongPdfText() throws InvalidPasswordException, IOException {
		
		try (final PDDocument document = Loader.loadPDF(
				PDFBoxTest.class.getResourceAsStream(APPLE_PDF))) {
			
			assertEquals(6, document.getNumberOfPages());
			
			logText(document);
		}
	}
}
