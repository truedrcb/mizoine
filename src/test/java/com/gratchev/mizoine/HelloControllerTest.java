package com.gratchev.mizoine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

	private final MockMvc mockMvc;

	HelloControllerTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	@WithMockUser("artem")
	void rootGreetsLoggedInUser() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(view().name("hello"))
				.andExpect(content().contentTypeCompatibleWith("text/html"))
				.andExpect(content().string(containsString("Hello, artem")));
	}

	@Test
	@WithMockUser("<script>alert(1)</script>")
	void usernameIsHtmlEscaped() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Hello, &lt;script&gt;alert(1)&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>"))));
	}
}
