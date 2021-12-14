package com.gratchev.mizoine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.gratchev.mizoine.Application.BasicUsersConfiguration;
import com.gratchev.mizoine.Application.UserCredentials;

/**
 * See http://www.baeldung.com/get-user-in-spring-security
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SignedInUserComponent implements SignedInUser {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SignedInUserComponent.class);

	@Override
	public String getName() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}
	@Override
	public String getEmail() {
		return getName() + "@mizoine.test";
	}

	@Autowired
	private BasicUsersConfiguration usersConfiguration;

	@Override
	public UserCredentials getCredentials() {
		return usersConfiguration.getUsers().get(getName());
	}
}
