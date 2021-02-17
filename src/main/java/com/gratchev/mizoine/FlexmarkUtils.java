package com.gratchev.mizoine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.gratchev.mizoine.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.api.AttachmentApiController;
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

	@Deprecated
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


	public static String generateMarkdownFooterRefs(final String project, final String issueNumber, final Collection<String> mentIds) {
		final StringBuilder sb = new StringBuilder("\n\n");
		for (final String id : mentIds) {
			generateRef(sb, id, mentThumbnailPath(project, issueNumber, id), "");
			generateRef(sb, id, mentPreviewPath(project, issueNumber, id), ".lg");
			generateRef(sb, id, mentPagePath(project, issueNumber, id), ".page");
		}
		return sb.toString();
	}

	public static String mentThumbnailPath(final String project, final String issueNumber, final String mentId) {
		return mentImageBasePath(project, issueNumber, mentId) + "/thumbnail.jpg";
	}

	public static String mentPagePath(final String project, final String issueNumber, final String mentId) {
		return AttachmentApiController.getPageBaseUri(project, issueNumber) + mentId;
	}

	public static String mentPreviewPath(final String project, final String issueNumber, final String mentId) {
		return mentImageBasePath(project, issueNumber, mentId) + "/preview.jpg";
	}

	@NotNull
	private static String mentImageBasePath(String project, String issueNumber, String mentId) {
		return Repository.RESOURCE_URI_BASE + Repository.MIZOINE_DIR + '/' + project + '/' + issueNumber + '/' + mentId;
	}

	@Deprecated
	private static void generateRef(final StringBuilder sb, final Attachment attachment, final String uri, final String suffix) {
		sb.append('[');
		sb.append(attachment.id);
		sb.append(suffix);
		sb.append("]: ");
		sb.append(uri);
		if (attachment.meta != null && attachment.meta.title != null) {
			sb.append(" \"");
			sb.append(escapeQuotation(attachment.meta.title));
			sb.append("\"");
		}
		sb.append('\n');
	}

	private static void generateRef(final StringBuilder sb, final String id,
									final String uri, final String suffix) {
		sb.append('[');
		sb.append(id);
		sb.append(suffix);
		sb.append("]: ");
		sb.append(uri);
		sb.append('\n');
	}
}
