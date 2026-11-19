package com.gratchev.mizoine.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.gratchev.mizoine.MizoineProperties;

import jakarta.servlet.DispatcherType;

/**
 * Everything requires login, except the login page. Browsers get a login form,
 * other clients HTTP Basic. Users come from {@link com.gratchev.mizoine.users.UsersConfig}.
 * Spring Security defaults stay on (CSRF, nosniff, X-Frame-Options DENY, no-cache); CSP and referrer policy are added.
 * Failed logins are limited per client address ({@link LoginAttemptLimiter}, {@code mizoine.login-limit.*}).
 */
@Configuration
public class SecurityConfig {

	/** Only own resources, no plugins, no framing, forms post only to this server. */
	static final String CONTENT_SECURITY_POLICY =
			"default-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

	@Bean
	SecurityFilterChain securityFilterChain(final HttpSecurity http, final LoginAttemptLimiter limiter) {
		return http
				.addFilterBefore(new LoginLimitFilter(limiter), UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(requests -> requests
						// rendering an error (e.g. 400 for a rejected URL) needs no login: otherwise the error turns
						// into a login redirect. Error pages show no internals (step 8). A direct GET /error still needs login.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.anyRequest().authenticated())
				.requestCache(cache -> cache.requestCache(requestCache()))
				.formLogin(form -> form.permitAll().successHandler(loginSuccessHandler()))
				.httpBasic(withDefaults())
				.logout(logout -> logout.permitAll())
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
				.build();
	}

	/**
	 * Remembers the page asked for before login. Without the {@code continue} parameter that Spring Security
	 * adds by default (to look into the session only then): clean URLs after login. Every page needs a login,
	 * so the session is loaded anyway.
	 */
	private static HttpSessionRequestCache requestCache() {
		final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
		requestCache.setMatchingRequestParameterName(null);
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
