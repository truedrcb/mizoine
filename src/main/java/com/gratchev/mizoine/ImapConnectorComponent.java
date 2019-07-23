package com.gratchev.mizoine;

import com.gratchev.mizoine.mail.Folder;
import com.gratchev.mizoine.mail.Store;
import com.gratchev.mizoine.mail.impl.FolderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import java.util.Properties;

@Component
public class ImapConnectorComponent implements ImapConnector {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImapConnectorComponent.class);

	@Autowired
	protected SignedInUser currentUser;

	@Override
	public Store getStore() throws MessagingException {
		final Application.ImapConnection connection = currentUser.getCredentials().getImap();
		if (connection == null || connection.getHost() == null || connection.getUsername() == null
				|| connection.getPassword() == null) {
			LOGGER.warn("E-Mail IMAP connection is undefined for the user.");
			throw new AuthenticationFailedException("E-Mail IMAP connection is undefined for the user.");
		}
		final Session session = Session.getDefaultInstance(new Properties());
		final javax.mail.Store store = session.getStore("imaps");
			store.connect(connection.getHost(), connection.getPort(), connection.getUsername(),
					connection.getPassword());
		return new Store() {
			@Override
			public Folder getReadOnlyFolder(final String name) throws MessagingException {
				final javax.mail.Folder folder = store.getFolder(name);
				folder.open(javax.mail.Folder.READ_ONLY);
				return new FolderImpl(folder);
			}

			@Override
			public void close() throws Exception {
				store.close();
			}
		};
	}
}
