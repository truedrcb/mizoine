package com.gratchev.mizoine;

import java.io.IOException;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.MessageIDTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gratchev.mizoine.WebSecurityConfig.ImapConnection;

@Component
public class ImapComponent {
	public static final String FOLDER_INBOX = "INBOX";

	private static final Logger LOGGER = LoggerFactory.getLogger(ImapComponent.class);

	@Autowired
	protected SignedInUser currentUser;
	
	
	public interface MailReader {
		String read(final Folder inbox) throws Exception;
	}
	
	public String readFolder(final String folderName, final MailReader reader) {
		final ImapConnection connection = currentUser.getCredentials().getImap();
		if (connection == null || connection.getHost() == null || connection.getUsername() == null 
				|| connection.getPassword() == null) {
			LOGGER.warn("E-Mail IMAP connection is undefined for the user.");
			return null;
		}

		final Session session = Session.getDefaultInstance(new Properties( ));
		try (final Store store = session.getStore("imaps")) {
			store.connect(connection.getHost(), connection.getPort(), connection.getUsername(), connection.getPassword());
			final Folder folder = store.getFolder(folderName);
			try {
				folder.open(Folder.READ_ONLY);
				return reader.read(folder);
			} finally {
				folder.close();
			}
		} catch (final Exception e) {
			LOGGER.error("Mail reading error", e);
			return null;
		}
	}
	
	public String readInbox(final MailReader reader) {
		return readFolder(FOLDER_INBOX, reader);
	}

	public interface MessageReader {
		String read(final Message message) throws Exception;
	}

	private String readMessageUsingFullSearch(final Folder inbox, final String messageId, final MessageReader reader)
			throws MessagingException, Exception {
		// Fetch all messages from inbox folder
		for (final Message message : inbox.getMessages()) {
			String[] messageIds = message.getHeader("Message-ID");
			if (messageIds.length != 1) {
				LOGGER.warn("Unexpected number of Message-ID headers: " + messageIds.length);
				if (messageIds.length < 1) continue;
			}
			final String id = messageIds[0];
			if (messageId.equals(id)) {
				return reader.read(message);
			}
		}
		return null;
	}
	
	/**
	 * Same as {@link #readMessage(String, MessageReader)}, but loading all INBOX messages first<br>
	 * The method is required since some message Id's are not correctly recognised by standard inbox
	 * search, but returned correctly when reading complete list.
	 */
	public String readMessageUsingFullSearch(final String messageId, final MessageReader reader) {
		return readInbox((inbox) -> {
			return readMessageUsingFullSearch(inbox, messageId, reader);
		});
	}

	
	public String readMessage(final String messageId, final MessageReader reader) {
		return readInbox((inbox) -> {
			final Message[] messages = inbox.search(new MessageIDTerm(messageId));
			
			if (messages.length != 1) {
				LOGGER.error("Messages with Id '" + messageId + "' found: " + messages.length);
				if (messages.length < 1) {
					return readMessageUsingFullSearch(inbox, messageId, reader);
				}
			}

			final Message message = messages[0];
			return reader.read(message);
		});
		
	}

	public Part findPart(final Multipart multipart, final String mimeType) throws MessagingException, IOException {
		final int count = multipart.getCount();
		for (int i = 0; i < count; i++) {
			final Part part = findPart(multipart.getBodyPart(i), mimeType);
			if (part != null) {
				return part;
			}
		}
		return null;
	}
	
	public Part findPart(final Part part, final String mimeType) throws MessagingException, IOException {
		if (part.isMimeType(mimeType)) {
			return part;
		}
		final Object content = part.getContent();
		if (content instanceof Multipart) {
			return findPart((Multipart) content, mimeType);
		}
		return null;
	}

	public interface PartVisitor {
		void visit(Part part) throws MessagingException, IOException;
	}

	public void forParts(final Multipart multipart, final PartVisitor visitor) throws MessagingException, IOException {
		final int count = multipart.getCount();
		for (int i = 0; i < count; i++) {
			forParts(multipart.getBodyPart(i), visitor);
		}
	}

	public void forParts(final Part part, final PartVisitor visitor) throws MessagingException, IOException {
		final Object content = part.getContent();
		if (content instanceof Multipart) {
			forParts((Multipart) content, visitor);
		} else {
			visitor.visit(part);
		}
	}

}
