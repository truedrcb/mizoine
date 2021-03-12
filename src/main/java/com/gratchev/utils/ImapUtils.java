package com.gratchev.utils;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import java.io.IOException;

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

	public interface PartVisitor {
		void visit(Part part) throws MessagingException, IOException;
	}
}
