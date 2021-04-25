package com.gratchev.mizoine;

import com.gratchev.mizoine.mail.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.mail.Header;
import java.util.Arrays;
import java.util.Enumeration;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ImapComponent.class, SignedInUserComponentMock.class, ImapConnectorComponent.class})
public class ImapComponentIT {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImapComponentIT.class);
	@Value("${test.imap.suspId:<abcde==@test.io>}")
	private final String suspitiousId = "<!&!AA...cBAAAAAA==@gmx.de>";
	@Value("${test.imap.testId:<abcde==@test.io>}")
	private final String testId = "<WorkingId==@gmx.de>";
	@Autowired
	ImapComponent imap;

	@Test
	@Disabled
	public void testSearchByWorkingMessageID() throws Exception {
		final Message message = imap.readMessage(testId);
		LOGGER.info("Message: " + message.getMessageNumber() + " " + message.getSubject());
		LOGGER.info("Returned: " + message);
	}

	@Test
	@Disabled
	public void testSearchByWrongMessageID() throws Exception {
		final Message message = imap.readMessage(suspitiousId);
		LOGGER.info("Message: " + message.getMessageNumber() + " " + message.getSubject());
		LOGGER.info("Returned: " + message);
	}

	@Test
	@Disabled
	public void testListInbox() {
		imap.readInbox(inbox -> {
					// Fetch all messages from inbox folder
					inbox.getMessages().forEach(message -> {
						LOGGER.info("Message #: " + message.getMessageNumber());
						try {
							final String messageId = getMessageId(message);
							LOGGER.info("- Id: " + messageId);
							LOGGER.info("- From: " + Arrays.toString(message.getFrom()));
							LOGGER.info("- Recipients: " + Arrays.toString(message.getAllRecipients()));
							LOGGER.info("- Subject: " + message.getSubject());
							if (suspitiousId.equals(messageId) || testId.equals(messageId)) {
								LOGGER.info("Found matching Id: " + messageId);
							}
						} catch (Exception e) {
							LOGGER.error("Unexpected", e);
						}
					});
					return "dummy";
				}
		);
	}

	@Test
	@Disabled
	public void testDetailsInbox() {
		imap.readInbox(inbox -> {
					// Fetch all messages from inbox folder
					inbox.getMessages().forEach(message -> {
						LOGGER.info("=================================\n\n\nMessage #: " + message.getMessageNumber());
						try {
							final String messageId = getMessageId(message);
							LOGGER.info("- Id: " + messageId);
							LOGGER.info("- From: " + Arrays.toString(message.getFrom()));
							LOGGER.info("- Recipients: " + Arrays.toString(message.getAllRecipients()));
							LOGGER.info("- Subject: " + message.getSubject());
							message.getParts().forEach(part -> {
								try {
									LOGGER.info("- Part: {} ({}) bytes: {}", part.getFileName(), part.getContentType(),
											part.getSize());
									final Enumeration<Header> headers = part.getAllHeaders();
									while (headers.hasMoreElements()) {
										final Header header = headers.nextElement();
										LOGGER.info("-- Header: {} = {}", header.getName(), header.getValue());
									}
								} catch (Exception e) {
									LOGGER.error("Unexpected", e);
								}
							});
						} catch (Exception e) {
							LOGGER.error("Unexpected", e);
						}
					});
					return "dummy";
				}
		);
	}

	@Test
	@Disabled
	public void testListFolder() {
		imap.readFolder("Mizoine",
				inbox -> {
					// Fetch all messages from inbox folder
					inbox.getMessages().forEach(message ->  {
						try {
							final String messageId = getMessageId(message);
							LOGGER.info("Message: " + message.getMessageNumber() + " " + messageId + " " + message.getSubject());
							if (suspitiousId.equals(messageId)) {
								LOGGER.info("Found matching Id: " + messageId);
							}
						} catch (Exception e) {
							LOGGER.error("Unexpected", e);
						}
					});
					return "dummy";
				});
	}

	private String getMessageId(Message message) throws Exception {
		String[] messageIds = message.getHeader("Message-ID");
		if (messageIds.length != 1) {
			LOGGER.error("Unexpected number of Message-ID headers: " + messageIds.length);
		}
		return messageIds[0];
	}
}
