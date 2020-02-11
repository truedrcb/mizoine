package com.gratchev.mizoine;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.mail.Message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ImapComponent.class, SignedInUserComponentMock.class})
public class ImapComponentIT {

	@Value("${test.imap.suspId:<abcde==@test.io>}")
	private String suspitiousId = "<!&!AA...cBAAAAAA==@gmx.de>";

	@Value("${test.imap.testId:<abcde==@test.io>}")
	private String testId = "<WorkingId==@gmx.de>";

	private static final Logger LOGGER = LoggerFactory.getLogger(ImapComponentIT.class);
	
	@Autowired
	ImapComponent imap;

	@Test
	public void testSearchByWorkingMessageID() {
		final String result = imap.readMessage(
				testId, message -> {
					LOGGER.info("Message: " + message.getMessageNumber() + " " + message.getSubject());
					return "Found: " + message;
				});
		
		LOGGER.info("Returned: " + result);
		assertNotNull(result);
	}

	@Test
	public void testSearchByWrongMessageID() {
		final String result = imap.readMessage(
				suspitiousId, message -> {
					LOGGER.info("Message: " + message.getMessageNumber() + " " + message.getSubject());
					return "Found: " + message;
				});
		
		LOGGER.info("Returned: " + result);
		assertNotNull(result);
	}

	@Test
	public void testListInbox() {
		imap.readInbox(
			inbox -> {
				// Fetch all messages from inbox folder
				for (final Message message : inbox.getMessages()) {
					String[] messageIds = message.getHeader("Message-ID");
					if (messageIds.length != 1) {
						LOGGER.error("Unexpected number of Message-ID headers: " + messageIds.length);
					}
					final String messageId = messageIds[0];
					LOGGER.info("Message: " + message.getMessageNumber() + " " + messageId + " " + message.getSubject());
					if (suspitiousId.equals(messageId) || testId.equals(messageId)) {
						LOGGER.info("Found matching Id: " + messageId);
					}
				}
				return null;
			});
	}
	
	@Test
	public void testListFolder() {
		imap.readFolder( "Mizoine", 
			inbox -> {
				// Fetch all messages from inbox folder
				for (final Message message : inbox.getMessages()) {
					String[] messageIds = message.getHeader("Message-ID");
					if (messageIds.length != 1) {
						LOGGER.error("Unexpected number of Message-ID headers: " + messageIds.length);
					}
					final String messageId = messageIds[0];
					LOGGER.info("Message: " + message.getMessageNumber() + " " + messageId + " " + message.getSubject());
					if (suspitiousId.equals(messageId)) {
						LOGGER.info("Found matching Id: " + messageId);
					}
				}
				return null;
			});
	}
}
