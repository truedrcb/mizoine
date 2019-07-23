package com.gratchev.mizoine;

import com.gratchev.mizoine.mail.Store;

import javax.mail.MessagingException;

public interface ImapConnector {
	Store getStore() throws MessagingException;
}
