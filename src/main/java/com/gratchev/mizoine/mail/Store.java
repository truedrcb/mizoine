package com.gratchev.mizoine.mail;

import javax.mail.MessagingException;

public interface Store extends AutoCloseable {
	Folder getFolder(String name) throws MessagingException;
}
