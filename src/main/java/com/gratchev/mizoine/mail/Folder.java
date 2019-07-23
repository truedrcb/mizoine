package com.gratchev.mizoine.mail;

import javax.mail.MessagingException;
import java.util.stream.Stream;

public interface Folder extends AutoCloseable {
	Stream<Message> getMessages() throws Exception;
	Stream<Message> searchById(String messageId) throws Exception;
	Stream<Message> getUnseenMessages() throws Exception;
}
