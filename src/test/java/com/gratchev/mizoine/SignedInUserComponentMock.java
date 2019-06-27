package com.gratchev.mizoine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.gratchev.mizoine.WebSecurityConfig.ImapConnection;
import com.gratchev.mizoine.WebSecurityConfig.UserCredentials;

@Component
@Primary
public class SignedInUserComponentMock implements SignedInUser {
	
	@Value("${test.user.name:testUser}")
	private String name = "testUser";
	
	@Value("${test.user.email:test@user.net}")
	private String email = "test@user.net";

	@Value("${test.imap.username:test@user.net}")
	private String imapUsername = "test@user.net";

	@Value("${test.imap.host:imap.googlemail.com}")
	private String imapHost = "imap.googlemail.com";

	@Value("${test.imap.port:993}")
	private int imapPort = 993;

	@Value("${test.imap.password:secret}")
	private String imapPassword = "secret";
	
	
	private final UserCredentials credentials = new UserCredentials();
	private final ImapConnection imap = new ImapConnection();

//	private final 
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getEmail() {
		return email;
	}

	@Override
	public UserCredentials getCredentials() {
		credentials.seteMail(getEmail());
		credentials.setImap(imap);
		imap.setUsername(imapUsername);
		imap.setHost(imapHost);
		imap.setPort(imapPort);
		imap.setPassword(imapPassword);
		return credentials;
	}
}