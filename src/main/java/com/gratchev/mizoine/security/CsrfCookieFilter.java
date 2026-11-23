package com.gratchev.mizoine.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * D27: Spring Security creates the CSRF token only when something asks for it. The static pages under
 * {@code /ui/} never do, so the cookie {@code XSRF-TOKEN} would never be written. Asking for it on every request
 * writes it (Spring Security's documented setup for single-page applications).
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
			final FilterChain chain) throws ServletException, IOException {
		if (request.getAttribute(CsrfToken.class.getName()) instanceof final CsrfToken token) {
			token.getToken();
		}
		chain.doFilter(request, response);
	}
}
