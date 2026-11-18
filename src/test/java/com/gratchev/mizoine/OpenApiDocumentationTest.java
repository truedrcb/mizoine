package com.gratchev.mizoine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
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
		"mizoine.users-file=src/test/resources/users-test.yaml",
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
							+ "Every page needs a login: browsers use the login form, other clients HTTP Basic."),
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
		return get(path).with(httpBasic("artem", "artem-pass")).header("Accept", accept);
	}

	@Test
	void describesTheApplication() throws Exception {
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
				.andExpect(jsonPath("$.info.title").value("Mizoine"))
				.andExpect(jsonPath("$.info.version").isNotEmpty());
	}

	@Test
	void serverIsRelativeNotTheTestInstance() throws Exception {
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(jsonPath("$.servers[0].url").value("/"))
				.andExpect(jsonPath("$.servers.length()").value(1));
	}

	@Test
	void startPageIsDocumented() throws Exception {
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(jsonPath("$.paths['/'].get.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths['/'].get.tags").isNotEmpty())
				.andExpect(jsonPath("$.paths['/'].get.responses['200'].content", hasKey("text/html")))
				.andExpect(jsonPath("$.paths['/'].get.operationId").value("startPage"))
				.andExpect(jsonPath("$.paths['/'].get.responses['200'].content", hasKey("application/json")))
				.andExpect(jsonPath("$.paths['/'].get.responses['406'].description").isNotEmpty())
				.andExpect(jsonPath("$.paths['/'].get.responses['401'].description").isNotEmpty());
	}

	@Test
	void folderListingIsDocumented() throws Exception {
		// springdoc writes the catch-all {*path} as {path}
		final String operation = "$.paths['/{repository}/{path}'].get";
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(jsonPath(operation + ".summary").isNotEmpty())
				.andExpect(jsonPath(operation + ".operationId").value("folder"))
				.andExpect(jsonPath(operation + ".parameters[0].name").value("repository"))
				.andExpect(jsonPath(operation + ".parameters[1].name").value("path"))
				.andExpect(jsonPath(operation + ".responses['200'].content", hasKey("text/html")))
				.andExpect(jsonPath(operation + ".responses['200'].content['application/json'].schema['$ref']")
						.value("#/components/schemas/FolderListing"))
				.andExpect(jsonPath(operation + ".responses['302'].description").isNotEmpty())
				.andExpect(jsonPath(operation + ".responses['401'].description").isNotEmpty())
				.andExpect(jsonPath(operation + ".responses['404'].description").isNotEmpty())
				.andExpect(jsonPath(operation + ".responses['406'].description").isNotEmpty())
				.andExpect(jsonPath(operation + ".responses['500'].content", hasKey("text/html")))
				.andExpect(jsonPath(operation + ".responses['500'].content['application/json'].schema['$ref']")
						.value("#/components/schemas/RepositoryProblem"));
	}

	@Test
	void repositoryWithoutTrailingSlashIsDocumented() throws Exception {
		final String operation = "$.paths['/{repository}'].get";
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(jsonPath(operation + ".summary").isNotEmpty())
				.andExpect(jsonPath(operation + ".responses['302'].description").isNotEmpty())
				.andExpect(jsonPath(operation + ".responses['404'].description").isNotEmpty());
	}

	@Test
	void httpBasicIsRequiredForAllOperations() throws Exception {
		mockMvc.perform(apiDocs(API_DOCS_PATH))
				.andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
				.andExpect(jsonPath("$.security[0]", hasKey("basicAuth")));
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
