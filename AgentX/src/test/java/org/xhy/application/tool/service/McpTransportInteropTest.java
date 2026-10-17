package org.xhy.application.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.Test;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.tool.constant.McpTransportType;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic wire-level interop for the frozen read-only MCP action boundary. */
class McpTransportInteropTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> FROZEN_SCHEMA = Map.of("type", "object", "properties",
            Map.of("query", Map.of("type", "string")), "required", List.of("query"), "additionalProperties", false);

    @Test
    void streamableHttpAndLegacySseExecuteTheSameReadOnlyActionThroughTheBroker() throws Exception {
        for (McpTransportType transportType : McpTransportType.values()) {
            try (McpInteropFixture fixture = new McpInteropFixture(transportType, "query")) {
                McpTransportClientFactory clients = clientsFor(fixture, transportType);
                ToolBroker broker = broker(clients);

                String result = broker.executeReadOnly(agent(), "user-1", "service-1", "search_docs",
                        "{\"query\":\"release\"}");

                assertEquals("found:release", result, transportType.name());
                assertEquals(1, fixture.toolCalls.get(), transportType.name());
                assertEquals(Map.of("query", "release"), fixture.lastArguments, transportType.name());
                if (transportType == McpTransportType.STREAMABLE_HTTP) {
                    assertEquals(2, fixture.requestsWithSession.get());
                    assertEquals(1, fixture.sessionDeletes.get());
                }
            }
        }
    }

    @Test
    void changedRuntimeSchemaPausesActionBeforeAnyToolCall() throws Exception {
        for (McpTransportType transportType : McpTransportType.values()) {
            try (McpInteropFixture fixture = new McpInteropFixture(transportType, "owner")) {
                ToolBroker broker = broker(clientsFor(fixture, transportType));

                BusinessException error = assertThrows(BusinessException.class, () -> broker.executeReadOnly(agent(),
                        "user-1", "service-1", "search_docs", "{\"query\":\"release\"}"));

                assertEquals(McpSchemaMismatchException.USER_MESSAGE, error.getMessage());
                assertEquals(0, fixture.toolCalls.get());
                if (transportType == McpTransportType.STREAMABLE_HTTP) {
                    assertEquals(1, fixture.sessionDeletes.get());
                }
            }
        }
    }

    @Test
    void unsupportedLiveSchemaConstraintPausesActionBeforeAnyToolCall() throws Exception {
        try (McpInteropFixture fixture = new McpInteropFixture(McpTransportType.STREAMABLE_HTTP, "query", "^.{1,8}$")) {
            ToolBroker broker = broker(clientsFor(fixture, McpTransportType.STREAMABLE_HTTP));

            McpSchemaMismatchException error = assertThrows(McpSchemaMismatchException.class, () -> broker
                    .executeReadOnly(agent(), "user-1", "service-1", "search_docs", "{\"query\":\"release\"}"));

            assertEquals(McpSchemaMismatchException.USER_MESSAGE, error.getMessage());
            assertEquals(0, fixture.toolCalls.get());
            assertEquals(1, fixture.sessionDeletes.get());
        }
    }

    @Test
    void mcpToolErrorsMustNotBeReturnedAsSuccessfulReadResults() throws Exception {
        try (McpInteropFixture fixture = new McpInteropFixture(McpTransportType.STREAMABLE_HTTP, "query", null, true)) {
            ToolBroker broker = broker(clientsFor(fixture, McpTransportType.STREAMABLE_HTTP));

            assertThrows(BusinessException.class, () -> broker.executeReadOnly(agent(), "user-1", "service-1",
                    "search_docs", "{\"query\":\"release\"}"));
            assertEquals(1, fixture.toolCalls.get());
        }
    }

    @Test
    void failsClosedWithoutContactingAnyMcpEndpointWhenNoVerifiedCredentialAdapterIsConfigured() throws Exception {
        String token = "github_pat_abcdefghijklmnopqrstuvwxyz123456";
        for (McpTransportType transportType : McpTransportType.values()) {
            try (McpInteropFixture fixture = new McpInteropFixture(transportType, "query")) {
                McpTransportClientFactory clients = clientsFor(fixture, transportType);

                assertThrows(BusinessException.class,
                        () -> clients.openWithGitHubToken("docs-service", "user-1", token));

                assertEquals(0, fixture.totalRequests.get(), transportType.name());
            }
        }
    }

    @Test
    void refusesCredentialsForSharedAndOtherOwnerIdentitiesBeforeResolvingAnEndpoint() throws Exception {
        String token = "github_pat_abcdefghijklmnopqrstuvwxyz123456";
        try (McpInteropFixture fixture = new McpInteropFixture(McpTransportType.STREAMABLE_HTTP, "query")) {
            AtomicInteger resolutions = new AtomicInteger();
            McpTransportClientFactory clients = new McpTransportClientFactory((serverName, userId) -> {
                resolutions.incrementAndGet();
                return new McpEndpoint(McpTransportType.STREAMABLE_HTTP, URI.create(fixture.endpointUrl()));
            });

            assertThrows(BusinessException.class, () -> clients.openWithGitHubToken("shared-review", "user-1", token));
            assertThrows(BusinessException.class, () -> clients.openWithGitHubToken("docs-service", "user-2", token));
            assertEquals(0, resolutions.get());
            assertEquals(0, fixture.totalRequests.get());
        }
    }

    private static McpTransportClientFactory clientsFor(McpInteropFixture fixture, McpTransportType type) {
        return clientsFor(fixture, type, new McpEndpoint(type, URI.create(fixture.endpointUrl())));
    }

    private static McpTransportClientFactory clientsFor(McpInteropFixture fixture, McpTransportType type,
            McpEndpoint endpoint) {
        return new McpTransportClientFactory((serverName, userId) -> {
            assertEquals("docs-service", serverName);
            assertEquals("user-1", userId);
            return endpoint;
        });
    }

    private static ToolBroker broker(McpClientFactory clients) {
        RegisteredToolAction action = new RegisteredToolAction("service-1", "docs-service", "search_docs",
                "Search documents", FROZEN_SCHEMA, ToolActionClassification.READ_ONLY);
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
                return action;
            }

            @Override
            public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
                return List.of(action);
            }
        };
        return new ToolBroker(catalog, clients);
    }

    private static AgentEntity agent() {
        AgentEntity agent = new AgentEntity();
        agent.setToolIds(List.of("service-1"));
        return agent;
    }

    private static final class McpInteropFixture implements AutoCloseable {
        private final HttpServer server;
        private final McpTransportType transportType;
        private final String runtimeParameter;
        private final String runtimePattern;
        private final boolean toolError;
        private final AtomicInteger totalRequests = new AtomicInteger();
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final AtomicInteger requestsWithSession = new AtomicInteger();
        private final AtomicInteger sessionDeletes = new AtomicInteger();
        private final CountDownLatch closeSse = new CountDownLatch(1);
        private volatile com.sun.net.httpserver.HttpExchange sseExchange;
        private volatile java.io.OutputStream sseOutput;
        private volatile Map<String, Object> lastArguments;

        private McpInteropFixture(McpTransportType transportType, String runtimeParameter) throws IOException {
            this(transportType, runtimeParameter, null);
        }

        private McpInteropFixture(McpTransportType transportType, String runtimeParameter, String runtimePattern)
                throws IOException {
            this(transportType, runtimeParameter, runtimePattern, false);
        }

        private McpInteropFixture(McpTransportType transportType, String runtimeParameter, String runtimePattern,
                boolean toolError) throws IOException {
            this.transportType = transportType;
            this.runtimeParameter = runtimeParameter;
            this.runtimePattern = runtimePattern;
            this.toolError = toolError;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "mcp-interop-fixture");
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/mcp", this::handleStreamable);
            server.createContext("/legacy/sse", this::handleSse);
            server.createContext("/legacy/messages", this::handleSseMessage);
            server.start();
        }

        private String endpointUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort()
                    + (transportType == McpTransportType.STREAMABLE_HTTP ? "/mcp" : "/legacy/sse");
        }

        private void handleStreamable(HttpExchange exchange) throws IOException {
            recordRequest(exchange);
            if ("DELETE".equals(exchange.getRequestMethod())) {
                if ("fixture-session".equals(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"))) {
                    sessionDeletes.incrementAndGet();
                }
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            Map<String, Object> request = readJson(exchange);
            String method = (String) request.get("method");
            if (!"initialize".equals(method)
                    && !"fixture-session".equals(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"))) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            if ("tools/list".equals(method) || "tools/call".equals(method)) {
                requestsWithSession.incrementAndGet();
            }
            if ("notifications/initialized".equals(method)) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }
            Object result = switch (method) {
                case "initialize" -> initializeResult(request);
                case "tools/list" -> Map.of("tools", List.of(toolDefinition()));
                case "tools/call" -> callTool(request);
                default -> Map.of();
            };
            Map<String, Object> response = Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if ("initialize".equals(method)) {
                exchange.getResponseHeaders().add("Mcp-Session-Id", "fixture-session");
            }
            sendJson(exchange, 200, response);
        }

        private void handleSse(HttpExchange exchange) throws IOException {
            recordRequest(exchange);
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            sseExchange = exchange;
            sseOutput = exchange.getResponseBody();
            sseOutput.write("event: endpoint\ndata: /legacy/messages?sessionId=fixture-session\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            sseOutput.flush();
            try {
                closeSse.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        private void handleSseMessage(HttpExchange exchange) throws IOException {
            recordRequest(exchange);
            Map<String, Object> request = readJson(exchange);
            String method = (String) request.get("method");
            if (!"notifications/initialized".equals(method)) {
                Object result = switch (method) {
                    case "initialize" -> initializeResult(request);
                    case "tools/list" -> Map.of("tools", List.of(toolDefinition()));
                    case "tools/call" -> callTool(request);
                    default -> Map.of();
                };
                writeSseMessage(Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
            }
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        }

        private Map<String, Object> callTool(Map<String, Object> request) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            lastArguments = arguments;
            toolCalls.incrementAndGet();
            return Map.of("content", List.of(Map.of("type", "text", "text", "found:" + arguments.get("query"))),
                    "isError", toolError);
        }

        private void recordRequest(HttpExchange exchange) {
            totalRequests.incrementAndGet();
        }

        private Map<String, Object> initializeResult(Map<String, Object> request) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            return Map.of("protocolVersion", params.get("protocolVersion"), "capabilities",
                    Map.of("tools", Map.of("listChanged", false)), "serverInfo",
                    Map.of("name", "local-mcp-fixture", "version", "1.0.0"));
        }

        private Map<String, Object> toolDefinition() {
            Map<String, Object> parameterSchema = new java.util.LinkedHashMap<>();
            parameterSchema.put("type", "string");
            if (runtimePattern != null) {
                parameterSchema.put("pattern", runtimePattern);
            }
            return Map.of("name", "search_docs", "description", "Search documents", "inputSchema",
                    Map.of("type", "object", "properties", Map.of(runtimeParameter, parameterSchema), "required",
                            List.of(runtimeParameter), "additionalProperties", false));
        }

        private Map<String, Object> readJson(HttpExchange exchange) throws IOException {
            return JSON.readValue(exchange.getRequestBody(), Map.class);
        }

        private void sendJson(HttpExchange exchange, int status, Map<String, Object> value) throws IOException {
            byte[] body = JSON.writeValueAsBytes(value);
            exchange.getResponseHeaders().add("Content-Length", Integer.toString(body.length));
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private void writeSseMessage(Map<String, Object> value) throws IOException {
            java.io.OutputStream output = sseOutput;
            if (output == null) {
                throw new IllegalStateException("SSE stream was not opened");
            }
            synchronized (output) {
                output.write(("event: message\ndata: " + JSON.writeValueAsString(value) + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        }

        @Override
        public void close() {
            closeSse.countDown();
            if (sseExchange != null) {
                sseExchange.close();
            }
            server.stop(0);
        }
    }
}
