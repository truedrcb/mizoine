package com.gratchev.mizoine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultHandler;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gratchev.mizoine.repository.TempRepositoryUtils;
import com.gratchev.mizoine.repository2.Configuration.RepositoryDto;
import com.gratchev.mizoine.repository2.file.ConfigurationImpl.ConfigurationDto;
import com.gratchev.mizoine.repository2.file.RepositoryConstants;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {"users.amadeus.password=god"})
@AutoConfigureMockMvc
public class RestTest {
	private static final Logger log = LoggerFactory.getLogger(RestTest.class);
	public static final String REPO_ID1 = "test1";
	public static final String REPO_ID2 = "test2";
	private static Path configFile;

	@Autowired
	private MockMvc mvc;
	private static Path repoDir1;
	private static Path repoDir2;

	@BeforeAll
	static void setup() throws IOException {
		configFile = Files.createTempFile("miz-test-config", ".json").toAbsolutePath();
	}
	
	@BeforeEach
	void setupTest() throws Exception {
		newTempConfiguration();
		mvc.perform(put("/api2/updateConfig").with(csrf()).with(user("hacker"))).andExpect(status().isAccepted());
	}

	@AfterEach
	void disposeTest() {
		log.info("Disposing {} : {}", REPO_ID1, repoDir1);
		disposeRepository(repoDir1);
		log.info("Disposing {} : {}", REPO_ID2, repoDir2);
		disposeRepository(repoDir2);
	}

	private void disposeRepository(Path path) {
		TempRepositoryUtils.printDirectory(path.toFile());
		try {
			TempRepositoryUtils.removeDirectory(path.toFile());
		} catch (IOException e) {
			log.error("Failed to remove {}", repoDir1, e);
		}
	}

	private static void newTempConfiguration()
			throws IOException, JsonGenerationException, JsonMappingException, FileNotFoundException {
		repoDir1 = Files.createTempDirectory("miz-test-repos").toAbsolutePath();
		repoDir2 = Files.createTempDirectory("miz-test-repos").toAbsolutePath();
		final ConfigurationDto config = new ConfigurationDto();
		config.repositories = Map.of(
				REPO_ID1, new RepositoryDto(repoDir1.toString()),
				REPO_ID2, new RepositoryDto(repoDir2.toString())
		);
		try (FileOutputStream f = new FileOutputStream(configFile.toFile())) {
			new ObjectMapper().writeValue(f, config);
		}
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

	public static ResultHandler prettyPrintJson() {
		return r -> {
			log.info("Status: {}", r.getResponse().getStatus());
			final String contentAsString = r.getResponse().getContentAsString();
			try {
				final ObjectMapper mapper = new ObjectMapper();
				final Object jsonObject = mapper.readValue(contentAsString, Object.class);
				final String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
				log.info("Body: {}", prettyJson);
			} catch (Exception e) {
				log.error("Cannot pretty-print body: \"" + contentAsString + "\"", e);
			}
		};
	}

	@Test
	void api2repos() throws Exception {
		mvc.perform(get("/api2/repositories").with(user("hacker"))).andDo(prettyPrintJson()).andExpect(status().isOk())
				.andExpect(content().json("[{id:'" + REPO_ID1 + "'}, {id:'" + REPO_ID2 + "'}]"));
	}

	@Test
	void api2reposWriteMeta() throws Exception {
		final String metadata = "{\"title\":\"First repo\"}";
		mvc.perform(put("/api2/repositories('" + REPO_ID1 + "')/meta").contentType(MediaType.APPLICATION_JSON)
				.content(metadata).with(csrf()).with(user("hacker"))).andDo(prettyPrintJson())
				.andExpect(status().isAccepted());
		assertThat(repoDir1.resolve(Path.of(RepositoryConstants.META_JSON_FILENAME))).hasContent(metadata);
	}

	@Test
	void api2reposExpandMeta() throws Exception {
		mvc.perform(get("/api2/repositories?$expand=meta").with(user("hacker"))).andDo(prettyPrintJson()).andExpect(status().isOk())
				.andExpect(content().json("[{id:'" + REPO_ID1 + "'}, {id:'" + REPO_ID2 + "'}]"));
	}
}
