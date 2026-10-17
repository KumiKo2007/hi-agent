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
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolBrokerGenericActionTest {
    private static final String ARGUMENTS = "{\"repository\":\"owner/repo\",\"title\":\"Title\",\"body\":\"Details\"}";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties", Map.of(
            "repository", Map.of("type", "string"), "title", Map.of("type", "string"),
            "body", Map.of("type", "string")), "required", List.of("repository", "title", "body"),
            "additionalProperties", false);
    private static final JsonObjectSchema MCP_SCHEMA = JsonObjectSchema.builder().addStringProperty("repository")
            .addStringProperty("title").addStringProperty("body").required("repository", "title", "body")
            .additionalProperties(false).build();

    @Test
    void genericBrokerDoesNotInferGitHubRepositoryOrInjectConnectorMarkers() {
        AtomicInteger calls = new AtomicInteger();
        McpClient client = new McpClient() {
            @Override public String key() { return "custom"; }
            @Override public List<ToolSpecification> listTools() {
                return List.of(ToolSpecification.builder().name("create_issue").description("Create issue")
                        .parameters(MCP_SCHEMA).build());
            }
            @Override public String executeTool(ToolExecutionRequest request) { calls.incrementAndGet(); return "created"; }
            @Override public List<McpResource> listResources() { throw new UnsupportedOperationException(); }
            @Override public List<McpResourceTemplate> listResourceTemplates() { throw new UnsupportedOperationException(); }
            @Override public McpReadResourceResult readResource(String uri) { throw new UnsupportedOperationException(); }
            @Override public List<McpPrompt> listPrompts() { throw new UnsupportedOperationException(); }
            @Override public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
                throw new UnsupportedOperationException();
            }
            @Override public void checkHealth() { throw new UnsupportedOperationException(); }
            @Override public void presetParameters(List<PresetParameter> parameters) { throw new UnsupportedOperationException(); }
            @Override public void close() { }
        };
        RegisteredToolAction action = new RegisteredToolAction("custom-service", "custom", "create_issue",
                "Create issue", SCHEMA, ToolActionClassification.WRITE_REQUIRES_APPROVAL);
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override public RegisteredToolAction require(AgentEntity agent, String user, String service, String name) {
                return action;
            }
            @Override public List<RegisteredToolAction> list(AgentEntity agent, String user) { return List.of(action); }
        };
        ToolBroker broker = new ToolBroker(catalog, (server, user) -> client);

        RunApprovalEntity approval = RunApprovalEntity.create("run-1",
                broker.prepareForApproval(new AgentEntity(), "owner", "custom-service", "create_issue", ARGUMENTS));

        assertEquals(null, approval.getTargetRepository());
        assertEquals(ARGUMENTS, approval.getArguments());
        assertEquals(0, calls.get());
    }
}
