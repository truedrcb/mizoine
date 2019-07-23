package com.gratchev.mizoine;

import com.gratchev.mizoine.Application.UserCredentials;

public interface SignedInUser {

	String getName();

	String getEmail();

	UserCredentials getCredentials();

}