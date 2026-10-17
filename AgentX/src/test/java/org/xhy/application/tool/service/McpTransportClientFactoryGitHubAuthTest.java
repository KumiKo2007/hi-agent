package org.xhy.application.tool.service;

import org.junit.jupiter.api.Test;
import org.xhy.infrastructure.exception.BusinessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpTransportClientFactoryGitHubAuthTest {
    private static final String TOKEN = "github_pat_abcdefghijklmnopqrstuvwxyz123456";

    @Test
    void shouldAttachBearerAuthorizationOnlyToTheFixedOfficialHttpsHostAndPath() {
        HttpRequest.Builder official = HttpRequest.newBuilder(URI.create("https://api.githubcopilot.com/mcp/"));
        McpTransportClientFactory.addGitHubAuthorization(official,
                URI.create("https://api.githubcopilot.com/mcp/"), TOKEN);

        assertEquals("Bearer " + TOKEN, official.build().headers().firstValue("Authorization").orElseThrow());

        for (String destination : new String[] {
                "http://api.githubcopilot.com/mcp/",
                "https://github.com/mcp/",
                "https://api.githubcopilot.com/other/",
                "https://api.githubcopilot.com.evil.example/mcp/",
                "https://evil.example@api.githubcopilot.com/mcp/",
                "https://api.githubcopilot.com:8443/mcp/",
        }) {
            HttpRequest.Builder other = HttpRequest.newBuilder(URI.create(destination));
            McpTransportClientFactory.addGitHubAuthorization(other, URI.create(destination), TOKEN);
            assertFalse(other.build().headers().firstValue("Authorization").isPresent(), destination);
        }
    }

    @Test
    void shouldDisableHttpRedirectFollowingForCredentialBearingConnections() {
        assertEquals(HttpClient.Redirect.NEVER,
                McpTransportClientFactory.noRedirectHttpClientBuilder().build().followRedirects());
    }

    @Test
    void shouldNeverResolveGitHubThroughTheGenericGateway() {
        boolean[] resolverCalled = { false };
        McpTransportClientFactory factory = new McpTransportClientFactory((serverName, userId) -> {
            resolverCalled[0] = true;
            throw new AssertionError("GitHub credentials must not use generic endpoint resolution");
        });

        assertThrows(BusinessException.class, () -> factory.open("github", "owner"));
        assertThrows(BusinessException.class, () -> factory.openWithGitHubToken("some-gateway", "owner", TOKEN));
        assertFalse(resolverCalled[0]);
    }
}
