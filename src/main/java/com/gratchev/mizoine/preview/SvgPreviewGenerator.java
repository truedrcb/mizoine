package com.gratchev.mizoine.preview;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;
import com.gratchev.mizoine.preview.AttachmentPreviewGenerator.AttachmentPreview;

public class SvgPreviewGenerator implements AttachmentPreviewGenerator {
	private static final Logger LOGGER = LoggerFactory.getLogger(SvgPreviewGenerator.class);

	@Override
	public boolean isCompatibleWith(final File file) {
		return file.getName().toLowerCase().endsWith(".svg");
	}

	@Override
	public AttachmentPreview generatePreviews(final File file, final File targetDir) throws IOException {
		final AttachmentPreview ap = new AttachmentPreview();
		LOGGER.debug("SVG preview: Just copy files to preview directory. File can be directly used as preview.");
		ap.preview = new File(targetDir, PREVIEW_PAGE_PREFIX + ".svg");
		Files.copy(file, ap.preview);
		ap.thumbnail = new File(targetDir, THUMBNAIL_PAGE_PREFIX + ".svg");
		Files.copy(file, ap.thumbnail);
		return ap;
	}

	@Override
	public String extractMarkdown(final File file) throws IOException {
		return null;
	}

}
