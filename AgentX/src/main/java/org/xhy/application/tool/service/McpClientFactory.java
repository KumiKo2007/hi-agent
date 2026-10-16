package org.xhy.application.tool.service;

import dev.langchain4j.mcp.client.McpClient;

/** MCP 外部连接边界。 */
@FunctionalInterface
public interface McpClientFactory {
    McpClient open(String serverName, String userId);
}
