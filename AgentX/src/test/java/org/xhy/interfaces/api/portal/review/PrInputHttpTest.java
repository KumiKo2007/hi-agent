package org.xhy.interfaces.api.portal.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.github.service.*;
import org.xhy.application.review.service.PrInputAppService;
import org.xhy.application.tool.service.*;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.repository.AgentRepository;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.github.model.GitHubConnection;
import org.xhy.domain.github.repository.GitHubConnectionRepository;
import org.xhy.domain.review.model.PrInputSnapshot;
import org.xhy.domain.review.repository.PrInputStore;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.domain.tool.model.*;
import org.xhy.domain.tool.model.config.ToolDefinition;
import org.xhy.domain.tool.repository.*;
import org.xhy.domain.tool.service.UserToolDomainService;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.config.GlobalExceptionHandler;
import org.xhy.infrastructure.github.GitHubTokenCipher;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 补充 HTTP 行为测试：真实流程配合内存持久化及外部 MCP 替身，不代表数据库验收。 */
class PrInputHttpTest extends ControlledGitHubMcp {
    final Map<String, GitHubConnection> connections = new HashMap<>();
    final Map<String, PrInputSnapshot> snapshots = new HashMap<>();
    final Map<String, ToolActionPolicy> policies = new HashMap<>();
    final MockMvc mvc = api();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void readsAuthorizedPullRequestAndReloadsImmutableCommitSnapshot() throws Exception {
        UserContext.setCurrentUserId("owner");
        mvc.perform(get("/reviews/input-options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.choices[0].agentId").value("agent"))
                .andExpect(jsonPath("$.data.connection.githubLogin").value("alice"));
        JsonNode input = submit("https://github.com/alice/repo/pull/42");
        assertEquals(BASE, input.path("baseSha").asText());
        assertEquals(HEAD, input.path("headSha").asText());
        assertEquals("src/math.ts", input.path("files").get(0).path("path").asText());
        assertTrue(input.path("contexts").toString().contains("return 2"));
        assertFalse(input.toString().contains(TOKEN));
        assertTrue(calls.stream().filter(c -> c.has("sha"))
                .allMatch(c -> List.of(BASE, HEAD).contains(c.path("sha").asText())));
        int previousCalls = calls.size();
        mvc.perform(get("/reviews/inputs/" + input.path("id").asText())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headSha").value(HEAD));
        assertEquals(previousCalls, calls.size());
        UserContext.setCurrentUserId("other");
        mvc.perform(get("/reviews/inputs/" + input.path("id").asText())).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnauthorizedOrInvalidTargetsBeforeCallingMcp() throws Exception {
        UserContext.setCurrentUserId("owner");
        for (String target : List.of("https://github.com/bob/private/pull/1",
                "https://github.com.evil/alice/repo/pull/42", "https://github.com/alice/repo/issues/42",
                "https://github.com/alice/repo/pull/42?token=bad")) {
            mvc.perform(post("/reviews/inputs").contentType("application/json")
                    .content(JSON.writeValueAsString(
                            Map.of("agentId", "agent", "serviceId", "github-service", "pullRequestUrl", target))))
                    .andExpect(status().isBadRequest());
        }
        assertTrue(calls.isEmpty());
    }

    @Test
    void requiresExplicitReadClassificationAndActualInstalledActionSchema() throws Exception {
        UserContext.setCurrentUserId("owner");
        policies.remove("get_file_contents");
        mvc.perform(post("/reviews/inputs").contentType("application/json").content(request()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("只读")));
        assertTrue(calls.isEmpty());
        policies.put("get_file_contents", new ToolActionPolicy("github-service", "get_file_contents",
                ToolActionClassification.READ_ONLY, fileSchema));
        liveMismatch = true;
        mvc.perform(post("/reviews/inputs").contentType("application/json").content(request()))
                .andExpect(status().isBadRequest());
        assertTrue(calls.isEmpty());
    }

    @Test
    void rejectsCommitDriftAndKeepsExternalFailureCredentialSafe() throws Exception {
        UserContext.setCurrentUserId("owner");
        drift = true;
        mvc.perform(post("/reviews/inputs").contentType("application/json").content(request()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("提交已变化")))
                .andExpect(jsonPath("$.data").isEmpty());
        drift = false;
        failing = true;
        String failure = mvc.perform(post("/reviews/inputs").contentType("application/json").content(request()))
                .andExpect(status().isBadRequest()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(failure.contains(TOKEN));
        assertTrue(failure.contains("PR 读取失败"));
    }

    @Test
    void disclosesMissingSourceInsteadOfClaimingACompleteRepositoryRead() throws Exception {
        UserContext.setCurrentUserId("owner");
        missingContext = true;
        JsonNode input = submit("https://github.com/alice/repo/pull/42");
        assertTrue(input.path("contexts").isEmpty());
        assertTrue(input.path("gaps").toString().contains("未取得源码"));
        assertTrue(input.path("gaps").toString().contains("未读取完整仓库"));
    }

    @Test
    void disclosesLargeFilesWithoutContentRatherThanTreatingThemAsEmptySource() throws Exception {
        UserContext.setCurrentUserId("owner");
        emptyLargeFile = true;
        JsonNode input = submit("https://github.com/alice/repo/pull/42");
        assertTrue(input.path("contexts").isEmpty());
        assertTrue(input.path("gaps").toString().contains("未取得源码"));
    }

    String request() throws Exception {
        return JSON.writeValueAsString(Map.of("agentId", "agent", "serviceId", "github-service", "pullRequestUrl",
                "https://github.com/alice/repo/pull/42"));
    }

    JsonNode submit(String url) throws Exception {
        return submit(mvc, url);
    }

    JsonNode submit(MockMvc http, String url) throws Exception {
        String response = http
                .perform(post("/reviews/inputs").contentType("application/json")
                        .content(JSON.writeValueAsString(
                                Map.of("agentId", "agent", "serviceId", "github-service", "pullRequestUrl", url))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(response).path("data");
    }

    MockMvc api() {
        return api(null);
    }

    MockMvc api(PrInputStore persisted) {
        AgentEntity agent = new AgentEntity();
        agent.setId("agent");
        agent.setUserId("owner");
        agent.setName("代码审查");
        agent.setEnabled(true);
        agent.setToolIds(List.of("github-service"));
        AgentRepository agents = memory(AgentRepository.class,
                Map.of("selectOne", agent, "selectList", List.of(agent)));
        UserToolEntity installed = new UserToolEntity();
        installed.setToolId("github-service");
        installed.setUserId("owner");
        installed.setMcpServerName("github");
        installed.setToolList(
                List.of(definition("pull_request_read", prSchema), definition("get_file_contents", fileSchema)));
        UserToolRepository tools = memory(UserToolRepository.class,
                Map.of("selectOne", installed, "selectList", List.of(installed)));
        ToolActionPolicyStore policyStore = new ToolActionPolicyStore() {
            public ToolActionPolicy find(String service, String name) {
                return policies.get(name);
            }
            public void save(ToolActionPolicy value) {
                policies.put(value.actionName(), value);
            }
        };
        policies.put("pull_request_read", new ToolActionPolicy("github-service", "pull_request_read",
                ToolActionClassification.READ_ONLY, prSchema));
        policies.put("get_file_contents", new ToolActionPolicy("github-service", "get_file_contents",
                ToolActionClassification.READ_ONLY, fileSchema));
        AgentToolActionSelectionStore selections = memory(AgentToolActionSelectionStore.class,
                Map.of("contains", true, "isPreauthorized", false));
        ToolActionCatalog catalog = new PersistentToolActionCatalog(new UserToolDomainService(tools), policyStore,
                selections);
        GitHubConnectionRepository repository = new GitHubConnectionRepository() {
            public Optional<GitHubConnection> findByOwner(String owner) {
                return Optional.ofNullable(connections.get(owner));
            }
            public Optional<GitHubConnection> findByOwnerForUpdate(String owner) {
                return findByOwner(owner);
            }
            public void save(GitHubConnection value) {
                connections.put(value.ownerId(), value);
            }
            public void deleteByOwner(String owner) {
                connections.remove(owner);
            }
        };
        GitHubTokenCipher cipher = new GitHubTokenCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        GitHubConnectionAppService github = new GitHubConnectionAppService(repository,
                (token, repos) -> new org.xhy.infrastructure.github.GitHubCredentialVerifier.Identity("1", "alice"),
                cipher);
        github.connect("owner", TOKEN, List.of("alice/repo"));
        McpClientFactory factory = factory();
        ToolBroker broker = new ToolBroker(catalog, factory, new GitHubMcpActionClientProvider(factory, github),
                List.of(new GitHubMcpToolActionAdapter()));
        PrInputStore store = new PrInputStore() {
            public void save(String owner, PrInputSnapshot value) {
                snapshots.put(owner + "/" + value.id(), value);
            }
            public Optional<PrInputSnapshot> find(String id, String owner) {
                return Optional.ofNullable(snapshots.get(owner + "/" + id));
            }
        };
        return MockMvcBuilders
                .standaloneSetup(
                        new PrInputController(new PrInputAppService(new AgentDomainService(agents, null, null, null),
                                catalog, broker, github, persisted == null ? store : persisted)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @SuppressWarnings("unchecked")
    static <T> T memory(Class<T> type, Map<String, Object> responses) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (responses.containsKey(method.getName()))
                return responses.get(method.getName());
            throw new UnsupportedOperationException("未支持的内存持久化动作 " + method.getName());
        });
    }
}
