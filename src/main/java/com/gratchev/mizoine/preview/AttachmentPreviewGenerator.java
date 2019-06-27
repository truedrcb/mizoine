package com.gratchev.mizoine.preview;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface AttachmentPreviewGenerator {

	static final String PREVIEW_PNG = "preview.png";
	static final String PREVIEW_PAGE_PREFIX = "preview-";
	static final String PREVIEW_PAGE_SUFFIX = ".png";

	static final String THUMBNAIL_PNG = "thumbnail.png";
	static final String THUMBNAIL_PAGE_PREFIX = "thumbnail-";
	static final String THUMBNAIL_PAGE_SUFFIX = ".png";

	
	boolean isCompatibleWith(File file);
	
	public static class AttachmentPreview {
		public File thumbnail;
		public File preview;
		
		public List<File> thumbnails;
		public List<File> previews;
	}
	
	AttachmentPreview generatePreviews(File file, File outputDir) throws IOException;
	
	String extractMarkdown(File file) throws IOException;

}
