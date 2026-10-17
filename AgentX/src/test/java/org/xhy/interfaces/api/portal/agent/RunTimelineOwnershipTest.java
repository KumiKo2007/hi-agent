package org.xhy.interfaces.api.portal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.run.service.ApprovedActionReconciliation;
import org.xhy.application.run.service.RunAppService;
import org.xhy.application.run.service.RunKnowledgeVersionResolver;
import org.xhy.application.run.service.RunTimelineEvents;
import org.xhy.application.run.service.RunWorker;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.model.RunTimelineEventEntity;
import org.xhy.domain.run.model.RunFailureCode;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.domain.run.repository.RunTimelineRepository;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.config.GlobalExceptionHandler;
import org.xhy.infrastructure.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunTimelineOwnershipTest {
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldReturnChronologicalEventsToOwnerWithoutQueryingTimelineForForeignOwner() throws Exception {
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        SessionDomainService sessions = new SessionDomainService(null) {
            @Override
            public SessionEntity getSession(String sessionId, String userId) {
                return session;
            }
        };
        Store store = new Store();
        Timeline timeline = new Timeline();
        RunWorker worker = new RunWorker(store.proxy(),
                run -> "owner result\nCookie: answerSentinelabcdefghijklmnopqrstuvwxyz", (run, approval) -> "resource",
                (run, approval) -> ApprovedActionReconciliation.unknown("unknown"), timeline);
        RunAppService service = new RunAppService(sessions, store.proxy(), worker::process, null, null, null,
                RunKnowledgeVersionResolver.none(), null, null, timeline);
        MockMvc api = MockMvcBuilders.standaloneSetup(new PortalAgentRunController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        UserContext.setCurrentUserId("owner");
        String createdBody = api
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/agents/sessions/session-1/runs").contentType("application/json")
                        .content("{\"goal\":\"trace this run\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String runId = json.readTree(createdBody).path("data").path("id").asText();
        JsonNode owner = json.readTree(api
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/agents/runs/" + runId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");

        assertEquals(List.of("RUN_CREATED", "RUN_ATTEMPT_STARTED", "RUN_COMPLETED"),
                eventTypes(owner.path("timeline")));
        assertEquals(runId, owner.path("timeline").get(0).path("runId").asText());
        assertEquals(owner.path("steps").get(0).path("id").asText(),
                owner.path("timeline").get(2).path("stepId").asText());
        assertEquals("owner result\nCookie: [REDACTED]", owner.path("result").asText());
        assertEquals(false, owner.toString().contains("answerSentinelabcdefghijklmnopqrstuvwxyz"));
        int ownerReadCount = timeline.reads;

        UserContext.setCurrentUserId("outsider");
        JsonNode foreign = json.readTree(api
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/agents/runs/" + runId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals(404, foreign.path("code").asInt());
        assertEquals(ownerReadCount, timeline.reads);
    }

    @Test
    void shouldNotWriteCredentialSentinelsFromModelFailuresToLogsOrTimeline() {
        String cookieSentinel = "cookieFailureSentinelabcdefghijklmnopqrstuvwxyz";
        String headerSentinel = "userAgentFailureSentinelabcdefghijklmnopqrstuvwxyz";
        Store store = new Store();
        Timeline timeline = new Timeline();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "safe goal");
        store.proxy().createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(RunWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);

        try {
            RunWorker worker = new RunWorker(store.proxy(), ignored -> {
                throw new BusinessException(
                        "Cookie: " + cookieSentinel + " headers={\"User-Agent\":\"" + headerSentinel + "\"}");
            }, (ignoredRun, ignoredApproval) -> "resource",
                    (ignoredRun, ignoredApproval) -> ApprovedActionReconciliation.unknown("unknown"), timeline);
            worker.process(run.getId());

            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",
                    (all, message) -> all + message);
            String serializedTimeline = timeline.events.stream().map(RunTimelineEventEntity::getDetailsJson).reduce("",
                    (all, details) -> all + details);
            assertEquals("FAILED", store.runs.get(run.getId()).getStatus());
            assertFalse(logs.contains(cookieSentinel));
            assertFalse(logs.contains(headerSentinel));
            assertFalse(serializedTimeline.contains(cookieSentinel));
            assertFalse(serializedTimeline.contains(headerSentinel));
            assertTrue(timeline.events.stream().anyMatch(event -> "RUN_FAILED".equals(event.getEventType())
                    && event.getDetailsJson().contains(RunFailureCode.EXECUTION_FAILED.name())));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldKeepTimelineInBothAlreadyCancelledResponses() {
        Store alreadyCancelledStore = new Store();
        Timeline alreadyCancelledTimeline = new Timeline();
        RunEntity alreadyCancelled = RunEntity.create("owner", "session-1", "agent-1", "cancelled");
        RunStepEntity alreadyCancelledStep = RunStepEntity.modelCall(alreadyCancelled.getId());
        alreadyCancelledStore.proxy().createRunWithStep(alreadyCancelled, alreadyCancelledStep);
        alreadyCancelled.cancel();
        alreadyCancelledTimeline
                .append(RunTimelineEvents.runCancelled(alreadyCancelled.getId(), alreadyCancelledStep.getId()));
        RunAppService alreadyCancelledService = new RunAppService(null, alreadyCancelledStore.proxy(), ignored -> {
        }, null, null, null, RunKnowledgeVersionResolver.none(), null, null, alreadyCancelledTimeline);

        var alreadyCancelledResponse = alreadyCancelledService.cancel(alreadyCancelled.getId(), "owner");
        assertEquals(List.of("RUN_CANCELLED"),
                alreadyCancelledResponse.getTimeline().stream().map(event -> event.eventType()).toList());

        Store racedStore = new Store();
        Timeline racedTimeline = new Timeline();
        RunEntity racedRun = RunEntity.create("owner", "session-1", "agent-1", "cancelled during request");
        RunStepEntity racedStep = RunStepEntity.modelCall(racedRun.getId());
        racedStore.proxy().createRunWithStep(racedRun, racedStep);
        racedTimeline.append(RunTimelineEvents.runCreated(racedRun.getId(), racedStep.getId()));
        racedTimeline.append(RunTimelineEvents.runCancelled(racedRun.getId(), racedStep.getId()));
        racedStore.cancelDuringFailedAttempt = true;
        RunAppService racedService = new RunAppService(null, racedStore.proxy(), ignored -> {
        }, null, null, null, RunKnowledgeVersionResolver.none(), null, null, racedTimeline);

        var racedResponse = racedService.cancel(racedRun.getId(), "owner");
        assertEquals(List.of("RUN_CREATED", "RUN_CANCELLED"),
                racedResponse.getTimeline().stream().map(event -> event.eventType()).toList());
    }

    private static List<String> eventTypes(JsonNode events) {
        List<String> types = new ArrayList<>();
        events.forEach(event -> types.add(event.path("eventType").asText()));
        return types;
    }

    private static final class Timeline implements RunTimelineRepository {
        private final List<RunTimelineEventEntity> events = new ArrayList<>();
        private int reads;

        @Override
        public void append(RunTimelineEventEntity event) {
            events.add(event);
        }
        @Override
        public List<RunTimelineEventEntity> eventsForRun(String runId) {
            reads++;
            return events.stream().filter(event -> runId.equals(event.getRunId())).toList();
        }
    }

    private static final class Store {
        private final Map<String, RunEntity> runs = new LinkedHashMap<>();
        private final Map<String, RunStepEntity> steps = new LinkedHashMap<>();
        private boolean cancelDuringFailedAttempt;

        private RunStore proxy() {
            return RunStore.class.cast(Proxy.newProxyInstance(RunStore.class.getClassLoader(),
                    new Class<?>[]{RunStore.class}, (ignored, method, args) -> switch (method.getName()) {
                        case "createRunWithStep" -> {
                            runs.put(((RunEntity) args[0]).getId(), (RunEntity) args[0]);
                            steps.put(((RunStepEntity) args[1]).getRunId(), (RunStepEntity) args[1]);
                            yield null;
                        }
                        case "findOwned" -> {
                            RunEntity run = runs.get(args[0]);
                            yield run != null && args[1].equals(run.getUserId()) ? run : null;
                        }
                        case "findForWorker" -> runs.get(args[0]);
                        case "stepsForRun" -> {
                            RunStepEntity step = steps.get(args[0]);
                            yield step == null ? List.of() : List.of(step);
                        }
                        case "findApprovalForRun" -> null;
                        case "claim" -> {
                            RunEntity run = runs.get(args[0]);
                            if (run == null || !"PENDING".equals(run.getStatus()))
                                yield false;
                            run.start();
                            steps.get(args[0]).start();
                            yield true;
                        }
                        case "complete" -> {
                            RunEntity run = runs.get(args[0]);
                            if (run == null || !"RUNNING".equals(run.getStatus()))
                                yield false;
                            run.complete((String) args[2]);
                            steps.get(args[0]).complete((String) args[2]);
                            yield true;
                        }
                        case "fail" -> {
                            RunEntity run = runs.get(args[0]);
                            if (run == null || !"RUNNING".equals(run.getStatus()))
                                yield false;
                            run.fail((String) args[2]);
                            steps.get(args[0]).fail((String) args[2]);
                            yield true;
                        }
                        case "cancelOwned" -> {
                            if (cancelDuringFailedAttempt)
                                runs.get(args[0]).cancel();
                            yield false;
                        }
                        case "renewLease" -> true;
                        default -> defaultValue(method.getReturnType());
                    }));
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class)
                return false;
            if (type == int.class)
                return 0;
            if (type == long.class)
                return 0L;
            return null;
        }
    }
}
