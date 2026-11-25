package com.gratchev.mizoine.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.WebConfig;
import com.gratchev.mizoine.mcp.McpPaths;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Everything requires login, except the login page. Browsers get a login form,
 * other clients HTTP Basic. Users come from {@link com.gratchev.mizoine.users.UsersConfig}.
 * Spring Security defaults stay on (CSRF, nosniff, X-Frame-Options DENY, no-cache); CSP and referrer policy are added.
 * Failed logins are limited per client address ({@link LoginAttemptLimiter}, {@code mizoine.login-limit.*}).
 */
@Configuration
public class SecurityConfig {

	/** Only own resources, no plugins, no framing, forms post only to this server. */
	public static final String CONTENT_SECURITY_POLICY = contentSecurityPolicy(List.of());
	/** For repository files (step 28, D10): the same, and never scripts, even in an uploaded HTML or SVG file. */
	public static final String FILE_CONTENT_SECURITY_POLICY = CONTENT_SECURITY_POLICY + "; sandbox";

	@Bean
	SecurityFilterChain securityFilterChain(final HttpSecurity http, final LoginAttemptLimiter limiter,
			final MizoineProperties properties, final ObjectProvider<JwtDecoder> oauthTokens) {
		// step 34: with OAuth, /mcp also takes Mizoine's own Bearer tokens, and a 401 there leads chat apps to OAuth.
		// Decided by the configuration, not by whether some JwtDecoder bean exists.
		if (properties.oauthEnabled()) {
			final JwtDecoder tokens = oauthTokens.getObject();
			http
				.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.decoder(tokens))
						.authenticationEntryPoint(new McpOAuthEntryPoint(properties))
						// RFC 9728 at /.well-known/oauth-protected-resource[/mcp], answered by Spring Security without
						// login; with the configured address, as behind a proxy the request's host may be wrong
						.protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder -> builder
								.resource(OAuthConfig.resource(properties))
								.authorizationServer(OAuthConfig.publicUrl(properties))
								.resourceName("Mizoine")
								// tokens are not bound to client certificates
								.claims(claims -> claims.remove("tls_client_certificate_bound_access_tokens")))))
				.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
						new McpOAuthEntryPoint(properties), request -> McpPaths.ENDPOINT.equals(request.getServletPath())));
		}
		return http
				.addFilterBefore(new LoginLimitFilter(limiter), UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.authorizeHttpRequests(requests -> requests
						// rendering an error (e.g. 400 for a rejected URL) needs no login: otherwise the error turns
						// into a login redirect. Error pages show no internals (step 8). A direct GET /error still needs login.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						// browsers ask for the icon on every page, also on the login page
						.requestMatchers(HttpMethod.GET, WebConfig.FAVICON_PATH).permitAll()
						// public files of the user interface (logos, later scripts and styles)
						.requestMatchers(HttpMethod.GET, WebConfig.UI_PATTERN).permitAll()
						.anyRequest().authenticated())
				.requestCache(cache -> cache.requestCache(requestCache()))
				// D27: the token also as cookie XSRF-TOKEN, readable by the static pages under /ui/, which send it back
				// in the X-XSRF-TOKEN header (Spring Security's setup for single-page applications)
				.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfCookie())
						.ignoringRequestMatchers(SecurityConfig::isApiClientWrite))
				.formLogin(form -> form.permitAll().successHandler(loginSuccessHandler()))
				.httpBasic(withDefaults())
				.logout(logout -> logout.permitAll())
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy(properties.oauth().clients().stream()
								.flatMap(client -> client.redirectUris().stream()).toList())))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
				.build();
	}

	/**
	 * The policy of Mizoine's pages. Browsers apply {@code form-action} also to the redirects after a form is sent:
	 * with OAuth, the login form leads on through the authorization endpoint to a chat app's callback, so the
	 * callbacks' origins are allowed too (step 35: without them only a second Connect, already logged in, worked).
	 *
	 * @param callbacks the configured OAuth redirect URIs
	 */
	static String contentSecurityPolicy(final List<String> callbacks) {
		final String origins = callbacks.stream()
				.map(URI::create)
				.map(uri -> " " + uri.getScheme() + "://" + uri.getRawAuthority())
				.distinct()
				.sorted()
				.collect(Collectors.joining());
		return "default-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'" + origins
				+ "; frame-ancestors 'none'";
	}

	/**
	 * D11: PUT or DELETE with an {@code Authorization} header (agents, curl) needs no CSRF token: such clients have
	 * none, and cross-site forms cannot send these methods (no CORS either). POST is not exempt: browsers also send
	 * a cached Basic login on cross-site form posts. The browser UI, logged in by session cookie, sends the token.
	 */
	static boolean isApiClientWrite(final HttpServletRequest request) {
		final String method = request.getMethod();
		final boolean fileWrite = HttpMethod.PUT.matches(method) || HttpMethod.DELETE.matches(method);
		return fileWrite && request.getHeader(HttpHeaders.AUTHORIZATION) != null || isJsonPost(request) || isMove(request);
	}

	/**
	 * A move (step 41) is a POST with a {@code Destination} header, which a cross-site form cannot send (custom
	 * headers need CORS, which Mizoine does not allow). A POST without it stays CSRF-protected.
	 */
	private static boolean isMove(final HttpServletRequest request) {
		return HttpMethod.POST.matches(request.getMethod()) && request.getHeader("Destination") != null;
	}

	/**
	 * A POST with {@code Content-Type: application/json}: the MCP endpoint (step 33) and the Git actions (steps
	 * 46–48). A cross-site form can POST, but cannot send JSON (that needs CORS, which Mizoine does not allow), so a
	 * JSON POST needs no token, with or without credentials: a chat app connects to /mcp without them first and needs
	 * the 401 that leads it to the OAuth login (step 34), not a 403 from CSRF. A form POST stays CSRF-protected.
	 */
	private static boolean isJsonPost(final HttpServletRequest request) {
		return HttpMethod.POST.matches(request.getMethod()) && request.getContentType() != null
				&& MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()));
	}

	/** D27: readable by the pages' JavaScript; {@code SameSite=Lax} like the session cookie; Secure behind HTTPS. */
	private static CookieCsrfTokenRepository csrfCookie() {
		final CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
		return repository;
	}

	/**
	 * Remembers the page asked for before login. Without the {@code continue} parameter that Spring Security
	 * adds by default (to look into the session only then): clean URLs after login. Every page needs a login,
	 * so the session is loaded anyway.
	 */
	private static HttpSessionRequestCache requestCache() {
		final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
		requestCache.setMatchingRequestParameterName(null);
		// a script's request (the editor's load or save) is no page to return to after login (Spring's default too)
		requestCache.setRequestMatcher(request -> !"XMLHttpRequest".equals(request.getHeader("X-Requested-With")));
		return requestCache;
	}

	/** Back to the page first asked for, as before, but with a relative URL (see {@link RelativeRedirectStrategy}). */
	private static SavedRequestAwareAuthenticationSuccessHandler loginSuccessHandler() {
		final SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
		handler.setRedirectStrategy(new RelativeRedirectStrategy());
		return handler;
	}

	@Bean
	LoginAttemptLimiter loginAttemptLimiter(final MizoineProperties properties) {
		final MizoineProperties.LoginLimit limit = properties.loginLimit();
		return new LoginAttemptLimiter(Clock.systemUTC(), limit.maxFailures(), limit.window());
	}

	@Bean
	LoginAttemptEvents loginAttemptEvents(final LoginAttemptLimiter limiter) {
		return new LoginAttemptEvents(limiter);
	}
}
