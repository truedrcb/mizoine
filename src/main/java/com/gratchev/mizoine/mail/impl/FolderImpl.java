package com.gratchev.mizoine.mail.impl;

import com.gratchev.mizoine.mail.Folder;
import com.gratchev.mizoine.mail.Message;

import javax.mail.MessagingException;
import java.util.stream.Stream;

/**
 * Mockable wrapper for {@link javax.mail.Folder}
 */
public class FolderImpl implements Folder {
	private final javax.mail.Folder wrappedFolder;

	public FolderImpl() {
		wrappedFolder = null;
	}

	public FolderImpl(final javax.mail.Folder folderToWrap) {
		wrappedFolder = folderToWrap;
	}

	@Override
	public Stream<Message> getMessages() throws MessagingException {
		return Stream.of(wrappedFolder.getMessages()).map(m -> new MessageImpl(m));
	}

	@Override
	public void close() throws Exception {
		wrappedFolder.close();
	}
}
