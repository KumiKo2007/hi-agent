package org.xhy.application.conversation.service.message.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolProviderRequest;
import org.junit.jupiter.api.Test;
import org.xhy.application.conversation.service.handler.context.ChatContext;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.tool.service.McpClientFactory;
import org.xhy.application.tool.service.ToolActionCatalog;
import org.xhy.application.tool.service.ToolBroker;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 旧聊天入口只把平台审定的只读动作交给模型。 */
class AgentToolManagerTest {
    @Test
    void writeActionInSameMcpServiceIsNeverExposedOrExecuted() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query"), "additionalProperties", false);
        RegisteredToolAction read = new RegisteredToolAction("service-1", "docs", "search_docs", "Search", schema,
                ToolActionClassification.READ_ONLY);
        RegisteredToolAction write = new RegisteredToolAction("service-1", "docs", "create_issue", "Create", schema,
                ToolActionClassification.WRITE_REQUIRES_APPROVAL);
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
                return "search_docs".equals(actionName) ? read : write;
            }
            @Override
            public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
                return List.of(read, write);
            }
        };
        AtomicInteger externalCalls = new AtomicInteger();
        McpClientFactory clients = (serverName, userId) -> (McpClient) Proxy.newProxyInstance(
                McpClient.class.getClassLoader(), new Class<?>[]{McpClient.class}, (proxy, method, args) -> {
                    if ("listTools".equals(method.getName())) {
                        JsonObjectSchema parameters = JsonObjectSchema.builder().addStringProperty("query")
                                .required("query").additionalProperties(false).build();
                        return List.of(ToolSpecification.builder().name("search_docs").parameters(parameters).build(),
                                ToolSpecification.builder().name("create_issue").parameters(parameters).build());
                    }
                    if ("executeTool".equals(method.getName())) {
                        externalCalls.incrementAndGet();
                        return "found";
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        AgentToolManager manager = new AgentToolManager(catalog, new ToolBroker(catalog, clients), clients);
        AgentEntity agent = new AgentEntity();
        agent.setToolIds(List.of("service-1"));
        ChatContext context = new ChatContext();
        context.setAgent(agent);
        context.setUserId("user-1");

        ToolProviderResult tools = manager.createToolProvider(context)
                .provideTools(new ToolProviderRequest("chat-1", new UserMessage("查资料")));
        assertNotNull(tools.toolExecutorByName("docs__search_docs"));
        assertNull(tools.toolExecutorByName("docs__create_issue"));
        assertEquals(0, externalCalls.get());

        String result = tools.toolExecutorByName("docs__search_docs").execute(
                ToolExecutionRequest.builder().name("docs__search_docs").arguments("{\"query\":\"release\"}").build(),
                null);
        assertEquals("found", result);
        assertEquals(1, externalCalls.get());
    }
}
