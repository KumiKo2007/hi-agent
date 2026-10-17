package org.xhy.application.tool.service;

import dev.langchain4j.mcp.client.McpClient;

/** Supplies an authorized MCP client for a registered action and optional target repository. */
@FunctionalInterface
public interface McpActionClientProvider {
    McpClient open(String serverName, String userId, String targetRepository);
}
