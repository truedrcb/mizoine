package com.gratchev.mizoine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** {@code /favicon.ico}: the icon of the old version, for every visitor, also on the login page. */
@SpringBootTest(properties = "mizoine.users-file=src/test/resources/users-test.yaml")
@AutoConfigureMockMvc
class FaviconTest {

	private static final Path ICON = Path.of("src/main/resources/ui/favicon.ico");

	private final MockMvc mockMvc;

	FaviconTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void iconIsServedWithoutLogin() throws Exception {
		mockMvc.perform(get("/favicon.ico").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/x-icon"))
				.andExpect(content().bytes(Files.readAllBytes(ICON)));
	}

	@Test
	void iconIsServedWhenLoggedIn() throws Exception {
		mockMvc.perform(get("/favicon.ico").with(httpBasic("artem", "artem-pass")))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/x-icon"));
	}

	@Test
	void iconIsTheIcoFileFromTheOldVersion() throws Exception {
		final byte[] icon = Files.readAllBytes(ICON);
		// ICO header: reserved 0, type 1 (icon)
		assertThat(icon).startsWith(0, 0, 1, 0);
	}

	@Test
	void browsersMayCacheTheIcon() throws Exception {
		mockMvc.perform(get("/favicon.ico"))
				.andExpect(header().string("Cache-Control", "max-age=604800, public"));
	}

	@Test
	void onlyReadingTheIconIsOpen() throws Exception {
		mockMvc.perform(post("/favicon.ico")).andExpect(status().isForbidden());
	}

	@Test
	void otherIconsStillNeedLogin() throws Exception {
		mockMvc.perform(get("/favicon.png").accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login"));
	}
}
