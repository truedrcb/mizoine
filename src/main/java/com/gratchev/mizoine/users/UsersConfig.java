package com.gratchev.mizoine.users;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gratchev.mizoine.MizoineProperties;

/**
 * Users from the users file ({@code mizoine.users-file}). The file is loaded and checked
 * when the bean is created, so a missing or invalid file stops the application at startup.
 */
@Configuration
@EnableConfigurationProperties(MizoineProperties.class)
public class UsersConfig {

	@Bean
	FileUserDetailsService userDetailsService(final MizoineProperties properties) {
		return new FileUserDetailsService(properties.usersFilePath(), new UsersFileParser());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordHashing.encoder();
	}
}
