package org.xhy.interfaces.api.portal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.run.service.RunAppService;
import org.xhy.application.run.service.AgentRunModel;
import org.xhy.domain.agent.model.AgentEntity;
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
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.config.GlobalExceptionHandler;
import org.xhy.infrastructure.llm.LLMServiceFactory;
import org.xhy.infrastructure.llm.protocol.enums.ProviderProtocol;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 目标运行的公开 HTTP 行为测试。 */
class PortalAgentRunControllerTest {

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @Timeout(30)
    void should_keepTwoGoalsAndTheirStepsSeparateAfterReload() throws Exception {
        SessionDomainService sessions = mock(SessionDomainService.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setUserId("owner");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);

        InMemoryStore persistedStore = new InMemoryStore();
        MockMvc createApi = api(sessions, persistedStore);
        UserContext.setCurrentUserId("owner");
        JsonNode first = postGoal(createApi, "第一项目标");
        JsonNode second = postGoal(createApi, "第二项目标");

        assertNotEquals(first.path("id").asText(), second.path("id").asText());
        assertEquals("COMPLETED", first.path("status").asText());
        assertEquals("COMPLETED", second.path("status").asText());

        // 新控制器与应用服务重新读取同一持久存储，等价于浏览器刷新后的读取路径。
        MockMvc readApi = api(sessions, persistedStore);
        JsonNode list = data(readApi.perform(get("/agents/sessions/session-1/runs")).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(2, list.size());

        JsonNode firstDetail = data(readApi.perform(get("/agents/runs/" + first.path("id").asText())).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode secondDetail = data(readApi.perform(get("/agents/runs/" + second.path("id").asText())).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("session-1", firstDetail.path("sessionId").asText());
        assertEquals("回答：第一项目标", firstDetail.path("result").asText());
        assertEquals("回答：第二项目标", secondDetail.path("result").asText());
        assertEquals(1, firstDetail.path("steps").size());
        assertEquals(1, secondDetail.path("steps").size());
        assertEquals(firstDetail.path("id").asText(), firstDetail.path("steps").get(0).path("runId").asText());
        assertEquals(secondDetail.path("id").asText(), secondDetail.path("steps").get(0).path("runId").asText());
    }

    @Test
    @Timeout(30)
    void should_hideAnotherUsersRun() throws Exception {
        SessionDomainService sessions = mock(SessionDomainService.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);
        MockMvc api = api(sessions, new InMemoryStore());

        UserContext.setCurrentUserId("owner");
        String runId = postGoal(api, "私有目标").path("id").asText();
        UserContext.setCurrentUserId("outsider");
        String response = api.perform(get("/agents/runs/" + runId)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode envelope = json.readTree(response);

        assertEquals(404, envelope.path("code").asInt());
        assertEquals(0, envelope.path("data").size());
    }

    @Test
    @Timeout(30)
    void should_persistFailureWithoutExposingModelErrorDetails() throws Exception {
        SessionDomainService sessions = mock(SessionDomainService.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);
        InMemoryStore store = new InMemoryStore();
        RunAppService service = new RunAppService(sessions, store, run -> {
            throw new IllegalStateException("secret-provider-key");
        });
        MockMvc api = MockMvcBuilders.standaloneSetup(new PortalAgentRunController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        UserContext.setCurrentUserId("owner");

        JsonNode failed = postGoal(api, "无法完成的目标");
        JsonNode reloaded = data(api.perform(get("/agents/runs/" + failed.path("id").asText())).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("FAILED", reloaded.path("status").asText());
        assertEquals("模型执行失败", reloaded.path("errorMessage").asText());
        assertEquals("FAILED", reloaded.path("steps").get(0).path("status").asText());
        assertEquals(false, reloaded.toString().contains("secret-provider-key"));
    }

    @ParameterizedTest
    @EnumSource(ProviderProtocol.class)
    @Timeout(30)
    void should_completeGoalThroughConfiguredModelProtocol(ProviderProtocol protocol) throws Exception {
        SessionDomainService sessions = mock(SessionDomainService.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);

        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setUserId("owner");
        agent.setSystemPrompt("你是测试助手");
        AgentDomainService agents = mock(AgentDomainService.class);
        when(agents.getAgentById("agent-1")).thenReturn(agent);

        LLMModelConfig config = new LLMModelConfig();
        config.setModelId("model-1");
        AgentWorkspaceDomainService workspaces = mock(AgentWorkspaceDomainService.class);
        when(workspaces.getWorkspace("agent-1", "owner"))
                .thenReturn(new AgentWorkspaceEntity("agent-1", "owner", config));

        ModelEntity model = new ModelEntity();
        model.setId("model-1");
        model.setProviderId("provider-1");
        model.setStatus(true);
        ProviderEntity provider = new ProviderEntity();
        provider.setId("provider-1");
        provider.setProtocol(protocol);
        provider.setStatus(true);
        provider.setIsOfficial(true);
        LLMDomainService llms = mock(LLMDomainService.class);
        when(llms.getModelById("model-1")).thenReturn(model);
        when(llms.getProvider("provider-1")).thenReturn(provider);

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(org.mockito.ArgumentMatchers.<ChatMessage>anyList()))
                .thenReturn(ChatResponse.builder().aiMessage(new AiMessage("模型答案")).build());
        LLMServiceFactory models = mock(LLMServiceFactory.class);
        when(models.getStrandClient(provider, model)).thenReturn(chatModel);

        RunAppService service = new RunAppService(sessions, new InMemoryStore(),
                new AgentRunModel(agents, workspaces, llms, models));
        MockMvc api = MockMvcBuilders.standaloneSetup(new PortalAgentRunController(service)).build();
        UserContext.setCurrentUserId("owner");

        JsonNode response = postGoal(api, "解释项目现状");
        assertEquals("COMPLETED", response.path("status").asText());
        assertEquals("模型答案", response.path("result").asText());
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(messages.capture());
        assertEquals(2, messages.getValue().size());
        assertEquals(true, messages.getValue().toString().contains("解释项目现状"));
        verify(models).getStrandClient(provider, model);
    }

    private MockMvc api(SessionDomainService sessions, InMemoryStore store) {
        RunAppService service = new RunAppService(sessions, store, run -> "回答：" + run.getGoal());
        return MockMvcBuilders.standaloneSetup(new PortalAgentRunController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private JsonNode postGoal(MockMvc api, String goal) throws Exception {
        String body = json.writeValueAsString(Map.of("goal", goal));
        String response = api.perform(post("/agents/sessions/session-1/runs").contentType("application/json")
                .content(body)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return data(response);
    }

    private JsonNode data(String response) throws Exception {
        JsonNode envelope = json.readTree(response);
        assertEquals(200, envelope.path("code").asInt(), response);
        return envelope.path("data");
    }

    private static final class InMemoryStore implements RunStore {
        private final Map<String, RunEntity> runs = new LinkedHashMap<>();
        private final Map<String, List<RunStepEntity>> steps = new LinkedHashMap<>();

        @Override
        public void createRunWithStep(RunEntity run, RunStepEntity step) {
            runs.put(run.getId(), copy(run));
            steps.computeIfAbsent(step.getRunId(), ignored -> new ArrayList<>()).add(copy(step));
        }

        @Override
        public void updateRunAndStep(RunEntity run, RunStepEntity step) {
            runs.put(run.getId(), copy(run));
            List<RunStepEntity> runSteps = steps.get(step.getRunId());
            for (int index = 0; index < runSteps.size(); index++) {
                if (step.getId().equals(runSteps.get(index).getId())) {
                    runSteps.set(index, copy(step));
                    return;
                }
            }
            throw new IllegalArgumentException("步骤不存在");
        }

        @Override
        public RunEntity findOwned(String runId, String userId) {
            RunEntity run = runs.get(runId);
            return run != null && userId.equals(run.getUserId()) ? copy(run) : null;
        }

        @Override
        public List<RunEntity> listOwnedBySession(String sessionId, String userId) {
            return runs.values().stream().filter(run -> sessionId.equals(run.getSessionId()))
                    .filter(run -> userId.equals(run.getUserId())).map(InMemoryStore::copy).toList();
        }

        @Override
        public List<RunStepEntity> stepsForRun(String runId) {
            return steps.getOrDefault(runId, List.of()).stream().map(InMemoryStore::copy).toList();
        }

        private static RunEntity copy(RunEntity run) {
            RunEntity snapshot = new RunEntity();
            BeanUtils.copyProperties(run, snapshot);
            return snapshot;
        }

        private static RunStepEntity copy(RunStepEntity step) {
            RunStepEntity snapshot = new RunStepEntity();
            BeanUtils.copyProperties(step, snapshot);
            return snapshot;
        }
    }
}
