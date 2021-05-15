package com.gratchev.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gratchev.mizoine.mail.Part;
import org.jsoup.Jsoup;

import javax.mail.Address;
import javax.mail.Header;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ImapUtils {

	public static MailPartDto extractMailBlock(final Part part) throws Exception {
		final HTMLtoMarkdown mailHTMLtoMarkdown = new HTMLtoMarkdown();
		final MailPartDto block = new MailPartDto();
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

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public static class MailPartDto {
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
		public final List<MailPartDto> blocks = new ArrayList<>();
		public String id;
		public String uri;
		public String subject;
		public Address[] from;
	}
}
