package com.gratchev.mizoine.mail.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Enumeration;
import java.util.Properties;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.ImapComponent;

public class EmlReadTest {
	private static final Logger log = LoggerFactory.getLogger(EmlReadTest.class);
	public static final Instant EML_SENT_DATE = Instant.parse("2021-12-07T19:49:10.000Z");
	public static final String EML_FILENAME = "Sicherheit Info Scan.eml";

	@Test
	void testReadEml() throws MessagingException {
		final MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()),
				EmlReadTest.class.getResourceAsStream(EML_FILENAME));
		assertThat(message.getFrom()).hasSize(1).contains(InternetAddress.parse("Artem Gratchev <artem@gratchev.com>"));
		final Enumeration<Header> allHeaders = message.getAllHeaders();
		if (allHeaders != null) {
			log.info("Headers");
			while (allHeaders.hasMoreElements()) {
				final Header header = allHeaders.nextElement();
				log.info("{}: {}", header.getName(), header.getValue());
			}
		}
		assertThat(message.getHeader(ImapComponent.MESSAGE_ID)).hasSize(1)
				.contains("<CAHGB224yNTRMciwxmgYDwgrs5hiLa5K1wC-W=q9C2H+zjxqk9A@mail.gmail.com>");
		assertThat(message.getSentDate()).isEqualTo(EML_SENT_DATE);
		assertThat(message.getReceivedDate()).isNull();
	}
}
