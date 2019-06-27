package com.gratchev.mizoine;

import com.gratchev.mizoine.WebSecurityConfig.UserCredentials;

public interface SignedInUser {

	String getName();

	String getEmail();

	UserCredentials getCredentials();

}