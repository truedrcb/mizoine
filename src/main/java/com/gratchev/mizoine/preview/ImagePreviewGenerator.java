package com.gratchev.mizoine.preview;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImagePreviewGenerator implements AttachmentPreviewGenerator {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImagePreviewGenerator.class);
	
	private String[] compatibleExtensions = {".png", ".jpg", ".jpeg", ".gif"};

	@Override
	public boolean isCompatibleWith(final File file) {
		final String fileName = file.getName().toLowerCase();
		for (final String ext : compatibleExtensions) {
			if (fileName.endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	public static void convertToPng(final InputStream sourceImageStream, final int maxWidth, final int maxHeight, final File outputFile) 
			throws IOException {
		// https://docs.oracle.com/javase/tutorial/2d/images/loadimage.html
		final BufferedImage bi = ImageIO.read(sourceImageStream);
		
		if (bi == null) {
			LOGGER.error("Unable to read image. No registered ImageReader claims to be able to read the resulting stream.");
			throw new IOException("ImageReader cannot decode image.");
		}
		
		final int width = bi.getWidth();
		final int height = bi.getHeight();
		
		int targetWidth = width;
		int targetHeight = height;
		
		if (targetWidth > maxWidth) {
			targetHeight = targetHeight * maxWidth / targetWidth;
			targetWidth = maxWidth;
		}

		if (targetHeight > maxHeight) {
			targetWidth = targetWidth * maxHeight / targetHeight;
			targetHeight = maxHeight;
		}

		LOGGER.debug("Converting image: " + width + "x" + height + " to " + targetWidth + "x" + targetHeight);

		final BufferedImage biCopy = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_4BYTE_ABGR);

		final Graphics2D g = biCopy.createGraphics();
		// https://stackoverflow.com/questions/29105154/smooth-bufferimage-edges
		try {
			// https://stackoverflow.com/questions/15558202/how-to-resize-image-in-java
			g.drawImage(bi.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);
		} finally {
			g.dispose();
		}

		// https://docs.oracle.com/javase/tutorial/2d/images/saveimage.html
		ImageIO.write(biCopy, "png", outputFile);
		LOGGER.debug("Saved to: " + outputFile.getAbsolutePath());
	}
	
	
	@Override
	public AttachmentPreview generatePreviews(final File file, final File targetDir) throws IOException {
		LOGGER.debug("Generating image previews");
		final AttachmentPreview ap = new AttachmentPreview();
		ap.preview = new File(targetDir, PREVIEW_PNG);
		try (final FileInputStream sourceImageStream = new FileInputStream(file)) {
			convertToPng(sourceImageStream, 1920, 2160, ap.preview);
		} catch (final Exception e) {
			ap.preview = null;
			LOGGER.error("Error converting image: " + file.getAbsolutePath(), e);
		}
		ap.thumbnail = new File(targetDir, THUMBNAIL_PNG);
		try (final FileInputStream sourceImageStream = new FileInputStream(file)) {
			convertToPng(sourceImageStream, 320, 400, ap.thumbnail);
		} catch (final Exception e) {
			ap.thumbnail = null;
			LOGGER.error("Error converting image: " + file.getAbsolutePath(), e);
		}
		return ap;
	}

	@Override
	public String extractMarkdown(final File file) throws IOException {
		return null;
	}

}
