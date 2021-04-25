package com.gratchev.mizoine.mail.impl;

import com.gratchev.mizoine.mail.Part;
import org.jetbrains.annotations.NotNull;

import javax.mail.Header;
import java.io.InputStream;
import java.util.Enumeration;

/**
 * Wrapper for {@link javax.mail.Part}
 */
public class PartImpl implements Part {
	private final javax.mail.Part wrappedPart;

	public PartImpl(javax.mail.Part partToWrap) {
		wrappedPart = partToWrap;
	}

	@Override
	public int getSize() throws Exception {
		return wrappedPart.getSize();
	}

	@Override
	public String getFileName() throws Exception {
		return wrappedPart.getFileName();
	}

	@Override
	public @NotNull InputStream getInputStream() throws Exception {
		return wrappedPart.getInputStream();
	}

	@Override
	public Object getContent() throws Exception {
		return wrappedPart.getContent();
	}

	@Override
	public String getContentType() throws Exception {
		return wrappedPart.getContentType();
	}

	@Override
	public Enumeration<Header> getAllHeaders() throws Exception {
		return wrappedPart.getAllHeaders();
	}
}
