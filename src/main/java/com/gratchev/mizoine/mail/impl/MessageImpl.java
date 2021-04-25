package com.gratchev.mizoine.mail.impl;

import com.gratchev.mizoine.mail.Message;
import com.gratchev.mizoine.mail.Part;

import javax.mail.Address;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.stream.Stream;

/**
 * Wrapper for {@link javax.mail.Message}
 */
public class MessageImpl implements Message {
	private final javax.mail.Message wrappedMessage;

	public MessageImpl(javax.mail.Message messageToWrap) {
		wrappedMessage = messageToWrap;
	}

	@Override
	public String[] getHeader(final String name) throws Exception {
		return wrappedMessage.getHeader(name);
	}

	@Override
	public Date getSentDate() throws Exception {
		return wrappedMessage.getSentDate();
	}

	@Override
	public Date getReceivedDate() throws Exception {
		return wrappedMessage.getReceivedDate();
	}

	@Override
	public String getSubject() throws Exception {
		return wrappedMessage.getSubject();
	}

	@Override
	public Address[] getFrom() throws Exception {
		return wrappedMessage.getFrom();
	}

	@Override
	public String getContentType() throws Exception {
		return wrappedMessage.getContentType();
	}

	@Override
	public Enumeration<Header> getAllHeaders() throws Exception {
		return wrappedMessage.getAllHeaders();
	}

	@Override
	public Stream<Part> getParts() throws Exception {
		Stream.Builder<Part> sb = Stream.builder();
		forParts(wrappedMessage, part -> sb.add(new PartImpl(part)));
		return sb.build();
	}

	@Override
	public int getMessageNumber() {
		return wrappedMessage.getMessageNumber();
	}

	@Override
	public Address[] getAllRecipients() throws Exception {
		return wrappedMessage.getAllRecipients();
	}

	static void forParts(final Multipart multipart, final PartVisitor visitor) throws MessagingException, IOException {
		final int count = multipart.getCount();
		for (int i = 0; i < count; i++) {
			forParts(multipart.getBodyPart(i), visitor);
		}
	}

	static void forParts(final javax.mail.Part part, final PartVisitor visitor) throws MessagingException, IOException {
		final Object content = part.getContent();
		if (content instanceof Multipart) {
			forParts((Multipart) content, visitor);
		} else {
			visitor.visit(part);
		}
	}

	interface PartVisitor {
		void visit(javax.mail.Part part) throws MessagingException, IOException;
	}
}
