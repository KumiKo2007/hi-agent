package org.xhy.application.run.service;

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
import org.xhy.application.github.service.GitHubConnectionAppService;
import org.xhy.application.github.service.GitHubIssueResultNormalizer;
import org.xhy.application.github.service.GitHubRunTargetRepositoryAuthorization;
import org.xhy.application.run.assembler.RunAssembler;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.github.service.GitHubMcpActionClientProvider;
import org.xhy.application.github.service.GitHubMcpToolActionAdapter;
import org.xhy.application.tool.service.McpClientFactory;
import org.xhy.application.tool.service.ToolActionCatalog;
import org.xhy.application.tool.service.ToolBroker;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.repository.AgentRepository;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.github.GitHubCredentialVerifier;
import org.xhy.infrastructure.github.GitHubTokenCipher;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class RunWorkerApprovedActionIdempotencyTest {
    private static final String USER = "owner-1";
    private static final String TOKEN = "github_pat_abcdefghijklmnopqrstuvwxyz123456";
    private static final String REPOSITORY = "kumiko2007/hi-agent";
    private static final String ARGUMENTS = "{\"repository\":\"kumiko2007/hi-agent\",\"title\":\"Approved title\"}";
    private static final String EXTERNAL_RESOURCE_ID = "https://github.com/kumiko2007/hi-agent/issues/42";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
            Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string")), "required",
            List.of("repository", "title"), "additionalProperties", false);
    private static final JsonObjectSchema MCP_SCHEMA = JsonObjectSchema.builder().addStringProperty("repository")
            .addStringProperty("title").required("repository", "title").additionalProperties(false).build();

    @Test
    void redeliveryAfterPersistedExternalSuccessReusesResourceWithoutAnotherMcpCreate() {
        Fixture fixture = new Fixture();
        String actionIntentId = fixture.store.approval.getId();
        assertNotNull(actionIntentId);
        assertFalse(actionIntentId.isBlank());
        assertEquals(actionIntentId, fixture.store.findApprovalForRun("run-1").getId(),
                "the unique action intent must be stored before dispatch");

        assertThrows(IllegalStateException.class, () -> fixture.worker().process("run-1"));

        assertEquals("RUNNING", fixture.store.run.getStatus(), "first completion simulates a lost worker response");
        assertEquals("EXECUTED", fixture.store.approval.getStatus());
        assertEquals(EXTERNAL_RESOURCE_ID, fixture.store.approval.getExternalResourceId());

        fixture.worker().process("run-1");

        assertEquals("COMPLETED", fixture.store.run.getStatus());
        assertEquals(EXTERNAL_RESOURCE_ID, fixture.store.run.getResult());
        assertEquals(actionIntentId, fixture.store.approval.getId(), "the persisted action intent is reused");
        assertNotNull(actionIntentId);
        assertFalse(actionIntentId.isBlank());
        assertEquals(1, fixture.client.writeCalls.get(), "the external create action must run once");
        assertEquals("create_issue", fixture.client.lastRequest.name());
        assertEquals(ARGUMENTS, fixture.client.lastRequest.arguments());
        assertEquals(List.of(REPOSITORY, REPOSITORY), fixture.verifiedRepositories,
                "authorization must be checked before opening and again immediately before dispatch");

        var approvalDTO = RunAssembler.toDTO(fixture.store.run, List.of(), fixture.store.approval).getApproval();
        assertEquals(EXTERNAL_RESOURCE_ID, approvalDTO.getExternalResourceId(),
                "the stored external resource id must be visible to the run owner");
    }

    private static final class Fixture {
        private final MemoryRunStore store = new MemoryRunStore();
        private final TestMcpClient client = new TestMcpClient();
        private final List<String> verifiedRepositories = new ArrayList<>();
        private final GitHubTokenCipher cipher = new GitHubTokenCipher(
                Base64.getEncoder().encodeToString(new byte[32]));

        private Fixture() {
            GitHubConnection connection = new GitHubConnection(USER, "github-user-1", "owner",
                    cipher.encrypt(USER, TOKEN), List.of(REPOSITORY));
            GitHubConnectionRepository connections = new GitHubConnectionRepository() {
                @Override
                public Optional<GitHubConnection> findByOwner(String ownerId) {
                    return USER.equals(ownerId) ? Optional.of(connection) : Optional.empty();
                }
                @Override
                public Optional<GitHubConnection> findByOwnerForUpdate(String ownerId) {
                    return findByOwner(ownerId);
                }
                @Override
                public void save(GitHubConnection value) {
                    throw new UnsupportedOperationException();
                }
                @Override
                public void deleteByOwner(String ownerId) {
                    throw new UnsupportedOperationException();
                }
            };
            GitHubCredentialVerifier verifier = (token, repositories) -> {
                if (!TOKEN.equals(token))
                    throw new AssertionError("Expected saved credential");
                verifiedRepositories.addAll(repositories);
                return new GitHubCredentialVerifier.Identity("github-user-1", "owner");
            };

            AgentEntity agent = new AgentEntity();
            agent.setId("agent-1");
            agent.setUserId(USER);
            agent.setEnabled(true);
            agent.setToolIds(List.of("service-1"));
            AgentRepository repository = proxy(AgentRepository.class,
                    method -> method.getName().equals("selectByIds")
                            ? List.of(agent)
                            : defaultValue(method.getReturnType()));
            AgentDomainService agents = new AgentDomainService(repository, null, null, null);
            ToolActionCatalog catalog = new ToolActionCatalog() {
                @Override
                public RegisteredToolAction require(AgentEntity ignored, String userId, String serviceId,
                        String actionName) {
                    if (!"service-1".equals(serviceId) || !"create_issue".equals(actionName)
                            || !agent.getToolIds().contains(serviceId)) {
                        throw new BusinessException("Action not selected");
                    }
                    return action();
                }
                @Override
                public List<RegisteredToolAction> list(AgentEntity ignored, String userId) {
                    return List.of(action());
                }
            };
            McpClientFactory clients = new McpClientFactory() {
                @Override
                public McpClient open(String server, String user) {
                    return client;
                }

                @Override
                public McpClient openWithGitHubToken(String server, String user, String githubToken) {
                    if (!TOKEN.equals(githubToken))
                        throw new AssertionError("Expected authorized GitHub token");
                    return client;
                }
            };
            GitHubConnectionAppService githubConnections = new GitHubConnectionAppService(connections, verifier,
                    cipher);
            ToolBroker tools = new ToolBroker(catalog, clients,
                    new GitHubMcpActionClientProvider(clients, githubConnections),
                    List.of(new GitHubMcpToolActionAdapter()));
            ApprovedRunActionExecutor approvedActions = new McpApprovedRunActionExecutor(agents,
                    new GitHubRunTargetRepositoryAuthorization(githubConnections), tools,
                    new GitHubIssueResultNormalizer());
            RunModel unexpectedModel = run -> {
                fail("an approved action recovery must not invoke the model");
                return null;
            };
            store.approval = RunApprovalEntity.create("run-1",
                    new RunApprovalDraft("service-1", "create_issue", "Create issue", REPOSITORY, ARGUMENTS, SCHEMA));
            store.approval.decide(true, USER);
            store.run = RunEntity.create(USER, "session-1", "agent-1", "Create a GitHub issue");
            store.run.setId("run-1");
            store.run.setStatus("PENDING");
            store.failFirstCompletion = true;
            this.approvedActions = approvedActions;
            this.unexpectedModel = unexpectedModel;
        }

        private final ApprovedRunActionExecutor approvedActions;
        private final RunModel unexpectedModel;

        private RunWorker worker() {
            return new RunWorker(store, unexpectedModel, approvedActions);
        }

        private static RegisteredToolAction action() {
            return new RegisteredToolAction("service-1", "github", "create_issue", "Create issue", SCHEMA,
                    ToolActionClassification.WRITE_REQUIRES_APPROVAL);
        }
    }

    private static final class MemoryRunStore implements RunStore {
        private RunEntity run;
        private RunApprovalEntity approval;
        private boolean failFirstCompletion;
        private boolean expiredLease;
        private String leaseToken;

        @Override
        public void createRunWithStep(RunEntity run, RunStepEntity step) {
            throw new UnsupportedOperationException();
        }
        @Override
        public RunEntity findOwned(String runId, String userId) {
            return run;
        }
        @Override
        public RunEntity findForWorker(String runId) {
            return run;
        }
        @Override
        public List<RunEntity> listOwnedBySession(String sessionId, String userId) {
            return List.of();
        }
        @Override
        public List<RunStepEntity> stepsForRun(String runId) {
            return List.of();
        }
        @Override
        public boolean requestApproval(String runId, String token, RunApprovalEntity request) {
            return false;
        }
        @Override
        public RunApprovalEntity findApprovalForRun(String runId) {
            return copy(approval);
        }
        @Override
        public boolean decideApproval(String runId, String approvalId, String userId, boolean approve) {
            return false;
        }

        @Override
        public boolean beginApprovedAction(String runId, String token, String approvalId) {
            if (!owns(runId, token) || !approvalId.equals(approval.getId())
                    || !"APPROVED".equals(approval.getStatus())) {
                return false;
            }
            approval.setStatus("EXECUTING");
            return true;
        }

        @Override
        public boolean recordApprovedActionResult(String runId, String token, String approvalId,
                String externalResourceId) {
            if (!owns(runId, token) || !approvalId.equals(approval.getId())
                    || !"EXECUTING".equals(approval.getStatus())) {
                return false;
            }
            approval.recordExternalResource(externalResourceId);
            return true;
        }

        @Override
        public boolean waitForReconciliation(String runId, String token) {
            return false;
        }
        @Override
        public boolean cancelOwned(String runId, String userId) {
            return false;
        }

        @Override
        public boolean claim(String runId, String token, int leaseSeconds) {
            if (!"PENDING".equals(run.getStatus()) && !("RUNNING".equals(run.getStatus()) && expiredLease))
                return false;
            run.setStatus("RUNNING");
            leaseToken = token;
            expiredLease = false;
            return true;
        }

        @Override
        public boolean renewLease(String runId, String token, int leaseSeconds) {
            return owns(runId, token);
        }

        @Override
        public boolean complete(String runId, String token, String answer) {
            if (!owns(runId, token))
                return false;
            if (failFirstCompletion) {
                failFirstCompletion = false;
                leaseToken = null;
                expiredLease = true;
                throw new IllegalStateException("worker stopped after external success was persisted");
            }
            run.complete(answer);
            return true;
        }

        @Override
        public boolean fail(String runId, String token, String reason) {
            return false;
        }
        @Override
        public List<String> recoverableRunIds(int limit) {
            return List.of();
        }

        private boolean owns(String runId, String token) {
            return "run-1".equals(runId) && token.equals(leaseToken) && "RUNNING".equals(run.getStatus());
        }

        private static RunApprovalEntity copy(RunApprovalEntity source) {
            if (source == null)
                return null;
            RunApprovalEntity copy = new RunApprovalEntity();
            copy.setId(source.getId());
            copy.setRunId(source.getRunId());
            copy.setServiceId(source.getServiceId());
            copy.setActionName(source.getActionName());
            copy.setActionDescription(source.getActionDescription());
            copy.setTargetRepository(source.getTargetRepository());
            copy.setArguments(source.getArguments());
            copy.setSchemaJson(source.getSchemaJson());
            copy.setStatus(source.getStatus());
            copy.setDecidedBy(source.getDecidedBy());
            copy.setDecidedAt(source.getDecidedAt());
            copy.setExternalResourceId(source.getExternalResourceId());
            return copy;
        }
    }

    private static final class TestMcpClient implements McpClient {
        private final AtomicInteger writeCalls = new AtomicInteger();
        private ToolExecutionRequest lastRequest;

        @Override
        public String key() {
            return "github";
        }
        @Override
        public List<ToolSpecification> listTools() {
            return List.of(ToolSpecification.builder().name("create_issue").description("Create issue")
                    .parameters(MCP_SCHEMA).build());
        }
        @Override
        public String executeTool(ToolExecutionRequest request) {
            writeCalls.incrementAndGet();
            lastRequest = request;
            return EXTERNAL_RESOURCE_ID;
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

    private static <T> T proxy(Class<T> type, Function<Method, Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
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
}
