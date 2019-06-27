package com.gratchev.mizoine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.gratchev.mizoine.WebSecurityConfig.BasicUsersConfiguration;
import com.gratchev.mizoine.WebSecurityConfig.UserCredentials;

/**
 * See http://www.baeldung.com/get-user-in-spring-security
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SignedInUserComponent implements SignedInUser {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SignedInUserComponent.class);

	/* (non-Javadoc)
	 * @see com.gratchev.mizoine.SignedInUser#getName()
	 */
	@Override
	public String getName() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	/* (non-Javadoc)
	 * @see com.gratchev.mizoine.SignedInUser#getEmail()
	 */
	@Override
	public String getEmail() {
		return getName() + "@mizoine.test";
	}

	@Autowired
	private BasicUsersConfiguration usersConfiguration;

	/* (non-Javadoc)
	 * @see com.gratchev.mizoine.SignedInUser#getCredentials()
	 */
	@Override
	public UserCredentials getCredentials() {
		return usersConfiguration.getUsers().get(getName());
	}
}
