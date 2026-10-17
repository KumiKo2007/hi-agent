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
import org.xhy.application.github.service.GitHubMcpToolActionAdapter;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolBrokerApprovalTest {
    private static final String ARGS = "{\"repository\":\"KumiKo2007/hi-agent\",\"title\":\"Add release note\"}";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
            Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string")), "required",
            List.of("repository", "title"), "additionalProperties", false);
    private static final JsonObjectSchema MCP_SCHEMA = JsonObjectSchema.builder().addStringProperty("repository")
            .addStringProperty("title").required("repository", "title").additionalProperties(false).build();

    @Test
    void shouldCreateAnApprovalDraftFromValidatedFixedWriteArguments() {
        TestMcpClient mcp = new TestMcpClient(MCP_SCHEMA);
        ToolBroker broker = broker(action(SCHEMA), mcp);

        RunApprovalDraft draft = broker.prepareForApproval(agent(), "owner", "service-1", "create_issue", ARGS);

        assertEquals("service-1", draft.serviceId());
        assertEquals("create_issue", draft.actionName());
        assertEquals("kumiko2007/hi-agent", draft.targetRepository());
        assertEquals(ARGS, draft.arguments());
        assertEquals(0, mcp.calls.get());
    }

    @Test
    void shouldAddAnInvisibleStableMarkerToApprovedIssueBodyWhenSchemaSupportsIt() {
        Map<String, Object> issueSchema = Map.of("type", "object", "properties",
                Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string"), "body",
                        Map.of("type", "string")),
                "required", List.of("repository", "title"), "additionalProperties", false);
        JsonObjectSchema runtimeSchema = JsonObjectSchema.builder().addStringProperty("repository")
                .addStringProperty("title").addStringProperty("body").required("repository", "title")
                .additionalProperties(false).build();
        TestMcpClient mcp = new TestMcpClient(runtimeSchema);
        ToolBroker broker = broker(action(issueSchema), mcp);

        RunApprovalDraft draft = broker.prepareForApproval(agent(), "owner", "service-1", "create_issue",
                "{\"repository\":\"KumiKo2007/hi-agent\",\"title\":\"Add release note\",\"body\":\"Details\"}");
        RunApprovalEntity approval = RunApprovalEntity.create("run-1", draft);

        assertEquals(draft.intentId(), approval.getId());
        assertTrue(draft.arguments().contains("<!-- hi-agent-action-intent:" + approval.getId() + " -->"));
        assertTrue(draft.arguments().contains("Details\\n\\n<!-- hi-agent-action-intent:"),
                "the HTML comment should be invisible in rendered issue content while fixed preview args retain it");
    }

    @Test
    void shouldExecuteStoredApprovalSnapshotWhenAnInjectedMcpClientIsAvailable() {
        TestMcpClient mcp = new TestMcpClient(MCP_SCHEMA);
        ToolBroker broker = broker(action(SCHEMA), mcp);
        RunApprovalEntity approval = approval("kumiko2007/hi-agent", ARGS, SCHEMA);

        var result = broker.executeApprovedWrite(agent(), "owner", approval, () -> {
        });

        assertEquals("github", result.serverName());
        assertEquals("create_issue", result.actionName());
        assertEquals("kumiko2007/hi-agent", result.targetRepository());
        assertEquals("created", result.resultPayload());
        assertEquals(1, mcp.calls.get());
        assertEquals("create_issue", mcp.lastRequest.name());
        assertEquals(ARGS, mcp.lastRequest.arguments());
    }

    @Test
    void shouldRejectChangedParametersOrTargetBeforeCallingMcp() {
        TestMcpClient mcp = new TestMcpClient(MCP_SCHEMA);
        ToolBroker broker = broker(action(SCHEMA), mcp);
        RunApprovalEntity changedTarget = approval("other-org/other-repo", ARGS, SCHEMA);
        RunApprovalEntity unapproved = approval("kumiko2007/hi-agent", ARGS, SCHEMA);
        unapproved.setStatus("PENDING");

        assertThrows(BusinessException.class,
                () -> broker.executeApprovedWrite(agent(), "owner", changedTarget, () -> {
                }));
        assertThrows(BusinessException.class,
                () -> broker.executeApprovedWrite(agent(), "owner", unapproved, () -> {
                }));
        assertEquals(0, mcp.calls.get());
    }

    @Test
    void shouldRejectShapeDriftAndMissingRepositoryWithoutMcpWrite() {
        TestMcpClient mcp = new TestMcpClient(JsonObjectSchema.builder().addStringProperty("repository")
                .addStringProperty("title").addStringProperty("body").required("repository", "title", "body")
                .additionalProperties(false).build());
        ToolBroker broker = broker(action(SCHEMA), mcp);

        assertThrows(BusinessException.class, () -> broker.prepareForApproval(agent(), "owner", "service-1",
                "create_issue", "{\"title\":\"missing target\"}"));
        assertThrows(BusinessException.class, () -> broker.executeApprovedWrite(agent(), "owner",
                approval("kumiko2007/hi-agent", ARGS, SCHEMA), () -> {
                }));
        assertEquals(0, mcp.calls.get());
    }

    @Test
    void shouldRejectCatalogAndRuntimeSchemaDriftFromTheSnapshotExposedToTheModel() {
        Map<String, Object> changedSchema = Map.of("type", "object", "properties",
                Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string"), "body",
                        Map.of("type", "string")),
                "required", List.of("repository", "title", "body"), "additionalProperties", false);
        JsonObjectSchema changedRuntime = JsonObjectSchema.builder().addStringProperty("repository")
                .addStringProperty("title").addStringProperty("body").required("repository", "title", "body")
                .additionalProperties(false).build();
        AtomicReference<RegisteredToolAction> current = new AtomicReference<>(action(SCHEMA));
        TestMcpClient mcp = new TestMcpClient(MCP_SCHEMA);
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
                return current.get();
            }

            @Override
            public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
                return List.of(current.get());
            }
        };
        ToolBroker broker = new ToolBroker(catalog, (serverName, userId) -> mcp,
                (serverName, userId, targetRepository) -> mcp);
        ToolBroker.ModelTool exposed = broker.listModelTools(agent(), "owner", "kumiko2007/hi-agent").get(0);
        current.set(action(changedSchema));
        mcp.schema = changedRuntime;

        McpSchemaMismatchException mismatch = assertThrows(McpSchemaMismatchException.class,
                () -> broker.prepareForApproval(agent(), "owner", "service-1", "create_issue",
                        "{\"repository\":\"KumiKo2007/hi-agent\",\"title\":\"Title\",\"body\":\"Body\"}",
                        exposed.schemaSnapshot()));

        assertTrue(mismatch.getMessage().contains("结构已变化"));
        assertEquals(0, mcp.calls.get());
    }

    @Test
    void shouldRejectStoredApprovalWhenTheCurrentCatalogDiffersBeforeOpeningCredentialTransport() {
        Map<String, Object> changedSchema = Map.of("type", "object", "properties",
                Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string"), "body",
                        Map.of("type", "string")),
                "required", List.of("repository", "title", "body"), "additionalProperties", false);
        TestMcpClient mcp = new TestMcpClient(MCP_SCHEMA);
        ToolBroker broker = broker(action(changedSchema), mcp);
        RunApprovalEntity persistedApproval = approval("kumiko2007/hi-agent", ARGS, SCHEMA);

        assertThrows(McpSchemaMismatchException.class,
                () -> broker.executeApprovedWrite(agent(), "owner", persistedApproval, () -> {
                }));
        assertEquals(0, mcp.calls.get());
    }

    @Test
    void shouldRejectConflictingRepositoryTargetsAndSupportOwnerRepoArguments() {
        assertThrows(BusinessException.class,
                () -> McpSchema.targetRepository("{\"repository\":\"approved/repo\",\"repo\":\"outside/private\"}"));
        assertThrows(BusinessException.class,
                () -> McpSchema.targetRepository("{\"owner\":\"other\",\"repo\":\"approved/repo\"}"));
        assertThrows(BusinessException.class, () -> McpSchema.targetRepository(
                "{\"owner\":\"approved\",\"repository_url\":\"https://github.com/outside/private\"}"));
        assertEquals("kumiko2007/hi-agent",
                McpSchema.targetRepository("{\"owner\":\"KumiKo2007\",\"repo\":\"hi-agent\"}"));
    }

    private static RunApprovalEntity approval(String repository, String arguments, Map<String, Object> schema) {
        RunApprovalEntity approval = RunApprovalEntity.create("run-1",
                new RunApprovalDraft("service-1", "create_issue", "Create issue", repository, arguments, schema));
        approval.setStatus("APPROVED");
        return approval;
    }

    private static ToolBroker broker(RegisteredToolAction action, TestMcpClient mcp) {
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
                if (!action.serviceId().equals(serviceId) || !action.name().equals(actionName)) {
                    throw new BusinessException("Action not allowed");
                }
                return action;
            }

            @Override
            public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
                return List.of(action);
            }
        };
        return new ToolBroker(catalog, (serverName, userId) -> mcp,
                (serverName, userId, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
    }

    private static RegisteredToolAction action(Map<String, Object> schema) {
        return new RegisteredToolAction("service-1", "github", "create_issue", "Create GitHub issue", schema,
                ToolActionClassification.WRITE_REQUIRES_APPROVAL);
    }

    private static AgentEntity agent() {
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setToolIds(List.of("service-1"));
        return agent;
    }

    private static final class TestMcpClient implements McpClient {
        private JsonObjectSchema schema;
        private final AtomicInteger calls = new AtomicInteger();
        private ToolExecutionRequest lastRequest;

        private TestMcpClient(JsonObjectSchema schema) {
            this.schema = schema;
        }

        @Override
        public String key() {
            return "test";
        }
        @Override
        public List<ToolSpecification> listTools() {
            return List.of(ToolSpecification.builder().name("create_issue").description("Create GitHub issue")
                    .parameters(schema).build());
        }
        @Override
        public String executeTool(ToolExecutionRequest request) {
            calls.incrementAndGet();
            lastRequest = request;
            return "created";
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
