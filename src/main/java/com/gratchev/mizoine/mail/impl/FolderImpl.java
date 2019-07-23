package com.gratchev.mizoine.mail.impl;

import com.gratchev.mizoine.mail.Folder;
import com.gratchev.mizoine.mail.Message;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;
import javax.mail.search.MessageIDTerm;
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
		return toStream(wrappedFolder.getMessages());
	}

	@Override
	public Stream<Message> searchById(final String messageId) throws Exception {
		return toStream(wrappedFolder.search(new MessageIDTerm(messageId)));
	}

	@Override
	public Stream<Message> getUnseenMessages() throws Exception {
		return toStream(wrappedFolder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false)));
	}

	@Override
	public void close() throws Exception {
		wrappedFolder.close();
	}

	private Stream<Message> toStream(final javax.mail.Message[] messages) {
		return Stream.of(messages).map(m -> new MessageImpl(m));
	}
}
