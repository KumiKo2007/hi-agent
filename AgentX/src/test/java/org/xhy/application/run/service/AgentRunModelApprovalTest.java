package org.xhy.application.run.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.transport.PresetParameter;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.tool.service.ToolActionCatalog;
import org.xhy.application.tool.service.ToolBroker;
import org.xhy.application.github.service.GitHubConnectionAppService;
import org.xhy.application.github.service.GitHubIssueApprovedActionReconciler;
import org.xhy.application.github.service.GitHubMcpToolActionAdapter;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.model.AgentWorkspaceEntity;
import org.xhy.domain.agent.model.LLMModelConfig;
import org.xhy.domain.agent.repository.AgentRepository;
import org.xhy.domain.agent.repository.AgentVersionRepository;
import org.xhy.domain.agent.repository.AgentWorkspaceRepository;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.agent.service.AgentWorkspaceDomainService;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
import org.xhy.domain.llm.repository.ModelRepository;
import org.xhy.domain.llm.repository.ProviderRepository;
import org.xhy.domain.llm.service.LLMDomainService;
import org.xhy.domain.rag.model.RagVersionDocumentEntity;
import org.xhy.domain.rag.model.RagVersionEntity;
import org.xhy.domain.rag.model.RagVersionFileEntity;
import org.xhy.domain.rag.repository.RagVersionDocumentRepository;
import org.xhy.domain.rag.repository.RagVersionFileRepository;
import org.xhy.domain.rag.repository.RagVersionRepository;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunApprovalPolicy;
import org.xhy.domain.run.model.RunTimelineEventEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.llm.LLMServiceFactory;
import org.xhy.infrastructure.llm.protocol.enums.ProviderProtocol;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.github.GitHubApiTransport;
import org.xhy.infrastructure.github.GitHubCredentialVerifier;
import org.xhy.infrastructure.github.GitHubTokenCipher;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRunModelApprovalTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ARGUMENTS = "{\"repository\":\"KumiKo2007/hi-agent\",\"title\":\"Gateway retries\",\"body\":\"The gateway retries failed requests.\"}";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
            Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string"), "body",
                    Map.of("type", "string")),
            "required", List.of("repository", "title", "body"), "additionalProperties", false);
    private static final JsonObjectSchema MCP_SCHEMA = JsonObjectSchema.builder().addStringProperty("repository")
            .addStringProperty("title").addStringProperty("body").required("repository", "title", "body")
            .additionalProperties(false).build();

    @Test
    void shouldRecordActualRetrievalAndModelUsageWithoutForwardingCredentialSentinels() {
        String sentinel = "ghp_abcdefghijklmnopqrstuvwxyz123456";
        String cookieSentinel = "cookieSentinelabcdefghijklmnopqrstuvwxyz";
        String jsonApiSentinel = "opaqueJsonApiSentinel1234567890";
        String jsonAuthSentinel = "opaqueJsonAuthSentinel1234567890";
        String jsonHeaderSentinel = "opaqueJsonHeaderSentinel1234567890";
        String jsonCookieSentinel = "opaqueJsonCookieSentinel1234567890";
        String jsonNestedCookieSentinel = "opaqueNestedCookieSentinel1234567890";
        String truncatedJsonSentinel = "opaqueTruncatedJsonSentinel1234567890";
        AtomicReference<List<ChatMessage>> captured = new AtomicReference<>();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                captured.set(request.messages());
                return ChatResponse.builder().aiMessage(new AiMessage("answer")).tokenUsage(new TokenUsage(11, 7))
                        .build();
            }
        };
        KnowledgeCitation source = citation();
        source.setContent("Release notes. api_key=" + sentinel + " headers={\"Cookie\":\"" + cookieSentinel
                + "\",\"User-Agent\":\"agentSentinelabcdefghijklmnopqrstuvwxyz\"}\n"
                + "Retrieved metadata: {\"api_key\":\"" + jsonApiSentinel + "\",\"authorization\":\"" + jsonAuthSentinel
                + "\",\"headers\":{\"User-Agent\":\"" + jsonHeaderSentinel + "\"},\"cookies\":{\"session\":\""
                + jsonCookieSentinel + "\",\"refresh\":\"" + jsonNestedCookieSentinel
                + "\"}}\nIncomplete metadata: {\"api_key\":\"" + truncatedJsonSentinel + "\"");
        source.setDocumentId("doc-1");
        source.setFileId("file-1");
        source.setKnowledgeVersionId("knowledge-version");
        AgentRunModel model = runModel(chatModel, null,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));
        RunEntity run = run();
        run.setGoal("Summarize. api_key=" + sentinel + "\nCookie: " + cookieSentinel);
        List<RunTimelineEventEntity> events = new java.util.ArrayList<>();

        RunExecution result = model.executeWithActions(run, events::add);

        String prompt = captured.get().toString();
        assertTrue(result.answer().equals("answer"));
        assertFalse(prompt.contains(sentinel));
        assertFalse(prompt.contains(cookieSentinel));
        assertFalse(prompt.contains("agentSentinelabcdefghijklmnopqrstuvwxyz"));
        assertFalse(prompt.contains(jsonApiSentinel));
        assertFalse(prompt.contains(jsonAuthSentinel));
        assertFalse(prompt.contains(jsonHeaderSentinel));
        assertFalse(prompt.contains(jsonCookieSentinel));
        assertFalse(prompt.contains(jsonNestedCookieSentinel));
        assertFalse(prompt.contains(truncatedJsonSentinel));
        assertEquals(List.of("KNOWLEDGE_RETRIEVAL", "KNOWLEDGE_SOURCE", "MODEL_CALL"),
                events.stream().map(RunTimelineEventEntity::getEventType).toList());
        assertTrue(events.stream()
                .allMatch(event -> !event.getDetailsJson().contains(sentinel)
                        && !event.getDetailsJson().contains(cookieSentinel)
                        && !event.getDetailsJson().contains("agentSentinelabcdefghijklmnopqrstuvwxyz")));
        com.fasterxml.jackson.databind.JsonNode usage;
        try {
            usage = JSON.readTree(events.get(2).getDetailsJson());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertEquals(11, usage.path("inputTokens").asInt());
        assertEquals(7, usage.path("outputTokens").asInt());
        assertEquals("AVAILABLE", usage.path("usageStatus").asText());
        assertTrue(usage.path("durationMs").asLong() >= 0);
    }

    @Test
    void shouldScrubReadOnlyToolArgumentsAndResultsBeforeTheNextModelCall() {
        String argumentSentinel = "argumentSentinelabcdefghijklmnopqrstuvwxyz";
        String resultSentinel = "toolCookieSentinelabcdefghijklmnopqrstuvwxyz";
        Map<String, Object> readSchema = Map.of("type", "object", "properties",
                Map.of("query", Map.of("type", "string")), "required", List.of("query"), "additionalProperties", false);
        JsonObjectSchema mcpReadSchema = JsonObjectSchema.builder().addStringProperty("query").required("query")
                .additionalProperties(false).build();
        RegisteredToolAction readAction = new RegisteredToolAction("service-1", "docs", "search_docs",
                "Search documents", readSchema, ToolActionClassification.READ_ONLY);
        ToolSpecification spec = ToolSpecification.builder().name("search_docs").description("Search documents")
                .parameters(mcpReadSchema).build();
        TestMcpClient mcp = new TestMcpClient(spec, "Cookie: " + resultSentinel);
        ToolBroker broker = new ToolBroker(catalog(readAction), (server, user) -> mcp);
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:search_docs".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        ToolExecutionRequest readRequest = ToolExecutionRequest.builder().id("read-1").name(alias)
                .arguments("{\"query\":\"api_key=" + argumentSentinel + "\"}").build();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<ChatMessage>> secondRequestMessages = new AtomicReference<>();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (calls.getAndIncrement() == 0) {
                    return ChatResponse.builder().aiMessage(AiMessage.from(readRequest)).build();
                }
                secondRequestMessages.set(request.messages());
                return ChatResponse.builder().aiMessage(new AiMessage("done")).build();
            }
        };
        AgentRunModel model = runModel(chatModel, broker);
        List<RunTimelineEventEntity> events = new java.util.ArrayList<>();

        RunExecution result = model.executeWithActions(run(), events::add);

        assertEquals("done", result.answer());
        assertEquals(2, calls.get());
        assertFalse(secondRequestMessages.get().toString().contains(argumentSentinel));
        assertFalse(secondRequestMessages.get().toString().contains(resultSentinel));
        assertTrue(events.stream().noneMatch(event -> event.getDetailsJson().contains(argumentSentinel)
                || event.getDetailsJson().contains(resultSentinel)));
    }

    @Test
    void shouldTurnModelWriteProposalIntoApprovalWithoutCallingMcp() {
        TestMcpClient mcp = new TestMcpClient();
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
                return action();
            }

            @Override
            public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
                return List.of(action());
            }
        };
        ToolBroker broker = new ToolBroker(catalog, (server, user) -> mcp, (server, user, targetRepository) -> mcp,
                List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        ChatModel chatModel = new ProposingChatModel(
                ToolExecutionRequest.builder().id("request-1").name(alias).arguments(ARGUMENTS).build());
        AgentRunModel runModel = runModel(chatModel, broker);

        RunExecution result = runModel.executeWithActions(runWithTargetRepository());

        assertTrue(result.isAwaitingApproval());
        assertEquals("service-1", result.approvalRequest().serviceId());
        assertEquals("create_issue", result.approvalRequest().actionName());
        assertEquals("kumiko2007/hi-agent", result.approvalRequest().targetRepository());
        assertTrue(result.approvalRequest().arguments()
                .contains("The gateway retries failed requests.\\n\\n---\\n\\n### Sources"));
        assertTrue(result.approvalRequest().arguments().contains("[S1] `release-notes.md`, page 2"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldStopBeforeApprovalWhenPinnedEvidenceIsInsufficient() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction action = action();
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
        ToolBroker broker = new ToolBroker(catalog, (server, user) -> mcp, (server, user, targetRepository) -> mcp,
                List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        RagVersionEntity version = new RagVersionEntity();
        version.setId("knowledge-version");
        version.setUserId("owner");
        version.setSearchReady(true);
        RagVersionDocumentEntity document = new RagVersionDocumentEntity();
        document.setId("doc-1");
        document.setRagVersionId("knowledge-version");
        document.setRagVersionFileId("file-1");
        document.setContent("Please create an issue for the project and add it to the repository.");
        KnowledgeSnapshotRetrievalService knowledge = new KnowledgeSnapshotRetrievalService(proxy(
                RagVersionRepository.class,
                method -> method.getName().equals("selectOne") ? version : defaultValue(method.getReturnType())),
                proxy(RagVersionDocumentRepository.class,
                        method -> method.getName().equals("selectList")
                                ? List.of(document)
                                : defaultValue(method.getReturnType())),
                proxy(RagVersionFileRepository.class,
                        method -> method.getName().equals("selectList")
                                ? List.of(new RagVersionFileEntity())
                                : defaultValue(method.getReturnType())));
        AgentRunModel runModel = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(ARGUMENTS).build()),
                broker, knowledge);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> runModel.executeWithActions(runWithTargetRepository()));
        assertTrue(exception.getMessage().contains("资料依据不足"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareIssueWhenEvidenceSupportsOnlyTheFirstPartOfTheGoal() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction action = action();
        ToolActionCatalog catalog = catalog(action);
        ToolBroker broker = new ToolBroker(catalog, (server, user) -> mcp, (server, user, targetRepository) -> mcp,
                List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");

        RagVersionEntity version = new RagVersionEntity();
        version.setId("knowledge-version");
        version.setUserId("owner");
        version.setSearchReady(true);
        RagVersionDocumentEntity document = new RagVersionDocumentEntity();
        document.setId("doc-migration-only");
        document.setRagVersionId("knowledge-version");
        document.setRagVersionFileId("file-1");
        document.setContent("The database migration follows the existing backup procedure.");
        KnowledgeSnapshotRetrievalService knowledge = new KnowledgeSnapshotRetrievalService(proxy(
                RagVersionRepository.class,
                method -> method.getName().equals("selectOne") ? version : defaultValue(method.getReturnType())),
                proxy(RagVersionDocumentRepository.class,
                        method -> method.getName().equals("selectList")
                                ? List.of(document)
                                : defaultValue(method.getReturnType())),
                proxy(RagVersionFileRepository.class,
                        method -> method.getName().equals("selectList")
                                ? List.of(new RagVersionFileEntity())
                                : defaultValue(method.getReturnType())));
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(ARGUMENTS).build()),
                broker, knowledge);
        RunEntity run = runWithTargetRepository();
        run.setGoal("database migration, token rotation, recovery");

        BusinessException exception = assertThrows(BusinessException.class, () -> model.executeWithActions(run));
        assertTrue(exception.getMessage().contains("资料依据不足"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenTheGeneratedDraftAddsUnsupportedClaims() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction action = action();
        ToolBroker broker = new ToolBroker(catalog(action), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String unsupportedClaims = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Zero downtime migration\","
                + "\"body\":\"Token recovery is guaranteed without downtime.\"}";
        AgentRunModel model = runModel(new ProposingChatModel(
                ToolExecutionRequest.builder().id("request-1").name(alias).arguments(unsupportedClaims).build()),
                broker);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));
        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenTheDraftReversesCitedClaimPolarity() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction action = action();
        ToolBroker broker = new ToolBroker(catalog(action), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String oppositePolarity = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry\","
                + "\"body\":\"The gateway does not retry failed requests.\"}";
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(oppositePolarity).build()),
                broker);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenWithoutDelayIsMisreadAsGatewayNegation() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String oppositePolarity = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry\","
                + "\"body\":\"The gateway does not retry failed requests.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway retries failed requests without delay.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(oppositePolarity).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldSplitStandaloneChineseContrastBeforePreparingApproval() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String oppositePolarity = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry\","
                + "\"body\":\"The gateway does not retry failed requests.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway retries failed requests但缓存不会提前过期");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(oppositePolarity).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldPreservePositiveGatewayPolarityInChineseNotOnlyEvidence() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String arguments = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"网关重试失败请求\",\"body\":\"网关重试失败请求\"}";
        KnowledgeCitation source = citation();
        source.setContent("不但网关重试失败请求，而且缓存可用");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(arguments).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        RunExecution execution = model.executeWithActions(runWithTargetRepository());

        assertTrue(execution.isAwaitingApproval());
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenNoMonitoringIsMisreadAsGatewayNegation() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String oppositePolarity = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry\","
                + "\"body\":\"The gateway retry is not slow.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway retry is slow with no monitoring.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(oppositePolarity).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenPredicateNoContradictsTheDraft() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String positiveClaim = "{\"repository\":\"KumiKo2007/hi-agent\","
                + "\"title\":\"Gateway retries\",\"body\":\"The gateway has retries.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway has no retries.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(positiveClaim).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenNoLongerContradictsTheDraft() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String positiveClaim = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry\","
                + "\"body\":\"The gateway retries failed requests.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway no longer retries failed requests.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(positiveClaim).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenWithoutRetriesContradictsTheDraft() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String positiveClaim = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry\","
                + "\"body\":\"The gateway retries failed requests.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway operates without retries for failed requests.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(positiveClaim).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenChineseDeBuDeIsMisreadAsNegation() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String contradictoryClaim = "{\"repository\":\"KumiKo2007/hi-agent\","
                + "\"title\":\"网关不得不重试请求\",\"body\":\"网关不重试请求\"}";
        KnowledgeCitation source = citation();
        source.setContent("网关不得不重试请求");
        AgentRunModel model = runModel(
                new ProposingChatModel(ToolExecutionRequest.builder().id("request-1").name(alias)
                        .arguments(contradictoryClaim).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenGatewayAvoidsRetries() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String positiveClaim = "{\"repository\":\"KumiKo2007/hi-agent\","
                + "\"title\":\"Gateway retry\",\"body\":\"The gateway retries failed requests.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway avoids retries for failed requests.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(positiveClaim).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenGatewayProvidesNoRetrySupport() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String positiveClaim = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry support\","
                + "\"body\":\"The gateway provides retry support.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway provides no retry support.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(positiveClaim).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenGatewayLacksRetrySupport() {
        TestMcpClient mcp = new TestMcpClient();
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String positiveClaim = "{\"repository\":\"KumiKo2007/hi-agent\"," + "\"title\":\"Gateway retry support\","
                + "\"body\":\"The gateway has retry support.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway lacks retry support.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(positiveClaim).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenNotLackingEvidenceIsDraftedAsLacking() {
        assertDraftRejectedWithoutMcpWrite("The gateway is not lacking retry support for failed requests.",
                "The gateway is lacking retry support for failed requests.");
    }

    @Test
    void shouldNotPrepareApprovalWhenPositiveRetryEvidenceIsDraftedAsRefusalOrInability() {
        List<String[]> cases = List.of(
                new String[]{"Gateways do not avoid retries for failed requests.", "Gateways",
                        "Gateways avoid retries for failed requests."},
                new String[]{"The gateway is not avoiding retries for failed requests.", "Gateway retry",
                        "The gateway is avoiding retries for failed requests."},
                new String[]{"The gateway is not refusing to retry failed requests.", "Gateway retry",
                        "The gateway is refusing to retry failed requests."},
                new String[]{"The gateway is not unable to retry failed requests.", "Gateway retry",
                        "The gateway is unable to retry failed requests."},
                new String[]{"The gateway is not stopping retries for failed requests.", "Gateway retry",
                        "The gateway is stopping retries for failed requests."},
                new String[]{"The gateway is not refraining from retrying failed requests", "Gateway retrying",
                        "The gateway is refraining from retrying failed requests."});

        for (String[] testCase : cases) {
            assertDraftRejectedWithoutMcpWrite(testCase[0], testCase[1], testCase[2]);
        }
    }

    @Test
    void shouldNotTreatRetryConfigurationComplementsAsNoRetryEvidence() {
        for (String detail : List.of("with no retry delay", "without retry latency", "without a retry timeout",
                "without delay", "without a retry backoff", "without a retry back-off")) {
            assertDraftRejectedWithoutMcpWrite("The gateway retries failed requests " + detail + ".",
                    "The gateway does not retry failed requests.");
        }
    }

    @Test
    void shouldNotPrepareApprovalForRetryRefusalOrUnavailableRetryCapability() {
        List<String[]> cases = List.of(
                new String[]{"The gateway refuses to retry failed requests.", "The gateway retries failed requests."},
                new String[]{"The gateway stops retrying failed requests.", "The gateway is retrying failed requests."},
                new String[]{"The gateway refrains from retrying failed requests.",
                        "The gateway is retrying failed requests."},
                new String[]{"The gateway supports no retries for failed requests.",
                        "The gateway retries failed requests."},
                new String[]{"The gateway offers no retries for failed requests.",
                        "The gateway retries failed requests."},
                new String[]{"The gateway operates with no retries for failed requests.",
                        "The gateway retries failed requests."},
                new String[]{"The gateway is unable to retry failed requests.",
                        "The gateway retries failed requests."});

        for (String[] testCase : cases) {
            assertDraftRejectedWithoutMcpWrite(testCase[0], testCase[1]);
        }
    }

    @Test
    void shouldNotPrepareApprovalFromBareStopOrRefrainEvidence() {
        for (String evidence : List.of("Workers stop retrying failed requests.",
                "Workers refrain from retrying failed requests.")) {
            assertDraftRejectedWithoutMcpWrite(evidence, "Workers", "Workers are retrying failed requests.");
        }
    }

    @Test
    void shouldNotPrepareApprovalFromInterveningRetryActionEvidence() {
        for (String evidence : List.of("The gateway lacks the ability to retry failed requests.",
                "The gateway stops attempts to retry failed requests.",
                "The gateway refrains from initiating retries for failed requests.")) {
            assertDraftRejectedWithoutMcpWrite(evidence, "Gateway retry", "The gateway retries failed requests.");
        }
    }

    @Test
    void shouldNotPrepareApprovalWhenPositiveNotFailingToRetryEvidenceIsDraftedAsNegative() {
        assertDraftRejectedWithoutMcpWrite("The gateway does not fail to retry failed requests.", "Gateway retry",
                "The gateway does not retry failed requests.");
    }

    @Test
    void shouldKeepWithNoDelayFromNegatingRetriesInApprovalEvidence() {
        assertDraftRejectedWithoutMcpWrite("The gateway retries failed requests with no delay.",
                "The gateway does not retry failed requests.");
    }

    private static void assertDraftRejectedWithoutMcpWrite(String evidence, String draftBody) {
        assertDraftRejectedWithoutMcpWrite(evidence, "Gateway retry", draftBody);
    }

    private static void assertDraftRejectedWithoutMcpWrite(String evidence, String title, String draftBody) {
        TestMcpClient mcp = new TestMcpClient();
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        ObjectNode arguments = JSON.createObjectNode();
        arguments.put("repository", "KumiKo2007/hi-agent");
        arguments.put("title", title);
        arguments.put("body", draftBody);
        KnowledgeCitation source = citation();
        source.setContent(evidence);
        ToolBroker broker = new ToolBroker(catalog(action()), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        AgentRunModel model = runModel(
                new ProposingChatModel(ToolExecutionRequest.builder().id("request-1").name(alias)
                        .arguments(arguments.toString()).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()), evidence);

        assertTrue(exception.getMessage().contains("未支持生成草稿"), evidence);
        assertEquals(0, mcp.writeCalls.get(), evidence);
    }

    @Test
    void shouldNotPrepareApprovalWhenFactualTitleReversesCitedPolarity() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction action = action();
        ToolBroker broker = new ToolBroker(catalog(action), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String oppositeTitle = "{\"repository\":\"KumiKo2007/hi-agent\","
                + "\"title\":\"The gateway retries failed requests.\","
                + "\"body\":\"The cache expires after 30 days.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway does not retry failed requests. The cache expires after 30 days.");
        AgentRunModel model = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(oppositeTitle).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotPrepareApprovalWhenDraftReversesTheCitedEntityRelationship() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction action = action();
        ToolBroker broker = new ToolBroker(catalog(action), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        String reversedRelationship = "{\"repository\":\"KumiKo2007/hi-agent\","
                + "\"title\":\"Gateway token rotation\"," + "\"body\":\"Token rotation blocks the gateway.\"}";
        KnowledgeCitation source = citation();
        source.setContent("The gateway blocks token rotation.");
        AgentRunModel model = runModel(
                new ProposingChatModel(ToolExecutionRequest.builder().id("request-1").name(alias)
                        .arguments(reversedRelationship).build()),
                broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(source)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> model.executeWithActions(runWithTargetRepository()));

        assertTrue(exception.getMessage().contains("未支持生成草稿"));
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void preservesTheApprovedIntentMarkerSoReconciliationFindsAnAlreadyCreatedIssue() throws Exception {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction action = new RegisteredToolAction("service-1", "github", "create_issue", "Create issue",
                SCHEMA, ToolActionClassification.WRITE_REQUIRES_APPROVAL);
        ToolBroker broker = new ToolBroker(catalog(action), (server, user) -> mcp,
                (server, user, targetRepository) -> mcp, List.of(new GitHubMcpToolActionAdapter()));
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        RunEntity run = run();
        run.setTargetRepository("kumiko2007/hi-agent");

        RunExecution execution = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(ARGUMENTS).build()),
                broker).executeWithActions(run);
        RunApprovalDraft draft = execution.approvalRequest();
        RunApprovalEntity approval = RunApprovalEntity.create(run.getId(), draft);
        approval.decide(true, "owner");
        approval.requireReconciliation();

        JsonNode arguments = JSON.readTree(approval.getArguments());
        String expectedMarker = "<!-- hi-agent-action-intent:" + approval.getId() + " -->";
        assertTrue(arguments.path("body").asText().contains(expectedMarker));

        String token = "github_pat_abcdefghijklmnopqrstuvwxyz123456";
        GitHubTokenCipher cipher = new GitHubTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
        GitHubConnection connection = new GitHubConnection("owner", "github-user", "github-login",
                cipher.encrypt("owner", token), List.of("kumiko2007/hi-agent"));
        GitHubConnectionRepository connectionRepository = new GitHubConnectionRepository() {
            @Override
            public Optional<GitHubConnection> findByOwner(String ownerId) {
                return "owner".equals(ownerId) ? Optional.of(connection) : Optional.empty();
            }
            @Override
            public Optional<GitHubConnection> findByOwnerForUpdate(String ownerId) {
                return findByOwner(ownerId);
            }
            @Override
            public void save(GitHubConnection value) {
            }
            @Override
            public void deleteByOwner(String ownerId) {
            }
        };
        GitHubConnectionAppService githubConnections = new GitHubConnectionAppService(connectionRepository,
                (credential, repositories) -> new GitHubCredentialVerifier.Identity("github-user", "github-login"),
                cipher);
        AtomicReference<String> searchPath = new AtomicReference<>();
        AtomicInteger githubReads = new AtomicInteger();
        GitHubApiTransport github = (credential, path) -> {
            assertEquals(token, credential);
            searchPath.set(path);
            githubReads.incrementAndGet();
            ObjectNode issue = JSON.createObjectNode();
            issue.put("body", arguments.path("body").asText());
            issue.put("repository_url", "https://api.github.com/repos/kumiko2007/hi-agent");
            issue.put("html_url", "https://github.com/kumikO2007/hi-agent/issues/42");
            return JSON.createObjectNode().set("items", JSON.createArrayNode().add(issue));
        };

        ApprovedActionReconciliation reconciliation = new GitHubIssueApprovedActionReconciler(githubConnections, github)
                .reconcile(run, approval);

        assertEquals(approval.getId(), draft.intentId());
        assertTrue(searchPath.get().contains("hi-agent-action-intent%3A" + approval.getId()));
        assertEquals(ApprovedActionReconciliation.Outcome.VERIFIED_CREATED, reconciliation.outcome());
        assertEquals("https://github.com/kumikO2007/hi-agent/issues/42", reconciliation.externalResourceId());
        assertEquals(1, githubReads.get());
        assertEquals(0, mcp.writeCalls.get(), "reconciliation must find the prior write without another create");
    }

    @Test
    void shouldHonorCreatorPreauthorizationOnlyForExplicitlyClassifiedWrites() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction preauthorized = new RegisteredToolAction("service-1", "test-server", "create_issue",
                "Create issue", SCHEMA, ToolActionClassification.WRITE_REQUIRES_APPROVAL, true, true);
        RunExecution result = proposedWrite(preauthorized, mcp, run());

        assertFalse(result.isAwaitingApproval());
        assertEquals("create_issue", result.approvalRequest().actionName());
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldRequireApprovalWhenUserTightensTheRunPolicy() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction preauthorized = new RegisteredToolAction("service-1", "test-server", "create_issue",
                "Create issue", SCHEMA, ToolActionClassification.WRITE_REQUIRES_APPROVAL, true, true);
        RunEntity run = run();
        run.setApprovalPolicy(RunApprovalPolicy.REQUIRE_APPROVAL_FOR_ALL_WRITES.name());
        RunExecution result = proposedWrite(preauthorized, mcp, run);

        assertTrue(result.isAwaitingApproval());
        assertEquals(0, mcp.writeCalls.get());
    }

    @Test
    void shouldNotHonorPreauthorizationForAnUnclassifiedAction() {
        TestMcpClient mcp = new TestMcpClient();
        RegisteredToolAction unknown = new RegisteredToolAction("service-1", "test-server", "create_issue",
                "Create issue", SCHEMA, ToolActionClassification.WRITE_REQUIRES_APPROVAL, false, true);
        RunExecution result = proposedWrite(unknown, mcp, run());

        assertTrue(result.isAwaitingApproval());
        assertEquals(0, mcp.writeCalls.get());
    }

    private static RunExecution proposedWrite(RegisteredToolAction action, TestMcpClient mcp, RunEntity run) {
        ToolActionCatalog catalog = catalog(action);
        String alias = "mcp_" + UUID.nameUUIDFromBytes("service-1:create_issue".getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        AgentRunModel runModel = runModel(
                new ProposingChatModel(
                        ToolExecutionRequest.builder().id("request-1").name(alias).arguments(ARGUMENTS).build()),
                new ToolBroker(catalog, (server, user) -> mcp));
        return runModel.executeWithActions(run);
    }

    private static ToolActionCatalog catalog(RegisteredToolAction action) {
        return new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity ignored, String userId, String serviceId,
                    String actionName) {
                return action;
            }

            @Override
            public List<RegisteredToolAction> list(AgentEntity ignored, String userId) {
                return List.of(action);
            }
        };
    }

    private static RunEntity run() {
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "Create a GitHub issue", null, null,
                "model-1", ProviderProtocol.OPENAI.name());
        run.setKnowledgeVersionId("knowledge-version");
        return run;
    }

    private static RunEntity runWithTargetRepository() {
        RunEntity run = run();
        run.setTargetRepository("kumiko2007/hi-agent");
        return run;
    }

    private static AgentRunModel runModel(ChatModel chatModel, ToolBroker broker) {
        return runModel(chatModel, broker,
                (version, query, user) -> new KnowledgeRetrievalResult(version, true, "evidence", List.of(citation())));
    }

    private static AgentRunModel runModel(ChatModel chatModel, ToolBroker broker, RunKnowledgeRetriever knowledge) {
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setUserId("owner");
        agent.setEnabled(true);
        agent.setSystemPrompt("Use registered tools when needed.");
        AgentWorkspaceEntity workspace = new AgentWorkspaceEntity("agent-1", "owner", new LLMModelConfig());

        AgentRepository agents = proxy(AgentRepository.class,
                method -> method.getName().equals("selectByIds")
                        ? List.of(agent)
                        : defaultValue(method.getReturnType()));
        AgentWorkspaceRepository workspaces = proxy(AgentWorkspaceRepository.class,
                method -> method.getName().equals("selectOne") ? workspace : defaultValue(method.getReturnType()));
        AgentVersionRepository versions = proxy(AgentVersionRepository.class,
                method -> defaultValue(method.getReturnType()));
        AgentDomainService agentService = new AgentDomainService(agents, versions, workspaces, null);
        AgentWorkspaceDomainService workspaceService = new AgentWorkspaceDomainService(workspaces, agentService,
                agents);

        ModelEntity model = new ModelEntity();
        model.setId("model-1");
        model.setProviderId("provider-1");
        model.setStatus(true);
        ProviderEntity provider = new ProviderEntity();
        provider.setId("provider-1");
        provider.setStatus(true);
        provider.setIsOfficial(true);
        provider.setProtocol(ProviderProtocol.OPENAI);
        ModelRepository models = proxy(ModelRepository.class,
                method -> method.getName().equals("selectById") ? model : defaultValue(method.getReturnType()));
        ProviderRepository providers = proxy(ProviderRepository.class,
                method -> method.getName().equals("selectOne") ? provider : defaultValue(method.getReturnType()));
        LLMDomainService llmService = new LLMDomainService(providers, models, event -> {
        });
        LLMServiceFactory modelFactory = new LLMServiceFactory() {
            @Override
            public ChatModel getStrandClient(ProviderEntity ignoredProvider, ModelEntity ignoredModel) {
                return chatModel;
            }
        };
        return new AgentRunModel(agentService, workspaceService, llmService, modelFactory, broker, knowledge);
    }

    private static KnowledgeCitation citation() {
        KnowledgeCitation citation = new KnowledgeCitation();
        citation.setFileName("release-notes.md");
        citation.setPage(2);
        citation.setContent("The gateway retries failed requests.");
        return citation;
    }

    private static RegisteredToolAction action() {
        return new RegisteredToolAction("service-1", "github", "create_issue", "Create issue", SCHEMA,
                ToolActionClassification.WRITE_REQUIRES_APPROVAL);
    }

    private static <T> T proxy(Class<T> type, Function<java.lang.reflect.Method, Object> handler) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> handler.apply(method)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }

    private static final class ProposingChatModel implements ChatModel {
        private final ToolExecutionRequest request;

        private ProposingChatModel(ToolExecutionRequest request) {
            this.request = request;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            assertTrue(chatRequest.toolSpecifications().stream().anyMatch(spec -> request.name().equals(spec.name())));
            return ChatResponse.builder().aiMessage(AiMessage.from(request)).build();
        }
    }

    private static final class TestMcpClient implements McpClient {
        private final AtomicInteger writeCalls = new AtomicInteger();
        private final ToolSpecification tool;
        private final String result;

        private TestMcpClient() {
            this(ToolSpecification.builder().name("create_issue").description("Create issue").parameters(MCP_SCHEMA)
                    .build(), "created");
        }

        private TestMcpClient(ToolSpecification tool, String result) {
            this.tool = tool;
            this.result = result;
        }

        @Override
        public String key() {
            return "github";
        }
        @Override
        public List<ToolSpecification> listTools() {
            return List.of(tool);
        }
        @Override
        public String executeTool(ToolExecutionRequest request) {
            writeCalls.incrementAndGet();
            return result;
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
