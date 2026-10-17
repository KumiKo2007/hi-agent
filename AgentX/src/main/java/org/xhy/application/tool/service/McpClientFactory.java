package org.xhy.application.tool.service;

import dev.langchain4j.mcp.client.McpClient;
import org.xhy.infrastructure.exception.BusinessException;

/** MCP 外部连接边界。 */
@FunctionalInterface
public interface McpClientFactory {
    McpClient open(String serverName, String userId);

    /** Opens an MCP connection with an owner token only when a secure credential adapter is configured. Implementations
     * must fail closed unless they can authenticate the verified GitHub MCP destination. */
    default McpClient openWithGitHubToken(String serverName, String userId, String githubToken) {
        throw new BusinessException("MCP 服务未配置安全的 GitHub 凭据通道，写入已停止");
    }
}
