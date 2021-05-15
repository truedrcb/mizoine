package com.gratchev.mizoine;

import com.gratchev.mizoine.mail.Folder;
import com.gratchev.mizoine.mail.Message;
import com.gratchev.mizoine.mail.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImapComponent {
	public static final String FOLDER_INBOX = "INBOX";

	private static final Logger LOGGER = LoggerFactory.getLogger(ImapComponent.class);

	@Autowired
	protected ImapConnector connector;

	public <T> T readFolder(final String folderName, final FolderReader<T> reader) {
		try (final Store store = connector.getStore()) {
			try (final Folder folder = store.getReadOnlyFolder(folderName)) {
				return reader.read(folder);
			}
		} catch (final Exception e) {
			LOGGER.error("Mail reading error", e);
			return null;
		}
	}

	public <T> T readInbox(final FolderReader<T> reader) {
		return readFolder(FOLDER_INBOX, reader);
	}

	private Message readMessageUsingFullSearch(final Folder inbox, final String messageId) {
		// Fetch all messages from inbox folder
		try {
			return inbox.getMessages().filter(message -> {
				final String[] messageIds;
				try {
					messageIds = message.getHeader("Message-ID");
				} catch (final Exception e) {
					LOGGER.info("Cannot get header: Message-ID", e);
					return false;
				}
				if (messageIds.length != 1) {
					LOGGER.warn("Unexpected number of Message-ID headers: " + messageIds.length);
					if (messageIds.length < 1) return false;
				}
				final String id = messageIds[0];
				return messageId.equals(id);
			}).findFirst().orElse(null);
		} catch (Exception e) {
			LOGGER.info("Cannot read messages", e);
			return null;
		}
	}

	/**
	 * Same as {@link #readMessage(String, MessageReader)}, but loading all INBOX messages first<br>
	 * The method is required since some message Id's are not correctly recognised by standard inbox
	 * search, but returned correctly when reading complete list.
	 */
	public <T> T readMessageUsingFullSearch(final String messageId, final MessageReader<T> reader) throws Exception {
		return reader.read(readInbox((inbox) -> readMessageUsingFullSearch(inbox, messageId)));
	}

	public <T> T readMessage(final String messageId, final MessageReader<T> reader) throws Exception {
		return reader.read(readInbox(inbox -> inbox.searchById(messageId).findFirst().orElseGet(() -> readMessageUsingFullSearch(inbox, messageId))));
	}

	public interface FolderReader<T> {
		T read(final Folder inbox) throws Exception;
	}

	public interface MessageReader<T> {
		T read(final Message message) throws Exception;
	}
}
