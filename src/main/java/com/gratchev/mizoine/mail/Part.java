package com.gratchev.mizoine.mail;

import org.jetbrains.annotations.NotNull;

import javax.mail.Header;
import java.io.InputStream;
import java.util.Enumeration;

public interface Part {
	int getSize() throws Exception;
	String getFileName() throws Exception;
	@NotNull InputStream getInputStream() throws Exception;

	Object getContent() throws Exception;

	String getContentType() throws Exception;

	Enumeration<Header> getAllHeaders() throws Exception;
}
