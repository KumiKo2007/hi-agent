package org.xhy.application.run.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
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
import org.xhy.application.github.service.GitHubMcpToolActionAdapter;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.tool.service.McpActionClientProvider;
import org.xhy.application.tool.service.McpClientFactory;
import org.xhy.application.tool.service.McpToolExecutionException;
import org.xhy.application.tool.service.ToolActionCatalog;
import org.xhy.application.tool.service.ToolBroker;
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
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.model.RunTimelineEventEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.domain.run.repository.RunTimelineRepository;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.llm.LLMServiceFactory;
import org.xhy.infrastructure.llm.protocol.enums.ProviderProtocol;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the checked-in v1 evaluation corpus against deterministic application collaborators. */
class VersionedRunEvaluationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CORPUS_RESOURCE = "/evaluations/v1.json";
    private static final Map<String, Object> ISSUE_SCHEMA = Map.of("type", "object", "properties",
            Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string"), "body",
                    Map.of("type", "string")),
            "required", List.of("repository", "title", "body"), "additionalProperties", false);
    private static final JsonObjectSchema MCP_ISSUE_SCHEMA = JsonObjectSchema.builder().addStringProperty("repository")
            .addStringProperty("title").addStringProperty("body").required("repository", "title", "body")
            .additionalProperties(false).build();

    @Test
    void versionedCorpusShouldPassAndWriteComparablePerRunReport() throws Exception {
        Corpus corpus = readCorpus();
        assertEquals("v1", corpus.version());
        assertEquals("fixture-usd-v1", corpus.rateCard().version());
        assertEquals(7, corpus.cases().size());

        List<CaseResult> results = new ArrayList<>();
        for (EvaluationCase evaluationCase : corpus.cases()) {
            results.add(runCase(corpus, evaluationCase));
        }
        Map<String, Object> summary = summarize(results, corpus.rateCard());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("evaluationId", java.util.UUID.randomUUID().toString());
        report.put("generatedAt", Instant.now().toString());
        report.put("corpusVersion", corpus.version());
        report.put("rateCard", corpus.rateCard());
        report.put("summary", summary);
        report.put("cases", results);

        String previousPath = System.getProperty("run.evaluation.previousReport");
        if (previousPath != null && !previousPath.isBlank()) {
            report.put("comparison", compare(summary, Path.of(previousPath), corpus));
        }

        Path output = Path.of("target", "evaluations", "run-evaluation-v1.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        List<CaseResult> failed = results.stream().filter(result -> !result.passed()).toList();
        assertTrue(failed.isEmpty(),
                () -> "Evaluation failures were saved to " + output + ": "
                        + failed.stream().map(result -> result.caseId() + " run=" + result.runId() + " step="
                                + result.stepId() + " reason=" + result.failure()).toList());
        assertEquals(1.0, ((Number) summary.get("passRate")).doubleValue());
        assertTrue(((Number) summary.get("activeDurationMs")).longValue() >= 0);
        assertEquals(416, ((Number) summary.get("totalTokens")).intValue());
        assertEquals(284, ((Number) summary.get("inputTokens")).intValue());
        assertEquals(132, ((Number) summary.get("outputTokens")).intValue());
        assertEquals("0.00008120", summary.get("costUsd"));
    }

    @Test
    void reportComparisonShouldUseStableAggregateMetrics() throws Exception {
        Corpus corpus = readCorpus();
        Map<String, Object> current = Map.of("totalCases", 7, "passedCases", 6, "passRate", 6.0 / 7.0,
                "activeDurationMs", 140, "inputTokens", 260, "outputTokens", 120, "totalTokens", 380, "costUsd",
                new BigDecimal("0.00007400"));
        Map<String, Object> previous = Map.of("schemaVersion", 1, "evaluationId", "previous-run", "corpusVersion",
                corpus.version(), "rateCard", Map.of("version", corpus.rateCard().version()), "summary",
                Map.of("totalCases", 7, "passedCases", 7, "passRate", 1.0, "activeDurationMs", 100, "inputTokens", 240,
                        "outputTokens", 100, "totalTokens", 340, "costUsd", "0.00006400"));
        Path prior = Files.createTempFile("run-evaluation-previous", ".json");
        try {
            JSON.writeValue(prior.toFile(), previous);
            JsonNode comparison = JSON.valueToTree(compare(current, prior, corpus));
            assertEquals("previous-run", comparison.path("evaluationId").asText());
            assertEquals(-1.0 / 7.0, comparison.path("passRateDelta").asDouble(), 0.000001);
            assertEquals(40, comparison.path("activeDurationDeltaMs").asLong());
            assertEquals(40, comparison.path("totalTokenDelta").asInt());
            assertEquals("0.00001000", comparison.path("costDeltaUsd").asText());
        } finally {
            Files.deleteIfExists(prior);
        }
    }

    @Test
    void activeDurationShouldExcludeTimeBetweenWorkerAttempts() {
        AtomicLong clock = new AtomicLong();
        ActiveDuration activeDuration = new ActiveDuration(clock::get);

        activeDuration.measure(() -> clock.addAndGet(Duration.ofMillis(3).toNanos()));
        clock.addAndGet(Duration.ofHours(8).toNanos());
        activeDuration.measure(() -> clock.addAndGet(Duration.ofMillis(2).toNanos()));

        assertEquals(5, activeDuration.milliseconds());
    }

    private CaseResult runCase(Corpus corpus, EvaluationCase evaluationCase) {
        RunEntity run = RunEntity.create("evaluation-owner", "evaluation-session", "evaluation-agent",
                evaluationCase.goal(), null, null, "evaluation-model", ProviderProtocol.OPENAI.name());
        run.setTargetRepository(evaluationCase.targetRepository());
        run.setKnowledgeVersionId("evaluation-knowledge-v1");
        RunStepEntity step = RunStepEntity.modelCall(run.getId());
        EvaluationStore memory = new EvaluationStore(run, step);
        RunStore store = memory.proxy();
        List<RunTimelineEventEntity> events = new ArrayList<>();
        RunTimelineRepository timeline = new InMemoryTimeline(events);
        AtomicInteger mcpOpens = new AtomicInteger();
        AtomicInteger externalWrites = new AtomicInteger();
        RunWorker worker = worker(corpus, evaluationCase, run, store, timeline, mcpOpens, externalWrites);

        // Time each durable worker attempt separately; pauses between attempts are outside the measured intervals.
        ActiveDuration activeDuration = new ActiveDuration(System::nanoTime);
        activeDuration.measure(() -> worker.process(run.getId()));
        long activeDurationMs = activeDuration.milliseconds();

        List<String> failures = checkCase(evaluationCase, run, step, memory, events, mcpOpens, externalWrites);
        Usage usage = usage(events);
        check(failures, evaluationCase.inputTokens() == usage.inputTokens(), "input token usage is missing or changed");
        check(failures, evaluationCase.outputTokens() == usage.outputTokens(),
                "output token usage is missing or changed");
        BigDecimal cost = cost(usage.inputTokens(), usage.outputTokens(), corpus.rateCard());
        return new CaseResult(evaluationCase.id(), evaluationCase.scenario(), failures.isEmpty(), run.getId(),
                step.getId(), run.getStatus(), activeDurationMs, usage.inputTokens(), usage.outputTokens(),
                usage.modelNames(), cost, failures.isEmpty() ? null : String.join("; ", failures),
                failures.isEmpty() ? null : run.getId(), failures.isEmpty() ? null : step.getId());
    }

    private RunWorker worker(Corpus corpus, EvaluationCase evaluationCase, RunEntity run, RunStore store,
            RunTimelineRepository timeline, AtomicInteger mcpOpens, AtomicInteger externalWrites) {
        if ("TOOL_FAILURE".equals(evaluationCase.scenario())) {
            RunApprovalDraft draft = approvalDraft(evaluationCase);
            RunModel model = new RunModel() {
                @Override
                public String execute(RunEntity ignored) {
                    throw new AssertionError("action-aware path required");
                }

                @Override
                public RunExecution executeWithActions(RunEntity activeRun, RunTimelineSink sink) {
                    sink.append(RunTimelineEvents.modelCall(activeRun.getId(), null, "evaluation-fixture-v1",
                            evaluationCase.inputTokens(), evaluationCase.outputTokens(), true, 1, true));
                    return RunExecution.preauthorizedWrite(draft);
                }
            };
            return new RunWorker(store, model, (activeRun, approval) -> {
                externalWrites.incrementAndGet();
                throw new McpToolExecutionException();
            }, (activeRun, approval) -> ApprovedActionReconciliation.unknown("fixture"), timeline);
        }

        KnowledgeRetrievalResult evidence = evidence(corpus, evaluationCase);
        ChatModel chatModel = scriptedChatModel(evaluationCase);
        ToolBroker broker = toolBroker(evaluationCase, mcpOpens);
        AgentRunModel model = agentRunModel(chatModel, broker, evidence);
        return new RunWorker(store, model, (activeRun, approval) -> {
            externalWrites.incrementAndGet();
            throw new AssertionError("a proposed write must not execute before owner approval");
        }, (activeRun, approval) -> ApprovedActionReconciliation.unknown("fixture"), timeline);
    }

    private List<String> checkCase(EvaluationCase evaluationCase, RunEntity run, RunStepEntity step,
            EvaluationStore memory, List<RunTimelineEventEntity> events, AtomicInteger mcpOpens,
            AtomicInteger externalWrites) {
        List<String> failures = new ArrayList<>();
        check(failures, evaluationCase.expectedStatus().equals(run.getStatus()),
                "expected status " + evaluationCase.expectedStatus() + " but got " + run.getStatus());
        check(failures, step.getId() != null && run.getId().equals(step.getRunId()), "run step identity missing");

        RunApprovalEntity approval = memory.approval();
        switch (evaluationCase.scenario()) {
            case "CITATION" -> {
                check(failures, approval != null, "grounded issue did not reach owner approval");
                if (approval != null) {
                    JsonNode issue = parseIssue(approval.getArguments(), failures);
                    String body = issue.path("body").asText();
                    check(failures, body.contains("### Sources") && body.contains("[S1]"),
                            "approval body is missing its source citation");
                    check(failures, body.contains(citationFileName()),
                            "approval body does not identify the cited file");
                }
            }
            case "ISSUE_FIELDS" -> {
                check(failures, approval != null, "issue proposal did not reach owner approval");
                if (approval != null) {
                    JsonNode issue = parseIssue(approval.getArguments(), failures);
                    String actualTitle = issue.path("title").asText();
                    String actualBody = issue.path("body").asText();
                    int sourcesStart = actualBody.indexOf("\n\n---\n\n### Sources");
                    String proposedBody = sourcesStart < 0 ? actualBody : actualBody.substring(0, sourcesStart);
                    check(failures, evaluationCase.issue().get("title").equals(actualTitle),
                            "issue title differs from the corpus expectation");
                    check(failures, evaluationCase.issue().get("body").equals(proposedBody),
                            "issue body differs from the corpus expectation");
                }
            }
            case "TARGET_REPOSITORY" -> {
                check(failures, approval != null, "targeted issue did not reach owner approval");
                if (approval != null) {
                    check(failures, run.getTargetRepository().equals(approval.getTargetRepository()),
                            "approval target differs from the run target");
                    check(failures, run.getTargetRepository().equals(targetFrom(approval.getArguments())),
                            "issue arguments target differs from the run target");
                }
            }
            case "TOOL_SELECTION" -> {
                check(failures, approval != null, "selected write action did not reach owner approval");
                if (approval != null) {
                    check(failures, evaluationCase.availableActions().size() > 1,
                            "tool selection case must expose competing actions");
                    check(failures, "service-1".equals(approval.getServiceId()), "wrong service was selected");
                    check(failures, evaluationCase.expectedActionName().equals(approval.getActionName()),
                            "wrong action was selected");
                }
                check(failures, externalWrites.get() == 0, "write executed before owner approval");
            }
            case "INSUFFICIENT_EVIDENCE" -> {
                check(failures, approval == null, "insufficient evidence still created an approval");
                check(failures, events.stream().anyMatch(
                        event -> "RUN_FAILED".equals(event.getEventType()) && step.getId().equals(event.getStepId())),
                        "failure timeline does not identify the run step");
                check(failures, externalWrites.get() == 0, "write executed without sufficient evidence");
            }
            case "UNAUTHORIZED_WRITE" -> {
                check(failures, approval == null, "write action was proposed without an authorized target");
                check(failures, mcpOpens.get() == 0, "MCP write tool was opened without an authorized target");
                check(failures, externalWrites.get() == 0, "write executed without an authorized target");
            }
            case "TOOL_FAILURE" -> {
                check(failures,
                        memory.approval() != null && "RECONCILIATION_REQUIRED".equals(memory.approval().getStatus()),
                        "uncertain tool failure did not require reconciliation");
                check(failures, externalWrites.get() == 1, "expected one failed tool attempt");
                check(failures,
                        events.stream()
                                .anyMatch(event -> "TOOL_ACTION".equals(event.getEventType())
                                        && "RECONCILIATION_REQUIRED".equals(event.getStatus())
                                        && step.getId().equals(event.getStepId())),
                        "tool failure event does not identify the step");
            }
            default -> failures.add("unknown scenario");
        }
        return failures;
    }

    private ChatModel scriptedChatModel(EvaluationCase evaluationCase) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (request.toolSpecifications() == null || request.toolSpecifications().isEmpty()) {
                    return ChatResponse.builder().aiMessage(new AiMessage("No external action is authorized."))
                            .tokenUsage(new TokenUsage(evaluationCase.inputTokens(), evaluationCase.outputTokens()))
                            .build();
                }
                String selectedTool = actionAlias(evaluationCase.expectedActionName());
                if (request.toolSpecifications().stream().noneMatch(spec -> selectedTool.equals(spec.name()))) {
                    throw new IllegalStateException("Expected deterministic action is not exposed");
                }
                try {
                    String arguments = JSON.writeValueAsString(evaluationCase.issue());
                    ToolExecutionRequest proposal = ToolExecutionRequest.builder().id("evaluation-action")
                            .name(selectedTool).arguments(arguments).build();
                    return ChatResponse.builder().aiMessage(AiMessage.from(proposal))
                            .tokenUsage(new TokenUsage(evaluationCase.inputTokens(), evaluationCase.outputTokens()))
                            .build();
                } catch (IOException exception) {
                    throw new IllegalStateException("Evaluation fixture could not be serialized", exception);
                }
            }
        };
    }

    private ToolBroker toolBroker(EvaluationCase evaluationCase, AtomicInteger mcpOpens) {
        List<String> availableActions = evaluationCase.availableActions() == null
                ? List.of("create_issue")
                : evaluationCase.availableActions();
        List<RegisteredToolAction> actions = availableActions.stream()
                .map(actionName -> new RegisteredToolAction("service-1", "github", actionName, "Create issue",
                        ISSUE_SCHEMA, ToolActionClassification.WRITE_REQUIRES_APPROVAL))
                .toList();
        ToolActionCatalog catalog = new ToolActionCatalog() {
            @Override
            public RegisteredToolAction require(AgentEntity agent, String userId, String serviceId, String actionName) {
                return actions.stream().filter(
                        candidate -> candidate.serviceId().equals(serviceId) && candidate.name().equals(actionName))
                        .findFirst().orElseThrow();
            }

            @Override
            public List<RegisteredToolAction> list(AgentEntity agent, String userId) {
                return actions;
            }
        };
        List<ToolSpecification> specifications = availableActions.stream().map(actionName -> ToolSpecification.builder()
                .name(actionName).description("Create issue").parameters(MCP_ISSUE_SCHEMA).build()).toList();
        McpClient client = mcpClient(specifications);
        McpClientFactory clients = (serverName, userId) -> client;
        McpActionClientProvider actionClients = (serverName, userId, targetRepository) -> {
            mcpOpens.incrementAndGet();
            return client;
        };
        return new ToolBroker(catalog, clients, actionClients, List.of(new GitHubMcpToolActionAdapter()));
    }

    private AgentRunModel agentRunModel(ChatModel chatModel, ToolBroker broker, KnowledgeRetrievalResult evidence) {
        AgentEntity agent = new AgentEntity();
        agent.setId("evaluation-agent");
        agent.setUserId("evaluation-owner");
        agent.setEnabled(true);
        agent.setSystemPrompt("Use registered actions only when they are authorized.");
        AgentWorkspaceEntity workspace = new AgentWorkspaceEntity("evaluation-agent", "evaluation-owner",
                new LLMModelConfig());

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
        model.setId("evaluation-model");
        model.setName("evaluation-fixture-v1");
        model.setProviderId("evaluation-provider");
        model.setStatus(true);
        ProviderEntity provider = new ProviderEntity();
        provider.setId("evaluation-provider");
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
        return new AgentRunModel(agentService, workspaceService, llmService, modelFactory, broker,
                (version, query, user) -> evidence);
    }

    private KnowledgeRetrievalResult evidence(Corpus corpus, EvaluationCase evaluationCase) {
        boolean available = evaluationCase.evidenceAvailable() == null
                ? corpus.evidence().available()
                : evaluationCase.evidenceAvailable();
        if (!available) {
            return new KnowledgeRetrievalResult("evaluation-knowledge-v1", false, "insufficient", List.of());
        }
        KnowledgeCitation citation = new KnowledgeCitation();
        citation.setKnowledgeVersionId("evaluation-knowledge-v1");
        citation.setDocumentId("evaluation-document-v1");
        citation.setFileId("evaluation-file-v1");
        citation.setFileName(corpus.evidence().fileName());
        citation.setPage(corpus.evidence().page());
        citation.setContent(corpus.evidence().content());
        return new KnowledgeRetrievalResult("evaluation-knowledge-v1", true, "evidence", List.of(citation));
    }

    private McpClient mcpClient(List<ToolSpecification> specifications) {
        return (McpClient) Proxy.newProxyInstance(McpClient.class.getClassLoader(), new Class<?>[]{McpClient.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "key" -> "evaluation-github";
                    case "listTools" -> specifications;
                    case "close" -> null;
                    case "toString" -> "EvaluationMcpClient";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private JsonNode parseIssue(String arguments, List<String> failures) {
        try {
            return JSON.readTree(arguments);
        } catch (IOException exception) {
            failures.add("approval arguments are not valid JSON");
            return JSON.createObjectNode();
        }
    }

    private String targetFrom(String arguments) {
        try {
            return org.xhy.application.tool.service.McpSchema.targetRepository(arguments);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Usage usage(List<RunTimelineEventEntity> events) {
        int input = 0;
        int output = 0;
        List<String> modelNames = new ArrayList<>();
        for (RunTimelineEventEntity event : events) {
            if (!"MODEL_CALL".equals(event.getEventType())) {
                continue;
            }
            try {
                JsonNode data = JSON.readTree(event.getDetailsJson());
                if (!"AVAILABLE".equals(data.path("usageStatus").asText())) {
                    continue;
                }
                input += data.path("inputTokens").asInt();
                output += data.path("outputTokens").asInt();
                modelNames.add(data.path("modelName").asText());
            } catch (IOException ignored) {
                // Invalid timeline usage is omitted; corpus assertions still report the failed model step.
            }
        }
        return new Usage(input, output, modelNames.stream().distinct().toList());
    }

    private Map<String, Object> summarize(List<CaseResult> results, RateCard rateCard) {
        long passed = results.stream().filter(CaseResult::passed).count();
        long duration = results.stream().mapToLong(CaseResult::activeDurationMs).sum();
        int inputTokens = results.stream().mapToInt(CaseResult::inputTokens).sum();
        int outputTokens = results.stream().mapToInt(CaseResult::outputTokens).sum();
        BigDecimal cost = results.stream().map(CaseResult::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCases", results.size());
        summary.put("passedCases", passed);
        summary.put("passRate", results.isEmpty() ? 0.0 : (double) passed / results.size());
        summary.put("activeDurationMs", duration);
        summary.put("inputTokens", inputTokens);
        summary.put("outputTokens", outputTokens);
        summary.put("totalTokens", inputTokens + outputTokens);
        summary.put("modelNames", results.stream().flatMap(result -> result.modelNames().stream()).distinct().toList());
        summary.put("rateCardVersion", rateCard.version());
        summary.put("costUsd", cost.setScale(8, RoundingMode.HALF_UP).toPlainString());
        return summary;
    }

    private Map<String, Object> compare(Map<String, Object> current, Path previousPath, Corpus corpus)
            throws IOException {
        JsonNode previous = JSON.readTree(previousPath.toFile());
        if (previous == null || !previous.path("corpusVersion").asText().equals(corpus.version())
                || !previous.path("rateCard").path("version").asText().equals(corpus.rateCard().version())) {
            throw new IllegalArgumentException("Previous report must use the same corpus and rate-card versions");
        }
        JsonNode prior = previous.path("summary");
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("evaluationId", previous.path("evaluationId").asText());
        comparison.put("passRateDelta",
                number(current.get("passRate")).doubleValue() - prior.path("passRate").asDouble());
        comparison.put("activeDurationDeltaMs",
                number(current.get("activeDurationMs")).longValue() - prior.path("activeDurationMs").asLong());
        comparison.put("totalTokenDelta",
                number(current.get("totalTokens")).intValue() - prior.path("totalTokens").asInt());
        comparison.put("costDeltaUsd",
                new BigDecimal(current.get("costUsd").toString())
                        .subtract(new BigDecimal(prior.path("costUsd").asText())).setScale(8, RoundingMode.HALF_UP)
                        .toPlainString());
        return comparison;
    }

    private BigDecimal cost(int inputTokens, int outputTokens, RateCard rateCard) {
        BigDecimal perMillion = BigDecimal.valueOf(1_000_000);
        return BigDecimal.valueOf(inputTokens).multiply(rateCard.inputUsdPerMillionTokens())
                .add(BigDecimal.valueOf(outputTokens).multiply(rateCard.outputUsdPerMillionTokens()))
                .divide(perMillion, 8, RoundingMode.HALF_UP);
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return new BigDecimal(value.toString());
    }

    private Corpus readCorpus() throws IOException {
        try (var stream = getClass().getResourceAsStream(CORPUS_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing versioned evaluation corpus " + CORPUS_RESOURCE);
            }
            return JSON.readValue(stream, Corpus.class);
        }
    }

    private RunApprovalDraft approvalDraft(EvaluationCase evaluationCase) {
        try {
            return new RunApprovalDraft("github", "create_issue", "Create issue", evaluationCase.targetRepository(),
                    JSON.writeValueAsString(evaluationCase.issue()), ISSUE_SCHEMA);
        } catch (IOException exception) {
            throw new IllegalStateException("Evaluation fixture could not be serialized", exception);
        }
    }

    private void check(List<String> failures, boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private String citationFileName() {
        return "release-notes.md";
    }

    private String actionAlias(String actionName) {
        String key = "service-1:" + actionName;
        return "mcp_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private static <T> T proxy(Class<T> type, Function<Method, Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, arguments) -> handler.apply(method)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0.0;
        return null;
    }

    private record Corpus(String version, RateCard rateCard, Evidence evidence, List<EvaluationCase> cases) {
    }

    private record RateCard(String version, String currency, BigDecimal inputUsdPerMillionTokens,
            BigDecimal outputUsdPerMillionTokens) {
    }

    private record Evidence(boolean available, String fileName, Integer page, String content) {
    }

    private record EvaluationCase(String id, String scenario, String goal, String targetRepository,
            Map<String, String> issue, List<String> availableActions, String expectedActionName, String expectedStatus,
            Boolean evidenceAvailable, int inputTokens, int outputTokens) {
    }

    private record Usage(int inputTokens, int outputTokens, List<String> modelNames) {
    }

    private record CaseResult(String caseId, String scenario, boolean passed, String runId, String stepId,
            String runStatus, long activeDurationMs, int inputTokens, int outputTokens, List<String> modelNames,
            BigDecimal costUsd, String failure, String failureRunId, String failureStepId) {
    }

    private static final class ActiveDuration {
        private final LongSupplier nanoClock;
        private long activeNanos;

        private ActiveDuration(LongSupplier nanoClock) {
            this.nanoClock = nanoClock;
        }

        private void measure(Runnable workerAttempt) {
            long started = nanoClock.getAsLong();
            try {
                workerAttempt.run();
            } finally {
                activeNanos += Math.max(0L, nanoClock.getAsLong() - started);
            }
        }

        private long milliseconds() {
            return activeNanos / 1_000_000;
        }
    }

    private static final class InMemoryTimeline implements RunTimelineRepository {
        private final List<RunTimelineEventEntity> events;

        private InMemoryTimeline(List<RunTimelineEventEntity> events) {
            this.events = events;
        }

        @Override
        public synchronized void append(RunTimelineEventEntity event) {
            events.add(event);
        }

        @Override
        public synchronized List<RunTimelineEventEntity> eventsForRun(String runId) {
            return events.stream().filter(event -> runId.equals(event.getRunId())).toList();
        }
    }

    private static final class EvaluationStore implements InvocationHandler {
        private final RunEntity run;
        private final RunStepEntity step;
        private String leaseToken;
        private RunApprovalEntity approval;

        private EvaluationStore(RunEntity run, RunStepEntity step) {
            this.run = run;
            this.step = step;
        }

        private RunStore proxy() {
            return (RunStore) Proxy.newProxyInstance(RunStore.class.getClassLoader(), new Class<?>[]{RunStore.class},
                    this);
        }

        private RunApprovalEntity approval() {
            return approval;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "claim" -> claim((String) arguments[1]);
                case "findForWorker" -> run;
                case "stepsForRun" -> List.of(step);
                case "findApprovalForRun" -> approval;
                case "requestApproval" -> request((RunApprovalEntity) arguments[2], false);
                case "requestPreauthorizedAction" -> request((RunApprovalEntity) arguments[2], true);
                case "beginApprovedAction" -> begin((String) arguments[1], (String) arguments[2]);
                case "waitForReconciliation" -> waitForReconciliation((String) arguments[1]);
                case "complete" -> complete((String) arguments[1], (String) arguments[2]);
                case "fail" -> fail((String) arguments[1], (String) arguments[2]);
                case "renewLease" -> leaseToken != null && leaseToken.equals(arguments[1]);
                case "recordApprovedActionResult" ->
                    recordResult((String) arguments[1], (String) arguments[2], (String) arguments[3]);
                case "toString" -> "EvaluationStore(" + run.getId() + ")";
                default -> defaultValue(method.getReturnType());
            };
        }

        private boolean claim(String token) {
            if (leaseToken != null)
                return false;
            leaseToken = token;
            run.start();
            step.start();
            return true;
        }

        private boolean request(RunApprovalEntity next, boolean preauthorized) {
            if (approval != null || !"RUNNING".equals(run.getStatus()))
                return false;
            approval = next;
            if (preauthorized) {
                return true;
            }
            run.waitForApproval();
            step.waitForApproval();
            return true;
        }

        private boolean begin(String token, String approvalId) {
            if (!tokenMatches(token) || approval == null || !approvalId.equals(approval.getId())
                    || !"APPROVED".equals(approval.getStatus()))
                return false;
            approval.setStatus("EXECUTING");
            return true;
        }

        private boolean waitForReconciliation(String token) {
            if (!tokenMatches(token))
                return false;
            run.waitForReconciliation();
            step.waitForReconciliation();
            if (approval != null)
                approval.requireReconciliation();
            return true;
        }

        private boolean complete(String token, String answer) {
            if (!tokenMatches(token))
                return false;
            run.complete(answer);
            step.complete(answer);
            return true;
        }

        private boolean fail(String token, String reason) {
            if (!tokenMatches(token))
                return false;
            run.fail(reason);
            step.fail(reason);
            return true;
        }

        private boolean recordResult(String token, String approvalId, String result) {
            if (!tokenMatches(token) || approval == null || !approvalId.equals(approval.getId()))
                return false;
            approval.recordExternalResource(result);
            return true;
        }

        private boolean tokenMatches(String token) {
            return leaseToken != null && leaseToken.equals(token) && "RUNNING".equals(run.getStatus());
        }
    }
}
