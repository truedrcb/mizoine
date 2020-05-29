package com.gratchev.mizoine.api;

import static org.hamcrest.CoreMatchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "users.amadeus.password=god" })
@AutoConfigureMockMvc
public class RestTest {

	@Autowired
	private MockMvc mvc;
	
	@Test
	public void shouldRedirectToLogin() throws Exception {
		mvc.perform(get("/")).andDo(print()).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("**/login.html"));
		mvc.perform(get("/login.html")).andDo(print()).andExpect(status().isOk())
				.andExpect(content().string(containsString("Remember me")));
	}

	@Test
	public void shouldLogin() throws Exception {
		// https://docs.spring.io/spring-security/site/docs/4.2.x/reference/html/test-mockmvc.html
		mvc.perform(formLogin("/login").user("amadeus").password("god")).andDo(print())
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
	}

	@Test
	void flexmarkConfig() {

	}

}
