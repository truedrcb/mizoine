package com.gratchev.mizoine.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.gratchev.mizoine.MizoineProperties;

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
				.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
				.formLogin(form -> form.permitAll())
				.httpBasic(withDefaults())
				.logout(logout -> logout.permitAll())
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
				.build();
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
