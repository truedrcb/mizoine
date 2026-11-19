package com.gratchev.mizoine;

import static com.gratchev.mizoine.TestUsers.ARTEM;
import static com.gratchev.mizoine.TestUsers.loggedInAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API description is generated only by {@link OpenApiDocumentationTest}. Other test contexts,
 * though springdoc is on the test classpath, do not serve it — like the application, which has no springdoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsNotServedTest {

	private final MockMvc mockMvc;

	ApiDocsNotServedTest(@Autowired final MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@ParameterizedTest
	@ValueSource(strings = {OpenApiDocumentationTest.API_DOCS_PATH, OpenApiDocumentationTest.API_DOCS_YAML_PATH})
	void descriptionIsNotServedEvenToLoggedInUsers(final String path) throws Exception {
		mockMvc.perform(get(path).with(loggedInAs(ARTEM)))
				.andExpect(status().isNotFound());
	}
}
