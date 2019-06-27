package com.gratchev.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaMailTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(JavaMailTest.class);

	/**
	 * https://stackoverflow.com/questions/11240368/how-to-read-text-inside-body-of-mail-using-javax-mail
	 */
	private String getTextFromMessage(final Message message) throws MessagingException, IOException {
		if (message.isMimeType("text/plain")) {
			return message.getContent().toString();
		} else if (message.isMimeType("multipart/*")) {
			final MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
			return getTextFromMimeMultipart(mimeMultipart);
		}
		throw new IOException("Unknown content type: " + message.getContentType());
	}

	private String getTextFromMimeMultipart(
			final MimeMultipart mimeMultipart)  throws MessagingException, IOException{
		String result = "";
		int count = mimeMultipart.getCount();
		for (int i = 0; i < count; i++) {
			BodyPart bodyPart = mimeMultipart.getBodyPart(i);
			if (bodyPart.isMimeType("text/*")) {
				result += bodyPart.getContent();
				break; // without break same text appears twice in my tests
//			} else if (bodyPart.isMimeType("text/html")) {
//				String html = (String) bodyPart.getContent();
//				result = result + "\n" + org.jsoup.Jsoup.parse(html).text();
			} else if (bodyPart.getContent() instanceof MimeMultipart){
				result += getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
			}
		}
		return result;
	}


	/**
	 * https://stackoverflow.com/questions/28689099/javamail-reading-recent-unread-mails-using-imap
	 * @throws Exception 
	 */
	@Test
	public void readImap() throws Exception {
		final String userName = System.getProperty("email.user"); // user@gmail.com
		final String password = System.getProperty("email.password"); // password
		final String host =  System.getProperty("email.host"); // imap.googlemail.com;

		if (userName == null || password == null || host == null) {
			LOGGER.info("Set system properties email.host, email.user and email.password to activate testing");
			return;
		}

		final Session session = Session.getDefaultInstance(new Properties( ));
		final Store store = session.getStore("imaps");
		store.connect(host, 993, userName, password);
		final Folder inbox = store.getFolder( "INBOX" );
		inbox.open( Folder.READ_ONLY );

		// Fetch unseen messages from inbox folder
		final Message[] messages = inbox.search(
				new FlagTerm(new Flags(Flags.Flag.SEEN), false));

		// Sort messages from recent to oldest
		Arrays.sort( messages, ( m1, m2 ) -> {
			try {
				return m2.getSentDate().compareTo( m1.getSentDate() );
			} catch ( final MessagingException e ) {
				LOGGER.warn("Date reading error", e);
				return 0;
			}
		} );

		for ( final Message message : messages ) {
			LOGGER.info( 
					"sendDate: " + message.getSentDate()
					+ " subject:" + message.getSubject() );
			LOGGER.info("contentType: " + message.getContentType());
			final Object content = message.getContent();
			LOGGER.info("content: " + content);
			final Enumeration<Header> headers = message.getAllHeaders();
			while(headers.hasMoreElements()) {
				final Header header = headers.nextElement();
				LOGGER.info("Header: " + header.getName() + " = " + header.getValue());
			}
			
			// https://stackoverflow.com/questions/1748183/download-attachments-using-java-mail
			if (content instanceof Multipart) {
				dumpMultipart((Multipart) content, "");
			}
			
			LOGGER.info(getTextFromMessage(message));
		}
	}

	public void dumpMultipart(final Multipart multipart, final String prefix) throws MessagingException, IOException {
		LOGGER.info("Multipart count: " + multipart.getCount());
		for (int i = 0; i < multipart.getCount(); i++) {
			final BodyPart bodyPart = multipart.getBodyPart(i);
			LOGGER.info("Body part (" + prefix + i + "): " + bodyPart.getContentType() + " - " + bodyPart);
			final Object content = bodyPart.getContent();
			if (bodyPart.isMimeType("text/*")) {
				LOGGER.info(content.toString());
			}
			if (content instanceof Multipart) {
				dumpMultipart((Multipart) content, "" + i + ".");
			}
		}
	}		
}
