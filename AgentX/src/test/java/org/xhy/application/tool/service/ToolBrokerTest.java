package org.xhy.application.tool.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.transport.PresetParameter;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 统一工具边界的外部可见行为：拒绝的动作不得到达 MCP 服务。 */
class ToolBrokerTest {
    private static final JsonObjectSchema ISSUE_SCHEMA = JsonObjectSchema.builder().addStringProperty("query")
            .required("query").additionalProperties(false).build();
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
            Map.of("query", Map.of("type", "string")), "required", List.of("query"), "additionalProperties", false);

    @Test
    void registeredReadActionCallsOnlyItsSelectedServiceWithValidatedArguments() {
        TestMcpClient client = new TestMcpClient("search_docs", ISSUE_SCHEMA);
        ToolBroker broker = broker(action(ToolActionClassification.READ_ONLY, SCHEMA), client);

        String result = broker.executeReadOnly(agent(), "user-1", "service-1", "search_docs",
                "{\"query\":\"release\"}");

        assertEquals("found", result);
        assertEquals(1, client.calls.get());
        assertEquals("search_docs", client.lastRequest.name());
        assertEquals("{\"query\":\"release\"}", client.lastRequest.arguments());
    }

    @Test
    void unclassifiedOrWriteActionNeverReachesMcp() {
        TestMcpClient client = new TestMcpClient("search_docs", ISSUE_SCHEMA);
        ToolBroker broker = broker(action(ToolActionClassification.WRITE_REQUIRES_APPROVAL, SCHEMA), client);

        assertThrows(BusinessException.class,
                () -> broker.executeReadOnly(agent(), "user-1", "service-1", "search_docs", "{\"query\":\"release\"}"));
        assertEquals(0, client.calls.get());
    }

    @Test
    void unregisteredActionNeverOpensOrCallsMcp() {
        TestMcpClient client = new TestMcpClient("search_docs", ISSUE_SCHEMA);
        ToolBroker broker = broker(action(ToolActionClassification.READ_ONLY, SCHEMA), client);

        assertThrows(BusinessException.class, () -> broker.executeReadOnly(agent(), "user-1", "service-1",
                "create_issue", "{\"query\":\"release\"}"));
        assertEquals(0, client.opens.get());
        assertEquals(0, client.calls.get());
    }

    @Test
    void invalidArgumentsAndChangedServerSchemaHaveNoExternalSideEffect() {
        TestMcpClient client = new TestMcpClient("search_docs", ISSUE_SCHEMA);
        ToolBroker broker = broker(action(ToolActionClassification.READ_ONLY, SCHEMA), client);

        assertThrows(BusinessException.class,
                () -> broker.executeReadOnly(agent(), "user-1", "service-1", "search_docs", "{\"query\":42}"));
        assertEquals(0, client.calls.get());

        TestMcpClient changed = new TestMcpClient("search_docs",
                JsonObjectSchema.builder().addStringProperty("owner").required("owner").build());
        ToolBroker changedBroker = broker(action(ToolActionClassification.READ_ONLY, SCHEMA), changed);
        assertThrows(BusinessException.class, () -> changedBroker.executeReadOnly(agent(), "user-1", "service-1",
                "search_docs", "{\"query\":\"release\"}"));
        assertEquals(0, changed.calls.get());
    }

    private static ToolBroker broker(RegisteredToolAction action, TestMcpClient client) {
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
                if (!"user-1".equals(userId) || !"service-1".equals(serviceId) || !"search_docs".equals(actionName)) {
                    throw new BusinessException("动作未获允许");
                }
                return action;
            }

            @Override
            public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
                return List.of(action);
            }
        };
        return new ToolBroker(catalog, (serverName, userId) -> {
            assertEquals("docs-service", serverName);
            assertEquals("user-1", userId);
            client.opens.incrementAndGet();
            return client;
        });
    }

    private static RegisteredToolAction action(ToolActionClassification classification, Map<String, Object> schema) {
        return new RegisteredToolAction("service-1", "docs-service", "search_docs", "Search documents", schema,
                classification);
    }

    private static AgentEntity agent() {
        AgentEntity agent = new AgentEntity();
        agent.setToolIds(List.of("service-1"));
        return agent;
    }

    private static class TestMcpClient implements McpClient {
        private final String toolName;
        private final JsonObjectSchema schema;
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger calls = new AtomicInteger();
        private ToolExecutionRequest lastRequest;

        private TestMcpClient(String toolName, JsonObjectSchema schema) {
            this.toolName = toolName;
            this.schema = schema;
        }

        @Override
        public String key() {
            return "test";
        }
        @Override
        public List<ToolSpecification> listTools() {
            return List.of(ToolSpecification.builder().name(toolName).description("Search documents").parameters(schema)
                    .build());
        }
        @Override
        public String executeTool(ToolExecutionRequest request) {
            calls.incrementAndGet();
            lastRequest = request;
            return "found";
        }
        @Override
        public List<McpResource> listResources() {
            throw new UnsupportedOperationException();
        }
        @Override
        public List<McpResourceTemplate> listResourceTemplates() {
            throw new UnsupportedOperationException();
        }
        @Override
        public McpReadResourceResult readResource(String uri) {
            throw new UnsupportedOperationException();
        }
        @Override
        public List<McpPrompt> listPrompts() {
            throw new UnsupportedOperationException();
        }
        @Override
        public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void checkHealth() {
            throw new UnsupportedOperationException();
        }
        @Override
        public void presetParameters(List<PresetParameter> parameters) {
            throw new UnsupportedOperationException();
        }
        @Override
        public void close() {
        }
    }
}
