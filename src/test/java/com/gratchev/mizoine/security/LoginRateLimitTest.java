package com.gratchev.mizoine.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Failed logins per client address are limited, for HTTP Basic and the login form.
 * Each test uses its own address, so the tests do not affect each other.
 */
@SpringBootTest(properties = {
		"mizoine.users-file=src/test/resources/users-test.yaml",
		"mizoine.login-limit.max-failures=3",
		"mizoine.login-limit.window=5m"})
@AutoConfigureMockMvc
class LoginRateLimitTest {

	private final MockMvc mockMvc;

	LoginRateLimitTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	private static RequestPostProcessor from(final String address) {
		return request -> {
			request.setRemoteAddr(address);
			return request;
		};
	}

	private MockHttpServletRequestBuilder formLogin(final String username, final String password, final String address) {
		return post("/login").param("username", username).param("password", password).with(csrf()).with(from(address));
	}

	private void wrongBasicLogins(final String address, final int times) throws Exception {
		for (int i = 0; i < times; i++) {
			mockMvc.perform(get("/").with(httpBasic("artem", "wrong")).with(from(address)))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void basicAuthIsRejectedAfterTooManyFailuresEvenWithRightPassword() throws Exception {
		wrongBasicLogins("10.1.0.1", 3);

		mockMvc.perform(get("/").with(httpBasic("artem", "artem-pass")).with(from("10.1.0.1")))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "300"));
	}

	@Test
	void unknownUsernamesCountToo() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(get("/").with(httpBasic("guess" + i, "x")).with(from("10.1.0.2")))
					.andExpect(status().isUnauthorized());
		}

		mockMvc.perform(get("/").with(httpBasic("artem", "artem-pass")).with(from("10.1.0.2")))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void otherAddressesAreNotAffected() throws Exception {
		wrongBasicLogins("10.1.0.3", 3);

		mockMvc.perform(get("/").with(httpBasic("artem", "artem-pass")).with(from("10.1.0.4")))
				.andExpect(status().isOk());
	}

	@Test
	void formLoginFailuresCountAndBlockFormLogin() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(formLogin("artem", "wrong", "10.1.0.5"))
					.andExpect(redirectedUrl("/login?error"));
		}

		mockMvc.perform(formLogin("artem", "artem-pass", "10.1.0.5"))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void formAndBasicFailuresAddUp() throws Exception {
		wrongBasicLogins("10.1.0.6", 2);
		mockMvc.perform(formLogin("artem", "wrong", "10.1.0.6"));

		mockMvc.perform(get("/").with(httpBasic("artem", "artem-pass")).with(from("10.1.0.6")))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void requestsWithoutCredentialsStillWorkWhenBlocked() throws Exception {
		wrongBasicLogins("10.1.0.7", 3);

		mockMvc.perform(get("/login").with(from("10.1.0.7")))
				.andExpect(status().isOk());
	}

	@Test
	void successfulLoginResetsFailures() throws Exception {
		wrongBasicLogins("10.1.0.8", 2);
		mockMvc.perform(get("/").with(httpBasic("artem", "artem-pass")).with(from("10.1.0.8")))
				.andExpect(status().isOk());
		wrongBasicLogins("10.1.0.8", 2);

		mockMvc.perform(get("/").with(httpBasic("artem", "artem-pass")).with(from("10.1.0.8")))
				.andExpect(status().isOk());
	}
}
