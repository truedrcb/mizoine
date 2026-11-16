package com.gratchev.mizoine.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.users.FileUserDetailsService;
import com.gratchev.mizoine.users.PasswordHashing;
import com.gratchev.mizoine.users.UsersFileParser;

/**
 * Everything requires login, except the login page. Browsers get a login form,
 * other clients HTTP Basic. Users come from the users file ({@code mizoine.users-file}).
 */
@Configuration
@EnableConfigurationProperties(MizoineProperties.class)
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

	@Bean
	UserDetailsService userDetailsService(final MizoineProperties properties) {
		return new FileUserDetailsService(properties.usersFile(), new UsersFileParser());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordHashing.encoder();
	}
}
