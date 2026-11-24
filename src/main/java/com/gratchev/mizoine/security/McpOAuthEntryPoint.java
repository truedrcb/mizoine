package com.gratchev.mizoine.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.mcp.McpPaths;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Not logged in at {@code /mcp}, with OAuth on (step 34): 401 with {@code WWW-Authenticate: Bearer
 * resource_metadata="…"}, which tells a chat app where to find the OAuth server (RFC 9728, MCP authorization).
 */
final class McpOAuthEntryPoint implements AuthenticationEntryPoint {

	private final String challenge;

	McpOAuthEntryPoint(final MizoineProperties properties) {
		this.challenge = "Bearer resource_metadata=\"" + OAuthConfig.publicUrl(properties)
				+ "/.well-known/oauth-protected-resource" + McpPaths.ENDPOINT + "\"";
	}

	@Override
	public void commence(final HttpServletRequest request, final HttpServletResponse response,
			final AuthenticationException exception) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
		response.sendError(HttpStatus.UNAUTHORIZED.value());
	}
}
