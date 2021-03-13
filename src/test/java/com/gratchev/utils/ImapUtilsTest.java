package com.gratchev.utils;

import com.gratchev.utils.ImapUtils.MailBlock;
import com.gratchev.utils.ImapUtils.PartVisitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import java.io.IOException;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ImapUtilsTest {
	public static final String HTML_CONTENT = "<html><body>abc</body>";
	public static final String MD_CONTENT = "abc";
	PartVisitor visitor;
	BodyPart part1;
	BodyPart part2;
	Multipart multipart;
	Part part;

	@BeforeEach
	void setup() {
		visitor = spy(PartVisitor.class);
		part1 = mock(BodyPart.class);
		part2 = mock(BodyPart.class);
		multipart = spy(new Multipart() {
			{
				try {
					addBodyPart(part1);
					addBodyPart(part2);
				} catch (final MessagingException e) {
					Assertions.fail("Unexpected error");
				}
			}

			@Override
			public void writeTo(OutputStream os) {
				Assertions.fail("Unexpected call");
			}
		});
		part = mock(Part.class);
	}

	@Test
	void forPart() throws IOException, MessagingException {
		// when
		ImapUtils.forParts(part, visitor);

		// then
		verify(visitor).visit(eq(part));
	}

	@Test
	void forMultiPart() throws IOException, MessagingException {
		when(part.getContent()).thenReturn(multipart);

		// when
		ImapUtils.forParts(part, visitor);

		// then
		verify(visitor).visit(eq(part1));
		verify(visitor).visit(eq(part2));
		verify(visitor, never()).visit(eq(part));
	}

	@Test
	void forMultiPartDirect() throws IOException, MessagingException {
		// when
		ImapUtils.forParts(multipart, visitor);

		// then
		verify(visitor).visit(eq(part1));
		verify(visitor).visit(eq(part2));
		verify(multipart).getCount();
		verify(multipart).getBodyPart(eq(0));
		verify(multipart).getBodyPart(eq(1));
	}

	@Test
	void extractMailBlockHtml() throws MessagingException, IOException {
		whenPart(MediaType.TEXT_HTML_VALUE, new StringBuilder(HTML_CONTENT));
		thenMailBlock(MediaType.TEXT_HTML_VALUE, MediaType.TEXT_HTML_VALUE, HTML_CONTENT, MD_CONTENT);
	}

	@Test
	void extractMailBlockHtmlExt() throws MessagingException, IOException {
		whenPart("text/html;additionalParameter=abc", new StringBuffer(HTML_CONTENT));
		thenMailBlock("text/html;additionalParameter=abc", "text/html", HTML_CONTENT, MD_CONTENT);
	}

	@Test
	void extractMailBlockText() throws MessagingException, IOException {
		whenPart(MediaType.TEXT_PLAIN_VALUE, new StringBuffer("hello \nworld! >_< "));
		thenMailBlock(MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_PLAIN_VALUE, "hello \nworld! >_< ", "hello world! " +
				">_&lt; ");
	}

	@Test
	void extractMailBlockTextExt() throws MessagingException, IOException {
		whenPart("text/xml;charset=ABC123", new StringBuffer("<z>666</z>"));
		thenMailBlock("text/xml;charset=ABC123", "text/xml", "<z>666</z>", "&lt;z>666&lt;/z>");
	}

	private void thenMailBlock(final String contentType, final String contentSubType,
							   final String content,
							   final String markdown) throws IOException, MessagingException {
		final MailBlock mailBlock = ImapUtils.extractMailBlock(part);
		assertThat(mailBlock).isNotNull();
		assertThat(mailBlock.size).isEqualTo(12345);
		assertThat(mailBlock.fileName).isEqualTo("test.html");
		assertThat(mailBlock.contentType).isEqualTo(contentType);
		assertThat(mailBlock.contentSubType).isEqualTo(contentSubType);
		assertThat(mailBlock.content).isEqualTo(content);
		assertThat(mailBlock.markdown).isEqualTo(markdown);
	}

	private void whenPart(final String contentType, final Object content) throws MessagingException, IOException {
		when(part.getSize()).thenReturn(12345);
		when(part.getFileName()).thenReturn("test.html");
		when(part.getContentType()).thenReturn(contentType);
		when(part.getContent()).thenReturn(content);
	}
}
