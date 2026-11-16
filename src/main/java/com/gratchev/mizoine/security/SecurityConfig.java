package com.gratchev.mizoine.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Everything requires login, except the login page. Browsers get a login form,
 * other clients HTTP Basic. Users come from {@link com.gratchev.mizoine.users.UsersConfig}.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(final HttpSecurity http) {
		return http
				.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
				.formLogin(form -> form.permitAll())
				.httpBasic(withDefaults())
				.logout(logout -> logout.permitAll())
				.build();
	}
}
