package com.gratchev.mizoine.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Feeds Spring Security's login results into the {@link LoginAttemptLimiter}.
 * Unknown usernames also arrive as bad credentials.
 */
class LoginAttemptEvents {

	private final LoginAttemptLimiter limiter;

	LoginAttemptEvents(final LoginAttemptLimiter limiter) {
		this.limiter = limiter;
	}

	@EventListener
	void onFailure(final AuthenticationFailureBadCredentialsEvent event) {
		final String address = address(event);
		if (address != null) {
			limiter.failed(address);
		}
	}

	@EventListener
	void onSuccess(final AuthenticationSuccessEvent event) {
		final String address = address(event);
		if (address != null) {
			limiter.succeeded(address);
		}
	}

	private static String address(final AbstractAuthenticationEvent event) {
		return event.getAuthentication().getDetails() instanceof final WebAuthenticationDetails details
				? details.getRemoteAddress()
				: null;
	}
}
