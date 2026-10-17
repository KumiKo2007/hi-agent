package org.xhy.application.github.service;

import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.Test;
import org.xhy.application.tool.service.McpClientFactory;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubMcpActionClientProviderTest {
    private static final String TOKEN = "github_pat_abcdefghijklmnopqrstuvwxyz123456";

    @Test
    void scopesGitHubTokenUseToTheAuthorizedSelectedRepositoryAndGitHubAdapter() {
        AtomicReference<String> authorizedRepository = new AtomicReference<>();
        AtomicReference<String> serverName = new AtomicReference<>();
        AtomicReference<String> forwardedToken = new AtomicReference<>();
        AtomicReference<String> genericServer = new AtomicReference<>();
        GitHubConnectionAppService connections = new GitHubConnectionAppService(null, null, null) {
            @Override
            public <T> T withAuthorizedToken(String owner, String repository, Function<String, T> action) {
                assertEquals("owner", owner);
                authorizedRepository.set(repository);
                return action.apply(TOKEN);
            }
        };
        McpClientFactory clients = new McpClientFactory() {
            @Override
            public McpClient open(String requestedServer, String userId) {
                genericServer.set(requestedServer);
                return null;
            }

            @Override
            public McpClient openWithGitHubToken(String requestedServer, String userId, String token) {
                serverName.set(requestedServer);
                forwardedToken.set(token);
                return null;
            }
        };
        GitHubMcpActionClientProvider provider = new GitHubMcpActionClientProvider(clients, connections);

        provider.open("github", "owner", "owner/repo");

        assertEquals("owner/repo", authorizedRepository.get());
        assertEquals("github", serverName.get());
        assertEquals(TOKEN, forwardedToken.get());
        assertEquals(null, genericServer.get());
    }

    @Test
    void requiresASelectedRepositoryBeforeRequestingGitHubCredentials() {
        GitHubConnectionAppService connections = new GitHubConnectionAppService(null, null, null) {
            @Override
            public <T> T withAuthorizedToken(String owner, String repository, Function<String, T> action) {
                throw new AssertionError("No token should be requested without a repository");
            }
        };
        McpClientFactory clients = (server, user) -> null;
        GitHubMcpActionClientProvider provider = new GitHubMcpActionClientProvider(clients, connections);

        assertThrows(BusinessException.class, () -> provider.open("github", "owner", null));
    }

    @Test
    void delegatesOtherConnectorsWithoutRequestingAGitHubCredential() {
        AtomicReference<String> openedServer = new AtomicReference<>();
        GitHubConnectionAppService connections = new GitHubConnectionAppService(null, null, null) {
            @Override
            public <T> T withAuthorizedToken(String owner, String repository, Function<String, T> action) {
                throw new AssertionError("Other connectors must not request GitHub credentials");
            }
        };
        McpClientFactory clients = new McpClientFactory() {
            @Override
            public McpClient open(String serverName, String userId) {
                openedServer.set(serverName);
                return null;
            }

            @Override
            public McpClient openWithGitHubToken(String serverName, String userId, String token) {
                throw new AssertionError("Other connectors must not receive GitHub credentials");
            }
        };
        GitHubMcpActionClientProvider provider = new GitHubMcpActionClientProvider(clients, connections);

        provider.open("custom", "owner", "owner/repo");

        assertEquals("custom", openedServer.get());
    }
}
