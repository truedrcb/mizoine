package com.gratchev.mizoine;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Securing: https://spring.io/guides/gs/securing-web/
 * 
 * Configuration: http://www.baeldung.com/properties-with-spring
 * 
 * Properties to map https://stackoverflow.com/questions/26275736/how-to-pass-a-mapstring-string-with-application-properties
 */
@Configuration
@EnableWebSecurity
@EnableAutoConfiguration
@EnableConfigurationProperties
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebSecurityConfig.class);

	@Autowired
	private Application.BasicUsersConfiguration usersConfiguration;

	@Override
	protected void configure(final HttpSecurity http) throws Exception {
		http
		.authorizeRequests()
		// https://stackoverflow.com/questions/20673230/spring-boot-overriding-favicon
		.antMatchers("/public/**", "/res/**", "/lib/**").permitAll()
		.anyRequest().authenticated()
		.and()
		.formLogin()
		.loginPage("/login.html")
		.loginProcessingUrl("/login")
		.defaultSuccessUrl("/")
		.permitAll()
		.and()
		.logout()
		.permitAll();

		// see http://www.baeldung.com/spring-security-remember-me
		http.rememberMe().key("rJyAqQnGfpwe7kg8");
		
		// Default setting. Not needed actually
		//http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);

		http.csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
	}

	@Autowired
	public void configureGlobal(final AuthenticationManagerBuilder auth) throws Exception {
		final InMemoryUserDetailsManagerConfigurer<AuthenticationManagerBuilder> authentication 
		= auth.inMemoryAuthentication();

		final Map<String, Application.UserCredentials> users = usersConfiguration.getUsers();
		if (users == null || users.size() < 1) {
			@SuppressWarnings("deprecation")
			final UserDetails user = User.withDefaultPasswordEncoder()
					.username("user")
					.password("password").roles("USER").build();
			authentication.withUser(user);

			LOGGER.warn("Users list not provided");
			LOGGER.warn("Default user is activated: users.user.password = password");
		} else {
			LOGGER.info("Users configured: " + users.size());

			// java.lang.IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"
			// https://stackoverflow.com/questions/46999940/spring-boot-passwordencoder-error


			for(final String userName : users.keySet()) {
				@SuppressWarnings("deprecation")
				final UserDetails user = User.withDefaultPasswordEncoder()
						.username(userName)
						.password(users.get(userName).getPassword())
						.roles("USER").build();
				authentication.withUser(user);
				LOGGER.debug("User added: " + userName);
			}
		}
	}
}