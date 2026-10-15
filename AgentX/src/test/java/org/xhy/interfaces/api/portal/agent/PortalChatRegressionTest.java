package org.xhy.interfaces.api.portal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.agent.service.AgentSessionAppService;
import org.xhy.application.billing.service.BillingService;
import org.xhy.application.conversation.service.ChatSessionManager;
import org.xhy.application.conversation.service.ConversationAppService;
import org.xhy.application.conversation.service.RagSessionManager;
import org.xhy.application.conversation.service.handler.MessageHandlerFactory;
import org.xhy.application.conversation.service.message.agent.AgentMessageHandler;
import org.xhy.application.conversation.service.message.agent.AgentToolManager;
import org.xhy.application.conversation.service.message.builtin.BuiltInToolRegistry;
import org.xhy.application.trace.collector.TraceCollector;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.model.AgentWorkspaceEntity;
import org.xhy.domain.agent.model.LLMModelConfig;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.agent.service.AgentWorkspaceDomainService;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.ContextDomainService;
import org.xhy.domain.conversation.service.ConversationDomainService;
import org.xhy.domain.conversation.service.MessageDomainService;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.llm.model.HighAvailabilityResult;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
import org.xhy.domain.llm.service.HighAvailabilityDomainService;
import org.xhy.domain.llm.service.LLMDomainService;
import org.xhy.domain.memory.service.MemoryDomainService;
import org.xhy.domain.memory.service.MemoryExtractorService;
import org.xhy.domain.token.service.TokenDomainService;
import org.xhy.domain.tool.service.UserToolDomainService;
import org.xhy.domain.user.model.AccountEntity;
import org.xhy.domain.user.service.AccountDomainService;
import org.xhy.domain.user.service.UserSettingsDomainService;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.llm.LLMServiceFactory;
import org.xhy.infrastructure.transport.MessageTransportFactory;
import org.xhy.infrastructure.transport.SseMessageTransport;
import org.xhy.infrastructure.transport.SyncMessageTransport;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 现有聊天 HTTP/SSE 链路的确定性回归测试，不依赖数据库、模型密钥或网络服务。 */
class PortalChatRegressionTest {

    private static final String USER_ID = "regression-user";
    private static final String SESSION_ID = "regression-session";
    private static final String AGENT_ID = "regression-agent";
    private static final String ANSWER = "这是一条确定性回复";

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @Timeout(30)
    void should_returnOneCompletedSseResponseFromTheModel() throws Exception {
        SessionDomainService sessions = mock(SessionDomainService.class);
        AgentDomainService agents = mock(AgentDomainService.class);
        AgentWorkspaceDomainService workspaces = mock(AgentWorkspaceDomainService.class);
        LLMDomainService llms = mock(LLMDomainService.class);
        ContextDomainService contexts = mock(ContextDomainService.class);
        MessageDomainService messages = mock(MessageDomainService.class);
        UserSettingsDomainService settings = mock(UserSettingsDomainService.class);
        HighAvailabilityDomainService availability = mock(HighAvailabilityDomainService.class);
        LLMServiceFactory models = mock(LLMServiceFactory.class);
        BuiltInToolRegistry builtInTools = mock(BuiltInToolRegistry.class);
        AgentToolManager agentTools = mock(AgentToolManager.class);
        AccountDomainService accounts = mock(AccountDomainService.class);
        ChatSessionManager activeSessions = new ChatSessionManager();

        AccountEntity account = new AccountEntity();
        account.setBalance(BigDecimal.ONE);
        when(accounts.getOrCreateAccount(USER_ID)).thenReturn(account);

        SessionEntity session = new SessionEntity();
        session.setId(SESSION_ID);
        session.setAgentId(AGENT_ID);
        when(sessions.getSession(SESSION_ID, USER_ID)).thenReturn(session);

        AgentEntity agent = new AgentEntity();
        agent.setId(AGENT_ID);
        agent.setUserId(USER_ID);
        agent.setEnabled(true);
        agent.setSystemPrompt("你是测试助手");
        when(agents.getAgentById(AGENT_ID)).thenReturn(agent);

        LLMModelConfig modelConfig = new LLMModelConfig();
        modelConfig.setModelId("model-id");
        when(workspaces.getWorkspace(AGENT_ID, USER_ID))
                .thenReturn(new AgentWorkspaceEntity(AGENT_ID, USER_ID, modelConfig));

        ModelEntity model = new ModelEntity();
        model.setId("model-id");
        model.setModelId("fake-model");
        model.setModelEndpoint("fake-model");
        model.setProviderId("provider-id");
        model.setStatus(true);
        when(llms.findModelById("model-id")).thenReturn(model);

        ProviderEntity provider = new ProviderEntity();
        provider.setId("provider-id");
        provider.setName("fake-provider");
        provider.setStatus(true);
        provider.setIsOfficial(false);
        when(llms.getProvider("provider-id")).thenReturn(provider);
        when(availability.selectBestProvider(eq(model), eq(USER_ID), eq(SESSION_ID), any()))
                .thenReturn(new HighAvailabilityResult(provider, model, "fake-instance"));
        when(models.getStreamingClient(provider, model)).thenReturn(new DeterministicModel());
        when(agentTools.getAvailableTools(any())).thenReturn(List.of());

        AgentMessageHandler handler = new AgentMessageHandler(models, messages, availability, sessions, settings, llms,
                builtInTools, mock(BillingService.class), accounts, activeSessions, mock(TraceCollector.class),
                agentTools);
        ReflectionTestUtils.setField(handler, "memoryDomainService", mock(MemoryDomainService.class));
        ReflectionTestUtils.setField(handler, "memoryExtractorService", mock(MemoryExtractorService.class));

        MessageHandlerFactory handlers = mock(MessageHandlerFactory.class);
        when(handlers.getHandler(any(org.xhy.application.conversation.dto.ChatRequest.class))).thenReturn(handler);

        ConversationAppService service = new ConversationAppService(mock(ConversationDomainService.class), sessions,
                agents, workspaces, llms, contexts, mock(TokenDomainService.class), messages, handlers,
                new MessageTransportFactory(new SseMessageTransport(), mock(SyncMessageTransport.class)),
                mock(UserToolDomainService.class), settings, null, availability, mock(RagSessionManager.class),
                activeSessions);
        PortalAgentSessionController controller = new PortalAgentSessionController(mock(AgentSessionAppService.class),
                service, activeSessions);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        UserContext.setCurrentUserId(USER_ID);
        MvcResult result = mvc.perform(post("/agents/sessions/chat").contentType("application/json")
                .content("{\"sessionId\":\"regression-session\",\"message\":\"你好\"}")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentType().startsWith("text/event-stream"));
        String stream = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<JsonNode> events = parseEvents(stream);
        JsonNode expectedEvents;
        try (InputStream fixture = Objects.requireNonNull(getClass().getResourceAsStream("/contracts/chat-sse.json"))) {
            expectedEvents = new ObjectMapper().readTree(fixture);
        }
        assertTrue(expectedEvents.isArray());
        assertEquals(2, expectedEvents.size());
        assertEquals(expectedEvents.size(), events.size(), stream);
        for (int index = 0; index < events.size(); index++) {
            JsonNode actual = events.get(index);
            JsonNode expected = expectedEvents.get(index);
            assertEquals(expected.path("content"), actual.path("content"), stream);
            assertEquals(expected.path("done"), actual.path("done"), stream);
            assertEquals(expected.path("messageType"), actual.path("messageType"), stream);
            assertTrue(actual.path("timestamp").isIntegralNumber(), stream);
        }
    }

    private static List<JsonNode> parseEvents(String stream) {
        ObjectMapper mapper = new ObjectMapper();
        return Arrays.stream(stream.split("\\n\\n")).filter(line -> line.startsWith("data:")).map(line -> {
            try {
                return mapper.readTree(line.substring(5));
            } catch (Exception error) {
                throw new AssertionError("SSE 事件不是有效 JSON: " + line, error);
            }
        }).toList();
    }

    @Test
    @Timeout(30)
    void should_rejectBlankMessage() throws Exception {
        PortalAgentSessionController controller = new PortalAgentSessionController(mock(AgentSessionAppService.class),
                mock(ConversationAppService.class), new ChatSessionManager());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult result = mvc.perform(post("/agents/sessions/chat").contentType("application/json")
                .content("{\"sessionId\":\"regression-session\",\"message\":\"  \"}")).andReturn();

        assertEquals(400, result.getResponse().getStatus());
    }

    private static class DeterministicModel implements StreamingChatModel {
        /** {@inheritDoc} */
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            assertTrue(request.messages().stream().anyMatch(message -> message.toString().contains("你好")));
            handler.onPartialResponse(ANSWER);
            handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(new AiMessage(ANSWER)).tokenUsage(new TokenUsage(3, 4)).build());
        }
    }
}
