package com.gratchev.mizoine.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.util.HtmlUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After the login form: back to the page first asked for, with a relative URL ({@link RelativeRedirectStrategy}).
 * <p>
 * For a chat app's Connect, the page first asked for is the OAuth authorization, which redirects on to the app.
 * Browsers apply the login page's CSP {@code form-action} to every redirect after the form, also to the app's own
 * redirects; so a redirect there was blocked on the first Connect (step 35, found live twice). Instead Mizoine's own
 * small page continues to the authorization as a plain navigation, which {@code form-action} does not cover.
 */
final class LoginSuccessHandler implements AuthenticationSuccessHandler {

	/** Spring Authorization Server's authorization endpoint. */
	private static final String AUTHORIZATION = "/oauth2/authorize";

	private final RequestCache requestCache;
	private final SavedRequestAwareAuthenticationSuccessHandler redirect = new SavedRequestAwareAuthenticationSuccessHandler();

	LoginSuccessHandler(final RequestCache requestCache) {
		this.requestCache = requestCache;
		redirect.setRequestCache(requestCache);
		redirect.setRedirectStrategy(new RelativeRedirectStrategy());
	}

	@Override
	public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response,
			final Authentication authentication) throws IOException, ServletException {
		final SavedRequest saved = requestCache.getRequest(request, response);
		final String target = saved == null ? null : RelativeRedirectStrategy.relative(saved.getRedirectUrl());
		if (target == null || !target.startsWith(AUTHORIZATION + "?")) {
			redirect.onAuthenticationSuccess(request, response, authentication);
			return;
		}
		requestCache.removeRequest(request, response);
		continuePage(response, target);
	}

	private static void continuePage(final HttpServletResponse response, final String target) throws IOException {
		final String url = HtmlUtils.htmlEscape(target, StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		response.getWriter().write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
				+ "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>Mizoine – logged in</title>\n"
				+ "<meta http-equiv=\"refresh\" content=\"0; url=" + url + "\">\n"
				+ "<link rel=\"stylesheet\" href=\"/ui/mizoine.css\">\n</head>\n<body>\n"
				+ "<p>Logged in. <a href=\"" + url + "\">Continue</a></p>\n</body>\n</html>\n");
	}
}
