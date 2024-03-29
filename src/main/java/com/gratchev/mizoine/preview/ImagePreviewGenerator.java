package com.gratchev.mizoine.preview;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImagePreviewGenerator implements AttachmentPreviewGenerator {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImagePreviewGenerator.class);
	
	private final String[] compatibleExtensions = {".png", ".jpg", ".jpeg", ".gif", ".webp"};

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
		final BufferedImage image = convertTo(sourceImageStream, maxWidth, maxHeight, BufferedImage.TYPE_4BYTE_ABGR);

		// https://docs.oracle.com/javase/tutorial/2d/images/saveimage.html
		ImageIO.write(image, "png", outputFile);
		LOGGER.debug("Saved to: " + outputFile.getAbsolutePath());
	}

	public static void convertToJpg(final InputStream sourceImageStream, final int maxWidth, final int maxHeight, final File outputFile) 
			throws IOException {
		final BufferedImage image = convertTo(sourceImageStream, maxWidth, maxHeight, BufferedImage.TYPE_3BYTE_BGR);

		//saveJpgWithQuality(outputFile, image, 1f);
		ImageIO.write(image, "jpg", outputFile);
		LOGGER.debug("Saved to: " + outputFile.getAbsolutePath());
	}

	/**
	 * Use in case if compression quality is to be set explicitly.
	 * See https://stackoverflow.com/questions/17108234/setting-jpg-compression-level-with-imageio-in-java
	 *
	 * @param outputFile
	 * @param image
	 * @param compressionQuality
	 * @throws IOException
	 */
	@SuppressWarnings("unused")
	private static void saveJpgWithQuality(final File outputFile, final BufferedImage image, float compressionQuality) throws IOException {
		final ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
		final ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
		jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		jpgWriteParam.setCompressionQuality(compressionQuality);
		
		try(final ImageOutputStream createImageOutputStream = ImageIO.createImageOutputStream(outputFile)) {
			jpgWriter.setOutput(createImageOutputStream);
			jpgWriter.write(null, new IIOImage(image, null, null), jpgWriteParam);
		} finally {
			jpgWriter.dispose();
		}
	}

	private static BufferedImage convertTo(final InputStream sourceImageStream, final int maxWidth, final int maxHeight, int imageType) throws IOException {
		// https://docs.oracle.com/javase/tutorial/2d/images/loadimage.html
		// https://github.com/haraldk/TwelveMonkeys
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

		final BufferedImage biCopy = new BufferedImage(targetWidth, targetHeight, imageType);

		final Graphics2D g = biCopy.createGraphics();
		// https://stackoverflow.com/questions/29105154/smooth-bufferimage-edges
		try {
			// https://stackoverflow.com/questions/15558202/how-to-resize-image-in-java
			g.drawImage(bi.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);
		} finally {
			g.dispose();
		}
		return biCopy;
	}
	
	
	@Override
	public AttachmentPreview generatePreviews(final File file, final File targetDir) {
		LOGGER.debug("Generating image previews");
		final AttachmentPreview ap = new AttachmentPreview();
		ap.preview = new File(targetDir, PREVIEW_JPG);
		try (final FileInputStream sourceImageStream = new FileInputStream(file)) {
			convertToJpg(sourceImageStream, 1920, 2160, ap.preview);
		} catch (final Exception e) {
			ap.preview = null;
			LOGGER.error("Error converting image: " + file.getAbsolutePath(), e);
		}
		ap.thumbnail = new File(targetDir, THUMBNAIL_JPG);
		try (final FileInputStream sourceImageStream = new FileInputStream(file)) {
			convertToJpg(sourceImageStream, 320, 400, ap.thumbnail);
		} catch (final Exception e) {
			ap.thumbnail = null;
			LOGGER.error("Error converting image: " + file.getAbsolutePath(), e);
		}
		return ap;
	}

	@Override
	public String extractMarkdown(final File file) {
		return null;
	}

}
