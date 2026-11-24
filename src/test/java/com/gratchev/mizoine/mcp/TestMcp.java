package com.gratchev.mizoine.mcp;

import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/** The MCP side of the tests: an agent's client against the test server, and tool calls. */
final class TestMcp {

	private TestMcp() {
	}

	/**
	 * Connects (initializes) an MCP client to {@code /mcp}.
	 *
	 * @param authorization value of the {@code Authorization} header (Basic or Bearer); {@code null} = none
	 */
	static McpSyncClient connect(final String baseUrl, final String authorization) {
		final McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder(baseUrl)
				.endpoint(McpPaths.ENDPOINT)
				.addHttpRequestCustomizer((request, method, uri, body, context) -> {
					if (authorization != null) {
						request.header("Authorization", authorization);
					}
				})
				.build()).build();
		client.initialize();
		return client;
	}

	static CallToolRequest request(final String tool, final Map<String, Object> arguments) {
		return CallToolRequest.builder(tool).arguments(arguments).build();
	}

	static CallToolResult call(final McpSyncClient client, final String tool, final Map<String, Object> arguments) {
		return client.callTool(request(tool, arguments));
	}

	/** The first text of the result; errors start with {@code "error: "}. */
	static String text(final CallToolResult result) {
		return (Boolean.TRUE.equals(result.isError()) ? "error: " : "") + ((TextContent) result.content().getFirst()).text();
	}
}
