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
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.github.service.GitHubMcpActionClientProvider;
import org.xhy.application.github.service.GitHubMcpToolActionAdapter;
import org.xhy.application.tool.service.ToolActionCatalog;
import org.xhy.application.tool.service.ToolBroker;
import org.xhy.application.tool.service.McpClientFactory;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.repository.AgentRepository;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunApprovalPolicy;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.github.GitHubCredentialVerifier;
import org.xhy.infrastructure.github.GitHubTokenCipher;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpApprovedRunActionExecutorTest {
    private static final String USER = "owner-1";
    private static final String TOKEN = "github_pat_abcdefghijklmnopqrstuvwxyz123456";
    private static final String ARGUMENTS = "{\"repository\":\"kumiko2007/hi-agent\",\"title\":\"Approved title\"}";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
            Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string")), "required",
            List.of("repository", "title"), "additionalProperties", false);
    private static final JsonObjectSchema MCP_SCHEMA = JsonObjectSchema.builder().addStringProperty("repository")
            .addStringProperty("title").required("repository", "title").additionalProperties(false).build();

    @Test
    void shouldStopBeforeMcpWhenSavedGitHubCredentialHasBeenRevoked() {
        Fixture fixture = new Fixture(List.of("kumiko2007/hi-agent"), true);

        assertThrows(BusinessException.class, () -> fixture.executor().execute(run(), approval(true)));

        assertEquals(0, fixture.client.opens.get());
        assertEquals(0, fixture.client.writes.get());
    }

    @Test
    void shouldStopBeforeMcpWhenApprovedRepositoryIsOutsideSavedScope() {
        Fixture fixture = new Fixture(List.of("kumiko2007/other-repo"), false);

        assertThrows(BusinessException.class, () -> fixture.executor().execute(run(), approval(true)));

        assertEquals(0, fixture.verifications.get());
        assertEquals(0, fixture.client.opens.get());
        assertEquals(0, fixture.client.writes.get());
    }

    @Test
    void shouldStopBeforeGitHubWhenCreatorRevokedPreauthorization() {
        Fixture fixture = new Fixture(List.of("kumiko2007/hi-agent"), false, false);

        assertThrows(BusinessException.class, () -> fixture.executor().execute(run(), approval(true)));

        assertEquals(0, fixture.verifications.get());
        assertEquals(0, fixture.client.opens.get());
        assertEquals(0, fixture.client.writes.get());
    }

    @Test
    void shouldRecheckCreatorPreauthorizationAtTheMcpDispatchBoundary() {
        Fixture fixture = new Fixture(List.of("kumiko2007/hi-agent"), false);
        fixture.revokePreauthorizationOnSecondCatalogLookup = true;

        assertThrows(BusinessException.class, () -> fixture.executor().execute(run(), approval(true)));

        assertEquals(0, fixture.client.opens.get());
        assertEquals(0, fixture.client.writes.get());
    }

    @Test
    void shouldNotLetCreatorPreauthorizationOverrideTheUsersStricterRunPolicy() {
        Fixture fixture = new Fixture(List.of("kumiko2007/hi-agent"), false);
        RunEntity run = run();
        run.setApprovalPolicy(RunApprovalPolicy.REQUIRE_APPROVAL_FOR_ALL_WRITES.name());

        assertThrows(BusinessException.class, () -> fixture.executor().execute(run, approval(true)));

        assertEquals(0, fixture.verifications.get());
        assertEquals(0, fixture.client.opens.get());
        assertEquals(0, fixture.client.writes.get());
    }

    @Test
    void shouldRevalidateCurrentGitHubAuthorizationImmediatelyBeforeTheMcpCall() {
        Fixture fixture = new Fixture(List.of("kumiko2007/hi-agent"), false);
        fixture.revokeOnSecondVerification = true;

        assertThrows(BusinessException.class, () -> fixture.executor().execute(run(), approval(false)));

        assertEquals(2, fixture.verifications.get());
        assertEquals(1, fixture.client.opens.get());
        assertEquals(0, fixture.client.writes.get());
    }

    @Test
    void normalizesTheMcpResultAndReturnsTheApprovedRepositoryIssueUrl() {
        Fixture fixture = new Fixture(List.of("kumiko2007/hi-agent"), false);

        String issueUrl = fixture.executor().execute(run(), approval(false));

        assertEquals("https://github.com/kumiko2007/hi-agent/issues/42", issueUrl);
        assertEquals(2, fixture.verifications.get());
        assertEquals(1, fixture.client.writes.get());
    }

    private static RunEntity run() {
        RunEntity run = RunEntity.create(USER, "session-1", "agent-1", "Create a GitHub issue");
        run.setId("run-1");
        return run;
    }

    private static RunApprovalEntity approval(boolean preauthorized) {
        RunApprovalEntity approval = RunApprovalEntity.create("run-1", new RunApprovalDraft("service-1", "create_issue",
                "Create issue", "kumiko2007/hi-agent", ARGUMENTS, SCHEMA));
        approval.setStatus("APPROVED");
        approval.setApprovalSource(preauthorized ? "CREATOR_PREAUTHORIZATION" : "USER");
        return approval;
    }

    private static final class Fixture {
        private final AtomicInteger verifications = new AtomicInteger();
        private final TestMcpClient client = new TestMcpClient();
        private final GitHubTokenCipher cipher = new GitHubTokenCipher(
                Base64.getEncoder().encodeToString(new byte[32]));
        private final GitHubConnectionRepository connections;
        private final GitHubCredentialVerifier verifier;
        private final boolean creatorPreauthorizationActive;
        private final AtomicInteger actionLookups = new AtomicInteger();
        private boolean revokePreauthorizationOnSecondCatalogLookup;
        private boolean revokeOnSecondVerification;

        private Fixture(List<String> scope, boolean revoked) {
            this(scope, revoked, true);
        }

        private Fixture(List<String> scope, boolean revoked, boolean creatorPreauthorizationActive) {
            this.creatorPreauthorizationActive = creatorPreauthorizationActive;
            GitHubConnection connection = new GitHubConnection(USER, "github-user-1", "owner",
                    cipher.encrypt(USER, TOKEN), scope);
            connections = new GitHubConnectionRepository() {
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
            verifier = (token, repositories) -> {
                verifications.incrementAndGet();
                if (!TOKEN.equals(token))
                    throw new AssertionError("Expected saved credential");
                if (revoked || (revokeOnSecondVerification && verifications.get() > 1)) {
                    throw new BusinessException("credential was revoked");
                }
                return new GitHubCredentialVerifier.Identity("github-user-1", "owner");
            };
        }

        private McpApprovedRunActionExecutor executor() {
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
                    int lookup = actionLookups.incrementAndGet();
                    return action(creatorPreauthorizationActive
                            && !(revokePreauthorizationOnSecondCatalogLookup && lookup > 1));
                }
                @Override
                public List<RegisteredToolAction> list(AgentEntity ignored, String userId) {
                    return List.of(action(creatorPreauthorizationActive));
                }
            };
            McpClientFactory clients = new McpClientFactory() {
                @Override
                public McpClient open(String server, String user) {
                    client.opens.incrementAndGet();
                    return client;
                }

                @Override
                public McpClient openWithGitHubToken(String server, String user, String githubToken) {
                    client.opens.incrementAndGet();
                    assertEquals(TOKEN, githubToken);
                    return client;
                }
            };
            GitHubConnectionAppService githubConnections = new GitHubConnectionAppService(connections, verifier,
                    cipher);
            ToolBroker tools = new ToolBroker(catalog, clients,
                    new GitHubMcpActionClientProvider(clients, githubConnections),
                    List.of(new GitHubMcpToolActionAdapter()));
            return new McpApprovedRunActionExecutor(agents,
                    new GitHubRunTargetRepositoryAuthorization(githubConnections), tools,
                    new GitHubIssueResultNormalizer());
        }

        private static RegisteredToolAction action(boolean preauthorized) {
            return new RegisteredToolAction("service-1", "github", "create_issue", "Create issue", SCHEMA,
                    ToolActionClassification.WRITE_REQUIRES_APPROVAL, true, preauthorized);
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

    private static final class TestMcpClient implements McpClient {
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger writes = new AtomicInteger();
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
            writes.incrementAndGet();
            lastRequest = request;
            return "created issue 42";
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
