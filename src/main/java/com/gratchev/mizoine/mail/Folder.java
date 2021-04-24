package com.gratchev.mizoine.mail;

import javax.mail.MessagingException;
import java.util.stream.Stream;

public interface Folder extends AutoCloseable {
	Stream<Message> getMessages() throws MessagingException;
}
