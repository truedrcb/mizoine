package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The server's HTML pages share one head: readable on the smartphone (viewport) and one stylesheet from
 * {@code /ui/}. Before, each page had its own head and none had a viewport, so phones showed them shrunk.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PageLayoutTest {

	private static final TestRepositories REPOSITORIES = TestRepositories.create()
			.file("notes/doc.md", "# Doc")
			.repository("notes", "path: notes\naccess:\n  artem: [\"/\"]\n")
			.repository("moved", "path: missing-root\naccess:\n  artem: [\"/\"]\n");

	@DynamicPropertySource
	static void repositories(final DynamicPropertyRegistry registry) {
		REPOSITORIES.register(registry);
	}

	@AfterAll
	static void delete() throws IOException {
		REPOSITORIES.delete();
	}

	private final MockMvc mockMvc;

	PageLayoutTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@ParameterizedTest
	@ValueSource(strings = {"/", "/notes/", "/notes/doc.md", "/moved/"})
	void everyPageIsReadableOnThePhoneAndUsesTheSharedStylesheet(final String url) throws Exception {
		mockMvc.perform(get(url).with(loggedInAs(ARTEM)).header(HttpHeaders.ACCEPT, "text/html"))
				.andExpect(status().is(url.equals("/moved/") ? 500 : 200))
				.andExpect(content().string(containsString(
						"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")))
				.andExpect(content().string(containsString("<link rel=\"stylesheet\" href=\"/ui/mizoine.css\">")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"/ui/mizoine.css"})
	void stylesheetLoadsWithoutLogin(final String url) throws Exception {
		mockMvc.perform(get(url)).andExpect(status().isOk());
	}
}
