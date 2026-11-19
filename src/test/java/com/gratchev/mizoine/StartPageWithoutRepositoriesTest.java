package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Step 19.3: no repositories at all (the folder is missing) is not an error. */
@SpringBootTest(properties = {
		"mizoine.repositories-dir=src/test/resources/no-such-folder"})
@AutoConfigureMockMvc
class StartPageWithoutRepositoriesTest {

	private final MockMvc mockMvc;

	StartPageWithoutRepositoriesTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void noRepositoriesGivesTheMessage() throws Exception {
		mockMvc.perform(get("/").with(loggedInAs(ARTEM)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Hello, artem")))
				.andExpect(content().string(containsString("No accessible repositories configured for you.")));
	}
}
