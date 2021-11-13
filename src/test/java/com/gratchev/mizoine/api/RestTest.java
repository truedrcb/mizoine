package com.gratchev.mizoine.api;

import static org.hamcrest.CoreMatchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "users.amadeus.password=god" })
@AutoConfigureMockMvc
public class RestTest {
	private static final Logger log = LoggerFactory.getLogger(RestTest.class);
	private static Path configFile; 

	@Autowired
	private MockMvc mvc;
	
	@BeforeAll
	static void setup() throws IOException {
		final String repoDir1 = Files.createTempDirectory("miz-test-repos").toAbsolutePath().toString();
		final String repoDir2 = Files.createTempDirectory("miz-test-repos").toAbsolutePath().toString();
		configFile = Files.createTempFile("miz-test-config", ".json").toAbsolutePath();
	}

	@DynamicPropertySource
	static void dynamicProperties(final DynamicPropertyRegistry registry) {
		registry.add("config", configFile::toString);
	}

	@Test
	void shouldRedirectToLogin() throws Exception {
		mvc.perform(get("/")).andDo(print()).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login.html"));
		mvc.perform(get("/login.html")).andDo(print()).andExpect(status().isOk())
				.andExpect(content().string(containsString("Remember me")));

		mvc.perform(get("/api2/app")).andDo(print()).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login.html"));
	}

	@Test
	void testLogin() throws Exception {
		// https://docs.spring.io/spring-security/site/docs/4.2.x/reference/html/test-mockmvc.html
		mvc.perform(formLogin("/login").user("amadeus").password("god")).andDo(print())
				.andExpect(status().is3xxRedirection()).andExpect(authenticated()).andExpect(redirectedUrl("/"));
	}

	@Test
	void testLoginError() throws Exception {
		mvc.perform(formLogin("/login").user("amadeus").password("dog")).andDo(print())
				.andExpect(status().is3xxRedirection()).andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login.html?error"));
	}

	@Test
	void shouldReadAppInfo() throws Exception {
		mvc.perform(get("/api/app").with(user("hacker"))).andDo(prettyPrintJson()).andExpect(status().isOk());
	}

	@Test
	void flexmarkConfig() {

	}

	@Test
	void api2app() throws Exception {
		mvc.perform(get("/api2/app").with(user("hacker"))).andDo(prettyPrintJson()).andExpect(status().isOk());
	}

	@Test
	void api2repos() throws Exception {
		mvc.perform(get("/api2/repositories").with(user("hacker"))).andDo(prettyPrintJson()).andExpect(status().isOk());
	}

	public static ResultHandler prettyPrintJson() {
		return r -> {
			log.info("Status: {}", r.getResponse().getStatus());
			final ObjectMapper mapper = new ObjectMapper();
			final Object jsonObject = mapper.readValue(r.getResponse().getContentAsString(), Object.class);
			final String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
			log.info("Body: {}", prettyJson);
		};
	}
}
