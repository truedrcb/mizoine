package com.gratchev.mizoine.preview;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.PDFText2HTML;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;
import com.gratchev.utils.HTMLtoMarkdown;;

public class PdfPreviewGenerator implements AttachmentPreviewGenerator {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfPreviewGenerator.class);

	private HTMLtoMarkdown pdfHTMLtoMarkdown = new HTMLtoMarkdown();

	@Override
	public boolean isCompatibleWith(final File file) {
		return file.getName().toLowerCase().endsWith(".pdf");
	}

	@Override
	public AttachmentPreview generatePreviews(final File file, final File targetDir) throws IOException {
		final AttachmentPreview ap = new AttachmentPreview();
		ap.previews = generatePages(file, targetDir, 120, PREVIEW_PAGE_PREFIX, PREVIEW_PAGE_SUFFIX);
		if (ap.previews != null && ap.previews.size() > 0) {
			ap.preview = new File(targetDir, PREVIEW_JPG);
			Files.copy(ap.previews.get(0), ap.preview);

			ap.thumbnails =  new ArrayList<>();
			int page = 0;
			for (final File previewFile : ap.previews) {
				final File thumbnailFile = new File(targetDir, THUMBNAIL_PAGE_PREFIX + ++page + THUMBNAIL_PAGE_SUFFIX);
				try (final FileInputStream sourceImageStream = new FileInputStream(previewFile)) {
					ImagePreviewGenerator.convertToJpg(sourceImageStream, 320, 400, thumbnailFile);
					ap.thumbnails.add(thumbnailFile);
				} catch (final Exception e) {
					LOGGER.error("Error converting image: " + previewFile.getAbsolutePath(), e);
				}
			}
		}
		//ap.thumbnails = generatePngPages(file, targetDir, 30, THUMBNAIL_PAGE_PREFIX, THUMBNAIL_PAGE_SUFFIX);
		if (ap.thumbnails != null && ap.thumbnails.size() > 0) {
			ap.thumbnail = new File(targetDir, THUMBNAIL_JPG);
			Files.copy(ap.thumbnails.get(0), ap.thumbnail);
		}
		return ap;
	}

	private List<File> generatePages(final File file, final File targetDir, final int dpi, 
			final String fileNamePrefix, final String fileNameSuffix) 
			throws IOException, InvalidPasswordException {
		final ArrayList<File> list = new ArrayList<>();
		
		try (final PDDocument document = Loader.loadPDF(file)) {
			PDFRenderer pdfRenderer = new PDFRenderer(document);
			for (int page = 0; page < document.getNumberOfPages(); ++page)
			{ 
				final String fileName = fileNamePrefix + (page + 1) + fileNameSuffix;
				final File outputFile = new File(targetDir, fileName);
				list.add(outputFile);
				final BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);

				// suffix in filename will be used as the file format
				final String absolutePath = outputFile.getAbsolutePath();
				ImageIOUtil.writeImage(bim, absolutePath, dpi);
				LOGGER.info("PDF screenshot written to file: " + absolutePath);
			}
		}
		
		return list;
	}

	@Override
	public String extractMarkdown(final File file) throws IOException {
		try (final PDDocument document = Loader.loadPDF(file)) {
			final PDFText2HTML pdfText2HTML = new PDFText2HTML();
			pdfText2HTML.setPageEnd("\n<hr>\n");
			final String html = pdfText2HTML.getText(document);
			LOGGER.trace(html);
			
			final String md = pdfHTMLtoMarkdown.convert(Jsoup.parse(html));
			return md;
		} 
	}

}
