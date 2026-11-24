package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Step 15: generates the OpenAPI description of all endpoints into {@value #OUTPUT} (committed).
 * <p>
 * Only for the repository docs: the application does not serve the description and has no Swagger UI.
 * springdoc is a test dependency, and the API-wide part of the description ({@link ApiDescription})
 * lives here, in test code. The endpoints carry their own annotations ({@code @Operation}, ...).
 */
@SpringBootTest(properties = {
		"springdoc.api-docs.enabled=true"})
@AutoConfigureMockMvc
@Import(OpenApiDocumentationTest.ApiDescription.class)
class OpenApiDocumentationTest {

	static final String OUTPUT = "docs/openapi.yaml";

	/**
	 * Where springdoc answers with the generated description, as JSON ("v3" = OpenAPI version 3;
	 * springdoc's default, property {@code springdoc.api-docs.path}). It exists only in test contexts that
	 * set {@code springdoc.api-docs.enabled=true}, like this one; all other tests switch it off
	 * ({@code src/test/resources/config/application.yaml}), and the application jar has no springdoc at all.
	 */
	static final String API_DOCS_PATH = "/v3/api-docs";
	/** The same description as YAML. */
	static final String API_DOCS_YAML_PATH = API_DOCS_PATH + ".yaml";
	static final String BASIC_AUTH = "basicAuth";

	/** Title, server and login method of the whole API. Read by springdoc in this test only. */
	@TestConfiguration
	@OpenAPIDefinition(
			info = @Info(title = "Mizoine", version = "1.0",
					description = "Personal knowledge base and file storage on plain files and Git. "
							+ "Every page needs a login: browsers use the login form, other clients HTTP Basic. "
							+ "Public without login: /favicon.ico and GET /ui/**, the static files of the user "
							+ "interface (logos etc.). Those under /ui/ are not listed as operations: "
							+ "they are plain files, not API methods. "
							+ "AI agents connect to the MCP endpoint /mcp (Model Context Protocol, Streamable HTTP, POST with "
							+ "JSON-RPC): tools list_repositories, list_folder, read_file, write_file, create_folder, delete, move, "
							+ "with the same grants as this API. It is not listed as an operation either. "
							+ "With OAuth configured (mizoine.oauth.clients), /mcp also takes Bearer tokens from Mizoine's own "
							+ "OAuth server for chat apps: /.well-known/oauth-protected-resource/mcp (RFC 9728), "
							+ "/.well-known/oauth-authorization-server, /oauth2/authorize and /oauth2/token (authorization code "
							+ "with PKCE, one configured client per chat app); served by Spring Security, so not listed as operations."),
			// relative: the same description fits every installation (e.g. behind nginx)
			servers = @Server(url = "/", description = "This Mizoine server"),
			security = @SecurityRequirement(name = BASIC_AUTH))
	@SecurityScheme(name = BASIC_AUTH, type = SecuritySchemeType.HTTP, scheme = "basic")
	static class ApiDescription {
	}

	private final MockMvc mockMvc;

	OpenApiDocumentationTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	/** Asks for JSON or YAML explicitly: without an Accept header the application prefers HTML (D6). */
	private static MockHttpServletRequestBuilder apiDocs(final String path) {
		final String accept = path.endsWith(".yaml") ? "application/vnd.oai.openapi" : "application/json";
		return get(path).with(loggedInAs(ARTEM)).header("Accept", accept);
	}

	// Smoke tests only: the description is generated and complete. Its details are reviewed in docs/openapi.yaml.

	@Test
	void describesTheApplication() throws Exception {
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Mizoine"))
				// served by a resource handler, which springdoc does not list as an operation
				.andExpect(jsonPath("$.info.description").value(containsString("/ui/")))
				// the MCP endpoint for agents (step 33) is a servlet, not a controller, so not listed either
				.andExpect(jsonPath("$.info.description").value(containsString("/mcp")))
				// step 34: OAuth for chat apps, served by Spring Security's filters, not controllers
				.andExpect(jsonPath("$.info.description").value(containsString("/.well-known/oauth-protected-resource")));
	}

	/** Method and path; springdoc writes {*path} as {path}. */
	@ParameterizedTest
	@ValueSource(strings = {"get /", "get /{repository}", "get /{repository}/{path}", "put /{repository}/{path}",
			"delete /{repository}/{path}", "post /{repository}/{path}"})
	void everyEndpointIsDocumented(final String endpoint) throws Exception {
		final String[] methodAndPath = endpoint.split(" ");
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(jsonPath("$.paths['" + methodAndPath[1] + "']." + methodAndPath[0] + ".summary").isNotEmpty());
	}

	@Test
	void descriptionIsWrittenToDocs() throws Exception {
		final String yaml = mockMvc.perform(apiDocs(API_DOCS_YAML_PATH))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		final Path output = Path.of(OUTPUT);
		Files.createDirectories(output.getParent());
		Files.writeString(output, yaml);

		assertThat(Files.readString(output)).isEqualTo(yaml).contains("title: Mizoine");
	}
}
