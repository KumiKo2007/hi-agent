package org.xhy.interfaces.api.portal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.run.service.RunAppService;
import org.xhy.application.run.service.RunWorker;
import org.xhy.domain.agent.model.AgentVersionEntity;
import org.xhy.domain.agent.model.AgentWorkspaceEntity;
import org.xhy.domain.agent.model.LLMModelConfig;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.agent.service.AgentWorkspaceDomainService;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
import org.xhy.domain.llm.service.LLMDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.config.GlobalExceptionHandler;
import org.xhy.infrastructure.llm.protocol.enums.ProviderProtocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 通过公开运行接口验证运行创建时固定 Agent 发布版本和模型连接。 */
class RunVersionPinningTest {

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void should_keepPublishedVersionAndModelConnectionWhenDefinitionsChange() throws Exception {
        SessionDomainService sessions = mock(SessionDomainService.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);

        MutablePublishedDefinition definition = new MutablePublishedDefinition();
        AgentDomainService agents = mock(AgentDomainService.class);
        when(agents.getPublishedAgentVersion("agent-1")).thenAnswer(ignored -> definition.version());
        AgentWorkspaceDomainService workspaces = mock(AgentWorkspaceDomainService.class);
        when(workspaces.getWorkspace("agent-1", "owner")).thenAnswer(ignored -> workspace(definition.modelId()));
        ModelEntity openAiModel = model("model-openai", "provider-openai");
        ModelEntity anthropicModel = model("model-anthropic", "provider-anthropic");
        ProviderEntity openAi = provider("provider-openai", ProviderProtocol.OPENAI);
        ProviderEntity anthropic = provider("provider-anthropic", ProviderProtocol.ANTHROPIC);
        LLMDomainService llms = mock(LLMDomainService.class);
        when(llms.getModelById("model-openai")).thenReturn(openAiModel);
        when(llms.getModelById("model-anthropic")).thenReturn(anthropicModel);
        when(llms.getProvider("provider-openai")).thenReturn(openAi);
        when(llms.getProvider("provider-anthropic")).thenReturn(anthropic);

        InMemoryStore store = new InMemoryStore();
        List<String> dispatched = new ArrayList<>();
        RunAppService service = new RunAppService(sessions, store, dispatched::add, agents, workspaces, llms);
        MockMvc api = MockMvcBuilders.standaloneSetup(new PortalAgentRunController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UserContext.setCurrentUserId("owner");

        String firstRunId = start(api, "使用第一版定义").path("id").asText();
        definition.replace("version-2", "2.0.0", "model-anthropic");
        String secondRunId = start(api, "使用第二版定义").path("id").asText();

        RunWorker worker = new RunWorker(store,
                run -> "执行版本=" + run.getAgentVersionNumber() + ",连接=" + run.getModelConnectionType());
        worker.process(firstRunId);
        worker.process(secondRunId);

        JsonNode first = detail(api, firstRunId);
        JsonNode second = detail(api, secondRunId);
        assertEquals("version-1", first.path("agentVersionId").asText());
        assertEquals("1.0.0", first.path("agentVersionNumber").asText());
        assertEquals("OPENAI", first.path("modelConnectionType").asText());
        assertEquals("执行版本=1.0.0,连接=OPENAI", first.path("result").asText());
        assertEquals("version-2", second.path("agentVersionId").asText());
        assertEquals("2.0.0", second.path("agentVersionNumber").asText());
        assertEquals("ANTHROPIC", second.path("modelConnectionType").asText());
        assertEquals("执行版本=2.0.0,连接=ANTHROPIC", second.path("result").asText());
        assertNotEquals(first.path("agentVersionId").asText(), second.path("agentVersionId").asText());
    }

    private JsonNode start(MockMvc api, String goal) throws Exception {
        String body = json.writeValueAsString(Map.of("goal", goal));
        String response = api
                .perform(post("/agents/sessions/session-1/runs").contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode envelope = json.readTree(response);
        assertEquals(200, envelope.path("code").asInt(), response);
        return envelope.path("data");
    }

    private JsonNode detail(MockMvc api, String runId) throws Exception {
        String response = api.perform(get("/agents/runs/" + runId)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode envelope = json.readTree(response);
        assertEquals(200, envelope.path("code").asInt(), response);
        return envelope.path("data");
    }

    private static AgentWorkspaceEntity workspace(String modelId) {
        LLMModelConfig config = new LLMModelConfig();
        config.setModelId(modelId);
        return new AgentWorkspaceEntity("agent-1", "owner", config);
    }

    private static ModelEntity model(String id, String providerId) {
        ModelEntity model = new ModelEntity();
        model.setId(id);
        model.setProviderId(providerId);
        model.setStatus(true);
        return model;
    }

    private static ProviderEntity provider(String id, ProviderProtocol protocol) {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(id);
        provider.setProtocol(protocol);
        provider.setStatus(true);
        provider.setIsOfficial(true);
        return provider;
    }

    private static final class MutablePublishedDefinition {
        private AgentVersionEntity version = version("version-1", "1.0.0");
        private String modelId = "model-openai";

        AgentVersionEntity version() {
            return version;
        }

        String modelId() {
            return modelId;
        }

        void replace(String id, String number, String modelId) {
            this.version = version(id, number);
            this.modelId = modelId;
        }

        private static AgentVersionEntity version(String id, String number) {
            AgentVersionEntity version = new AgentVersionEntity();
            version.setId(id);
            version.setAgentId("agent-1");
            version.setVersionNumber(number);
            version.setSystemPrompt("固定定义 " + number);
            return version;
        }
    }

    private static final class InMemoryStore implements RunStore {
        private final Map<String, RunEntity> runs = new LinkedHashMap<>();
        private final Map<String, List<RunStepEntity>> steps = new LinkedHashMap<>();

        @Override
        public synchronized void createRunWithStep(RunEntity run, RunStepEntity step) {
            runs.put(run.getId(), copy(run));
            steps.computeIfAbsent(step.getRunId(), ignored -> new ArrayList<>()).add(copy(step));
        }

        @Override
        public synchronized RunEntity findOwned(String runId, String userId) {
            RunEntity run = runs.get(runId);
            return run != null && userId.equals(run.getUserId()) ? copy(run) : null;
        }

        @Override
        public synchronized boolean cancelOwned(String runId, String userId) {
            RunEntity run = runs.get(runId);
            if (run == null || !userId.equals(run.getUserId()) || !isOpen(run.getStatus())) {
                return false;
            }
            run.cancel();
            steps.getOrDefault(runId, List.of()).forEach(step -> {
                if (isOpen(step.getStatus())) {
                    step.cancel("运行已取消");
                }
            });
            return true;
        }

        @Override
        public synchronized RunEntity findForWorker(String runId) {
            return copy(runs.get(runId));
        }

        @Override
        public synchronized boolean claim(String runId, String token, int leaseSeconds) {
            RunEntity run = runs.get(runId);
            if (run == null || !"PENDING".equals(run.getStatus())) {
                return false;
            }
            run.start();
            steps.get(runId).get(0).start();
            return true;
        }

        @Override
        public synchronized boolean renewLease(String runId, String token, int leaseSeconds) {
            RunEntity run = runs.get(runId);
            return run != null && "RUNNING".equals(run.getStatus());
        }

        @Override
        public synchronized boolean complete(String runId, String token, String answer) {
            RunEntity run = runs.get(runId);
            if (run == null || !"RUNNING".equals(run.getStatus())) {
                return false;
            }
            run.complete(answer);
            steps.get(runId).get(0).complete(answer);
            return true;
        }

        @Override
        public synchronized boolean fail(String runId, String token, String reason) {
            RunEntity run = runs.get(runId);
            if (run == null || !"RUNNING".equals(run.getStatus())) {
                return false;
            }
            run.fail(reason);
            steps.get(runId).get(0).fail(reason);
            return true;
        }

        @Override
        public synchronized List<String> recoverableRunIds(int limit) {
            return runs.values().stream().filter(run -> "PENDING".equals(run.getStatus())).limit(limit)
                    .map(RunEntity::getId).toList();
        }

        @Override
        public synchronized List<RunEntity> listOwnedBySession(String sessionId, String userId) {
            return runs.values().stream().filter(run -> sessionId.equals(run.getSessionId()))
                    .filter(run -> userId.equals(run.getUserId())).map(RunVersionPinningTest.InMemoryStore::copy)
                    .toList();
        }

        @Override
        public synchronized List<RunStepEntity> stepsForRun(String runId) {
            return steps.getOrDefault(runId, List.of()).stream().map(InMemoryStore::copy).toList();
        }

        @Override
        public boolean requestApproval(String runId, String token, RunApprovalEntity approval) {
            return false;
        }

        @Override
        public RunApprovalEntity findApprovalForRun(String runId) {
            return null;
        }

        @Override
        public boolean decideApproval(String runId, String approvalId, String userId, boolean approve) {
            return false;
        }

        @Override
        public boolean beginApprovedAction(String runId, String token, String approvalId) {
            return false;
        }

        @Override
        public boolean recordApprovedActionResult(String runId, String token, String approvalId,
                String externalResourceId) {
            return false;
        }

        @Override
        public boolean waitForReconciliation(String runId, String token) {
            return false;
        }

        private static RunEntity copy(RunEntity source) {
            if (source == null) {
                return null;
            }
            RunEntity target = RunEntity.create(source.getUserId(), source.getSessionId(), source.getAgentId(),
                    source.getGoal());
            target.setId(source.getId());
            target.setStatus(source.getStatus());
            target.setResult(source.getResult());
            target.setErrorMessage(source.getErrorMessage());
            target.setAgentVersionId(source.getAgentVersionId());
            target.setAgentVersionNumber(source.getAgentVersionNumber());
            target.setModelId(source.getModelId());
            target.setModelConnectionType(source.getModelConnectionType());
            target.setApprovalPolicy(source.getApprovalPolicy());
            target.setKnowledgeVersionId(source.getKnowledgeVersionId());
            target.setCreatedAt(source.getCreatedAt());
            target.setUpdatedAt(source.getUpdatedAt());
            return target;
        }

        private static boolean isOpen(String status) {
            return "PENDING".equals(status) || "RUNNING".equals(status) || "WAITING_APPROVAL".equals(status)
                    || "WAITING_RECONCILIATION".equals(status);
        }

        private static RunStepEntity copy(RunStepEntity source) {
            RunStepEntity target = new RunStepEntity();
            target.setId(source.getId());
            target.setRunId(source.getRunId());
            target.setSequenceNo(source.getSequenceNo());
            target.setKind(source.getKind());
            target.setStatus(source.getStatus());
            target.setResult(source.getResult());
            target.setErrorMessage(source.getErrorMessage());
            target.setCreatedAt(source.getCreatedAt());
            target.setUpdatedAt(source.getUpdatedAt());
            return target;
        }
    }
}
