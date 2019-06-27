package com.gratchev.mizoine;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.Attachment.FileInfo;

public class FlexmarkUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FlexmarkUtils.class);

	public static String escapeQuotation(String string) {
		if (string == null) {
			return null;
		}
		
		return string.replace("\"", "\\\"");
	}

	private static String guessUri(final List<FileInfo> infos, final FileInfo info, final String uri) {
		if (infos != null && infos.size() > 0) {
			return infos.get(0).fullFileUri;
		}
		if (info != null) {
			return info.fullFileUri;
		}
		return uri;
	}

	public static String generateMarkdownFooterRefs(final String pageBaseUri, final ArrayList<Attachment> attachments) {
		final StringBuilder sb = new StringBuilder("\n\n");
		for (final Attachment attachment : attachments) {
			if (attachment.files == null || attachment.files.size() < 1) {
				continue;
			}
			final FileInfo fileInfo = attachment.files.get(0);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Footers for: " + attachment.id + " - " + fileInfo.fileName);
			}

			// Simple image
			generateRef(sb, attachment, guessUri(attachment.previews, attachment.preview, fileInfo.fullFileUri), ".lg");

			// Thumbnail
			generateRef(sb, attachment, guessUri(attachment.thumbnails, attachment.thumbnail, fileInfo.fullFileUri),
					"");
			
			// Attachment file
			generateRef(sb, attachment, fileInfo.fullFileUri, ".file");
			
			// Attachment page
			generateRef(sb, attachment, pageBaseUri + attachment.id, ".page");
			
		}
		final String markdownFooterRefs = sb.toString();
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Footers: " + markdownFooterRefs);
		}
		return markdownFooterRefs;
	}

	private static void generateRef(final StringBuilder sb, final Attachment attachment, 
			final String uri, final String suffix) {
		sb.append('[');
		sb.append(attachment.id);
		sb.append(suffix + "]: ");
		sb.append(uri);
		if (attachment.meta != null && attachment.meta.title != null) {
			sb.append(" \"");
			sb.append(escapeQuotation(attachment.meta.title));
			sb.append("\"");
		}
		sb.append('\n');
	}
}
