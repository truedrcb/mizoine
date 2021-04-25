package com.gratchev.utils;

import com.gratchev.mizoine.mail.Part;
import com.gratchev.utils.ImapUtils.MailBlock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ImapUtilsTest {
	public static final String HTML_CONTENT = "<html><body>abc</body>";
	public static final String MD_CONTENT = "abc";
	Part part;

	@Test
	void extractMailBlockHtml() throws Exception {
		whenPart(MediaType.TEXT_HTML_VALUE, new StringBuilder(HTML_CONTENT));
		thenMailBlock(MediaType.TEXT_HTML_VALUE, MediaType.TEXT_HTML_VALUE, HTML_CONTENT, MD_CONTENT);
	}

	@Test
	void extractMailBlockHtmlExt() throws Exception {
		whenPart("text/html;additionalParameter=abc", new StringBuffer(HTML_CONTENT));
		thenMailBlock("text/html;additionalParameter=abc", "text/html", HTML_CONTENT, MD_CONTENT);
	}

	@Test
	void extractMailBlockText() throws Exception {
		whenPart(MediaType.TEXT_PLAIN_VALUE, new StringBuffer("hello \nworld! >_< "));
		thenMailBlock(MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_PLAIN_VALUE, "hello \nworld! >_< ", "hello world! " +
				">_&lt; ");
	}

	@Test
	void extractMailBlockTextExt() throws Exception {
		whenPart("text/xml;charset=ABC123", new StringBuffer("<z>666</z>"));
		thenMailBlock("text/xml;charset=ABC123", "text/xml", "<z>666</z>", "&lt;z>666&lt;/z>");
	}

	@Test
	void extractMailBlockNonText() throws Exception {
		final Object content = new Object() {
			@Override
			public String toString() {
				Assertions.fail("toString must not be called");
				return null;
			}
		};
		whenPart(MediaType.IMAGE_PNG_VALUE, content);
		thenMailBlock(MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_PNG_VALUE, null, null);
	}

	private void thenMailBlock(final String contentType, final String contentSubType,
							   final String content,
							   final String markdown) throws Exception {
		final MailBlock mailBlock = ImapUtils.extractMailBlock(part);
		assertThat(mailBlock).isNotNull();
		assertThat(mailBlock.size).isEqualTo(12345);
		assertThat(mailBlock.fileName).isEqualTo("test.html");
		assertThat(mailBlock.contentType).isEqualTo(contentType);
		assertThat(mailBlock.contentSubType).isEqualTo(contentSubType);
		assertThat(mailBlock.content).isEqualTo(content);
		assertThat(mailBlock.markdown).isEqualTo(markdown);
	}

	private void whenPart(final String contentType, final Object content) throws Exception {
		when(part.getSize()).thenReturn(12345);
		when(part.getFileName()).thenReturn("test.html");
		when(part.getContentType()).thenReturn(contentType);
		when(part.getContent()).thenReturn(content);
	}
}
