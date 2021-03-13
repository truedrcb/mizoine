package com.gratchev.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jsoup.Jsoup;

import javax.mail.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImapUtils {
	public static void forParts(final Multipart multipart, final PartVisitor visitor) throws MessagingException, IOException {
		final int count = multipart.getCount();
		for (int i = 0; i < count; i++) {
			forParts(multipart.getBodyPart(i), visitor);
		}
	}

	public static void forParts(final Part part, final PartVisitor visitor) throws MessagingException, IOException {
		final Object content = part.getContent();
		if (content instanceof Multipart) {
			forParts((Multipart) content, visitor);
		} else {
			visitor.visit(part);
		}
	}

	public static MailBlock extractMailBlock(final Part part) throws MessagingException, IOException {
		final HTMLtoMarkdown mailHTMLtoMarkdown = new HTMLtoMarkdown();
		final MailBlock block = new MailBlock();
		block.contentType = part.getContentType();
		final int indexOfSemicolon = block.contentType.indexOf(';');
		if (indexOfSemicolon > 0) {
			block.contentSubType = block.contentType.substring(0, indexOfSemicolon);
		} else {
			block.contentSubType = block.contentType;
		}

		block.size = part.getSize();
		block.fileName = part.getFileName();
		final String mimeType = block.contentSubType.toLowerCase();
		if (mimeType.startsWith("text/")) {
			block.content = part.getContent().toString();
			if (mimeType.contains("html")) {
				block.markdown = mailHTMLtoMarkdown.convert(Jsoup.parse(block.content));
			} else {
				block.markdown = block.content
						// Redundant line feeds (inserted by some formatting programs)
						.replace(" \n", " ")
						// Remove potential HTML injections
						.replace("<", "&lt;");
			}
		}

		return block;
	}

	public interface PartVisitor {
		void visit(Part part) throws MessagingException, IOException;
	}

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public static class MailBlock {
		public String id;
		public String contentType;
		public String contentSubType;
		public String content;
		public String markdown;
		public String html;
		public int size;
		public String fileName;
	}

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public static class MailMessage {
		public final List<Header> headers = new ArrayList<>();
		public final List<MailBlock> blocks = new ArrayList<>();
		public String id;
		public String uri;
		public String subject;
		public Address[] from;
	}
}
