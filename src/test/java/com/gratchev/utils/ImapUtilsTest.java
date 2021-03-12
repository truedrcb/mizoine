package com.gratchev.utils;

import com.gratchev.utils.ImapUtils.PartVisitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import java.io.IOException;
import java.io.OutputStream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ImapUtilsTest {
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
}
