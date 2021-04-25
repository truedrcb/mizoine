package com.gratchev.mizoine.mail;

import javax.mail.MessagingException;

public interface Store extends AutoCloseable {
	Folder getReadOnlyFolder(String name) throws MessagingException;
}
