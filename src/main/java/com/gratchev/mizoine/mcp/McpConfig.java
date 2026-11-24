package com.gratchev.mizoine.mcp;

import java.util.Map;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.repositories.RepositoryFiles;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

/**
 * The MCP endpoint {@code /mcp} for AI agents (step 33, T2): Streamable HTTP, stateless (no MCP sessions), behind
 * the same Spring Security filters as everything else, so only logged-in users reach it. The user is taken from the
 * request and given to every tool call; the tools apply that user's grants.
 */
@Configuration
class McpConfig {

	private final McpJsonMapper json = new JacksonMcpJsonMapper(JsonMapper.builder().build());

	@Bean
	HttpServletStatelessServerTransport mcpTransport() {
		return HttpServletStatelessServerTransport.builder()
				.jsonMapper(json)
				.messageEndpoint(McpPaths.ENDPOINT)
				// set by Spring Security's filters before the servlet runs
				.contextExtractor(request -> McpTransportContext.create(Map.of(McpTools.USER, request.getUserPrincipal().getName())))
				.build();
	}

	@Bean
	ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(final HttpServletStatelessServerTransport transport) {
		return new ServletRegistrationBean<>(transport, McpPaths.ENDPOINT);
	}

	@Bean(destroyMethod = "close")
	McpStatelessSyncServer mcpServer(final HttpServletStatelessServerTransport transport, final RepositoryFiles files,
			final MizoineProperties properties) {
		return McpServer.sync(transport)
				.jsonMapper(json)
				.serverInfo("Mizoine", "1.0")
				.instructions("Plain files in Git repositories. Store and retrieve knowledge in the folders you have "
						+ "access to; a human reviews and commits your changes.")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
				.tools(new McpTools(files, properties.maxFileSize().toBytes(), json).all())
				.build();
	}
}
