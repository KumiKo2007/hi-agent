package org.xhy.application.github.service;

import dev.langchain4j.mcp.client.McpClient;
import org.springframework.stereotype.Component;
import org.xhy.application.tool.service.McpActionClientProvider;
import org.xhy.application.tool.service.McpClientFactory;
import org.xhy.infrastructure.exception.BusinessException;

/** Supplies GitHub MCP clients only after checking the user's selected repository authorization. */
@Component
public class GitHubMcpActionClientProvider implements McpActionClientProvider {
    private final McpClientFactory clients;
    private final GitHubConnectionAppService githubConnections;

    public GitHubMcpActionClientProvider(McpClientFactory clients, GitHubConnectionAppService githubConnections) {
        this.clients = clients;
        this.githubConnections = githubConnections;
    }

    @Override
    public McpClient open(String serverName, String userId, String targetRepository) {
        if (!"github".equals(serverName)) {
            return clients.open(serverName, userId);
        }
        if (targetRepository == null || targetRepository.isBlank()) {
            throw new BusinessException("GitHub MCP 连接需要已授权的目标仓库");
        }
        return githubConnections.withAuthorizedToken(userId, targetRepository,
                token -> clients.openWithGitHubToken("github", userId, token));
    }
}
