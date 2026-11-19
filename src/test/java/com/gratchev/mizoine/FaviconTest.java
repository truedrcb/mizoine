package com.gratchev.mizoine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code /favicon.ico}: the icon of the old version ({@code ui/favicon.ico}), without login, also on the login
 * page. Access rules for {@code /ui/**} in general: {@link UiResourcesTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FaviconTest {

	private final MockMvc mockMvc;

	FaviconTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void iconIsServedWithoutLogin() throws Exception {
		// Accept as a browser sends it for page loads: D6 must not turn it into HTML
		mockMvc.perform(get(WebConfig.FAVICON_PATH).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/x-icon"))
				.andExpect(content().bytes(Files.readAllBytes(Path.of("src/main/resources/ui/favicon.ico"))));
	}

	@Test
	void browsersMayCacheTheIcon() throws Exception {
		mockMvc.perform(get(WebConfig.FAVICON_PATH))
				.andExpect(header().string("Cache-Control", "max-age=604800, public"));
	}
}
