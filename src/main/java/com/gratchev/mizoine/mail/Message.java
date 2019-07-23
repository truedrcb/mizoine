package com.gratchev.mizoine.mail;

import javax.mail.Address;
import javax.mail.Header;
import java.util.Date;
import java.util.Enumeration;
import java.util.stream.Stream;

public interface Message {
	String[] getHeader(String name) throws Exception;

	Date getSentDate() throws Exception;

	Date getReceivedDate() throws Exception;

	String getSubject() throws Exception;

	Address[] getFrom() throws Exception;

	Enumeration<Header> getAllHeaders() throws Exception;

	Stream<Part> getParts() throws Exception;

	int getMessageNumber();

	Address[] getAllRecipients() throws Exception;
}
