package com.gratchev.mizoine;

import com.gratchev.mizoine.WebSecurityConfig.ImapConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.MessageIDTerm;
import java.util.Properties;

@Component
public class ImapComponent {
	public static final String FOLDER_INBOX = "INBOX";

	private static final Logger LOGGER = LoggerFactory.getLogger(ImapComponent.class);

	@Autowired
	protected SignedInUser currentUser;

	public <T> T readFolder(final String folderName, final MailReader<T> reader) {
		final ImapConnection connection = currentUser.getCredentials().getImap();
		if (connection == null || connection.getHost() == null || connection.getUsername() == null
				|| connection.getPassword() == null) {
			LOGGER.warn("E-Mail IMAP connection is undefined for the user.");
			return null;
		}

		final Session session = Session.getDefaultInstance(new Properties());
		try (final Store store = session.getStore("imaps")) {
			store.connect(connection.getHost(), connection.getPort(), connection.getUsername(),
					connection.getPassword());
			try (final Folder folder = store.getFolder(folderName)) {
				folder.open(Folder.READ_ONLY);
				return reader.read(folder);
			}
		} catch (final Exception e) {
			LOGGER.error("Mail reading error", e);
			return null;
		}
	}

	public <T> T readInbox(final MailReader<T> reader) {
		return readFolder(FOLDER_INBOX, reader);
	}

	private Message readMessageUsingFullSearch(final Folder inbox, final String messageId) throws Exception {
		// Fetch all messages from inbox folder
		for (final Message message : inbox.getMessages()) {
			String[] messageIds = message.getHeader("Message-ID");
			if (messageIds.length != 1) {
				LOGGER.warn("Unexpected number of Message-ID headers: " + messageIds.length);
				if (messageIds.length < 1) continue;
			}
			final String id = messageIds[0];
			if (messageId.equals(id)) {
				return message;
			}
		}
		return null;
	}

	/**
	 * Same as {@link #readMessage(String)}, but loading all INBOX messages first<br>
	 * The method is required since some message Id's are not correctly recognised by standard inbox
	 * search, but returned correctly when reading complete list.
	 */
	public Message readMessageUsingFullSearch(final String messageId) {
		return readInbox((inbox) -> readMessageUsingFullSearch(inbox, messageId));
	}

	public Message readMessage(final String messageId) {
		return readInbox((inbox) -> {
			final Message[] messages = inbox.search(new MessageIDTerm(messageId));

			if (messages.length != 1) {
				LOGGER.error("Messages with Id '" + messageId + "' found: " + messages.length);
				if (messages.length < 1) {
					return readMessageUsingFullSearch(inbox, messageId);
				}
			}
			return messages[0];
		});

	}

	public interface MailReader<T> {
		T read(final Folder inbox) throws Exception;
	}

}
