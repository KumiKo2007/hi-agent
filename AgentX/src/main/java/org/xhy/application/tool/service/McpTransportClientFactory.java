package org.xhy.application.tool.service;

import java.net.URI;
import java.time.Duration;
import java.net.http.HttpClient;

import dev.langchain4j.mcp.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.stereotype.Component;
import org.xhy.domain.tool.constant.McpTransportType;
import org.xhy.infrastructure.exception.BusinessException;

/** Uses the official Java MCP SDK for both Streamable HTTP and migration-period SSE transports. */
@Component
public class McpTransportClientFactory implements McpClientFactory {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(10);
    private static final URI OFFICIAL_GITHUB_MCP = URI.create("https://api.githubcopilot.com/mcp/");

    private final McpEndpointResolver endpoints;

    public McpTransportClientFactory(McpEndpointResolver endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public McpClient open(String serverName, String userId) {
        if ("github".equals(serverName)) {
            throw new BusinessException("GitHub MCP 连接需要安全的授权通道");
        }
        return open(endpoints.resolve(serverName, userId));
    }

    /** Opens only GitHub's documented remote MCP endpoint with a user-scoped bearer credential. */
    @Override
    public McpClient openWithGitHubToken(String serverName, String userId, String githubToken) {
        if (!"github".equals(serverName) || userId == null || userId.isBlank() || !isUsableCredential(githubToken)) {
            throw new BusinessException("GitHub MCP 连接未能通过安全校验");
        }
        return open(new McpEndpoint(McpTransportType.STREAMABLE_HTTP, OFFICIAL_GITHUB_MCP), githubToken);
    }

    private boolean isUsableCredential(String token) {
        return token != null && token.matches("github_pat_[A-Za-z0-9_]{20,512}");
    }

    /** Opens a pre-resolved trusted endpoint, used while validating a newly uploaded service. */
    public McpClient open(McpEndpoint endpoint) {
        return open(endpoint, null);
    }

    private McpClient open(McpEndpoint endpoint, String githubToken) {
        McpClientTransport transport = createTransport(endpoint, githubToken);
        McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(transport).requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(INITIALIZATION_TIMEOUT).build();
        try {
            client.initialize();
            return new SdkMcpClientAdapter(clientKey(endpoint), client);
        } catch (RuntimeException exception) {
            try {
                client.closeGracefully();
            } catch (RuntimeException ignored) {
                // Preserve the initialization failure while still attempting to release SDK resources.
            }
            throw exception;
        }
    }

    private McpClientTransport createTransport(McpEndpoint endpoint, String githubToken) {
        URI uri = endpoint.uri();
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("MCP endpoint must be a trusted HTTP(S) URI");
        }
        String baseUri = uri.getScheme() + "://" + uri.getRawAuthority();
        String endpointPathAndQuery = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        if (githubToken != null && !isOfficialGitHubMcpUri(uri)) {
            throw new IllegalArgumentException("GitHub credentials are restricted to the official MCP endpoint");
        }
        if (endpoint.transportType() == McpTransportType.STREAMABLE_HTTP) {
            HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(baseUri)
                    .endpoint(endpointPathAndQuery).connectTimeout(CONNECT_TIMEOUT).openConnectionOnStartup(false);
            if (githubToken != null) {
                builder.clientBuilder(noRedirectHttpClientBuilder()).httpRequestCustomizer((request, method, requestUri,
                        body, context) -> addGitHubAuthorization(request, requestUri, githubToken));
            }
            return builder.build();
        }
        String gatewayQuery = uri.getRawQuery();
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(baseUri)
                .sseEndpoint(endpointPathAndQuery).connectTimeout(CONNECT_TIMEOUT)
                .httpRequestCustomizer((request, method, requestUri, body, context) -> {
                    if (gatewayQuery != null && !gatewayQuery.isBlank() && sameOrigin(uri, requestUri)
                            && !hasGatewayQuery(requestUri)) {
                        request.uri(appendQuery(requestUri, gatewayQuery));
                    }
                    if (githubToken != null) {
                        addGitHubAuthorization(request, requestUri, githubToken);
                    }
                });
        if (githubToken != null) {
            builder.clientBuilder(noRedirectHttpClientBuilder());
        }
        return builder.build();
    }

    static void addGitHubAuthorization(java.net.http.HttpRequest.Builder request, URI requestUri, String githubToken) {
        if (isOfficialGitHubMcpUri(requestUri)) {
            request.header("Authorization", "Bearer " + githubToken);
        }
    }

    private static boolean isOfficialGitHubMcpUri(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "api.githubcopilot.com".equalsIgnoreCase(uri.getHost()) && uri.getPort() == -1
                && uri.getUserInfo() == null && "/mcp/".equals(uri.getRawPath()) && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
    }

    static HttpClient.Builder noRedirectHttpClientBuilder() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
    }

    private boolean sameOrigin(URI expected, URI actual) {
        return expected.getScheme().equalsIgnoreCase(actual.getScheme())
                && expected.getHost().equalsIgnoreCase(actual.getHost())
                && effectivePort(expected) == effectivePort(actual);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean hasGatewayQuery(URI uri) {
        return uri.getRawQuery() != null && uri.getRawQuery().contains("api_key=");
    }

    private URI appendQuery(URI uri, String query) {
        String raw = uri.toASCIIString();
        return URI.create(raw + (uri.getRawQuery() == null ? "?" : "&") + query);
    }

    private String clientKey(McpEndpoint endpoint) {
        return endpoint.transportType() + "://" + endpoint.uri().getHost() + endpoint.uri().getRawPath();
    }
}
