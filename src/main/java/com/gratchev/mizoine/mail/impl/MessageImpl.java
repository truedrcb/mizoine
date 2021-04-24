package com.gratchev.mizoine.mail.impl;

import com.gratchev.mizoine.mail.Message;

/**
 * Wrapper for {@link javax.mail.Message}
 */
public class MessageImpl implements Message {
	private final javax.mail.Message wrappedMessage;

	public MessageImpl(javax.mail.Message messageToWrap) {
		wrappedMessage = messageToWrap;
	}

	@Override
	public String getHeader(final String name) {
		return null;
	}
}
