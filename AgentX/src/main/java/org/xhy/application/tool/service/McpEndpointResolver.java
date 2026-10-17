package org.xhy.application.tool.service;

/** Resolves a trusted MCP endpoint without exposing gateway credentials outside the service boundary. */
@FunctionalInterface
public interface McpEndpointResolver {
    McpEndpoint resolve(String serverName, String userId);
}
