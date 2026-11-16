package com.gratchev.mizoine.security;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects login attempts (HTTP Basic credentials or a login form post) from a blocked address
 * with 429 and {@code Retry-After}, before any password is checked. Other requests pass.
 * Runs inside the Spring Security filter chain; not registered as a servlet filter of its own.
 */
class LoginLimitFilter extends OncePerRequestFilter {

	private static final RequestMatcher LOGIN_FORM_POST =
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login");
	private static final String BASIC_PREFIX = "basic ";

	private final LoginAttemptLimiter limiter;

	LoginLimitFilter(final LoginAttemptLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
		if (isLoginAttempt(request)) {
			final Duration retryAfter = limiter.retryAfter(request.getRemoteAddr());
			if (!retryAfter.isZero()) {
				response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
				response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(roundUpToSeconds(retryAfter)));
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private static boolean isLoginAttempt(final HttpServletRequest request) {
		final String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization != null && authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
			return true;
		}
		return LOGIN_FORM_POST.matches(request);
	}

	private static long roundUpToSeconds(final Duration duration) {
		return duration.toNanosPart() == 0 ? duration.toSeconds() : duration.toSeconds() + 1;
	}
}
