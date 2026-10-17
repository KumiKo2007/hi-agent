package org.xhy.interfaces.api.portal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.github.service.GitHubApprovedActionReferenceValidator;
import org.xhy.application.run.service.RunAppService;
import org.xhy.application.run.service.RunExecution;
import org.xhy.application.run.service.RunModel;
import org.xhy.application.run.service.RunKnowledgeVersionResolver;
import org.xhy.application.run.service.RunWorker;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.repository.SessionRepository;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunReconciliationEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.config.GlobalExceptionHandler;
import org.xhy.interfaces.api.common.Result;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Public run and approval HTTP behavior across controller/worker reconstruction. */
class RunApprovalHttpTest {
    private static final String ARGUMENTS = "{\"repository\":\"KumiKo2007/hi-agent\",\"title\":\"Add release note\",\"body\":\"Mention approval recovery\"}";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
            Map.of("repository", Map.of("type", "string"), "title", Map.of("type", "string"), "body",
                    Map.of("type", "string")),
            "required", List.of("repository", "title", "body"), "additionalProperties", false);

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void should_waitForOwnerApprovalAndResumeSameFixedActionAfterControllerRestart() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc firstProcess = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(firstProcess);
        String runId = waiting.path("id").asText();
        JsonNode approval = waiting.path("approval");

        assertEquals("WAITING_APPROVAL", waiting.path("status").asText());
        assertEquals("PENDING", approval.path("status").asText());
        assertEquals("service-1", approval.path("serviceId").asText());
        assertEquals("create_issue", approval.path("actionName").asText());
        assertEquals("kumiko2007/hi-agent", approval.path("targetRepository").asText());
        assertEquals(ARGUMENTS, approval.path("arguments").asText());
        assertEquals(0, mcp.calls.size());

        // A new controller and worker can load and resolve the database-backed request.
        MockMvc restartedProcess = api(persistedStore, run -> "must not regenerate approved arguments", mcp);
        JsonNode stillWaiting = detail(restartedProcess, runId);
        assertEquals("PENDING", stillWaiting.path("approval").path("status").asText());
        assertEquals(ARGUMENTS, stillWaiting.path("approval").path("arguments").asText());

        JsonNode completed = data(restartedProcess
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}", runId, approval.path("id").asText())
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals("EXECUTED", completed.path("approval").path("status").asText());
        assertEquals("created issue 42", completed.path("approval").path("externalResourceId").asText());
        assertEquals("owner", completed.path("approval").path("decidedBy").asText());
        assertEquals("created issue 42", completed.path("result").asText());
        assertEquals(1, mcp.calls.size());
        assertEquals(ARGUMENTS, mcp.calls.get(0).arguments());
        assertEquals("kumiko2007/hi-agent", mcp.calls.get(0).targetRepository());
    }

    @Test
    void should_notCallMcpWhenOwnerDeniesApproval() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        JsonNode denied = data(api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}", waiting.path("id").asText(),
                        waiting.path("approval").path("id").asText()).contentType("application/json")
                        .content("{\"decision\":\"DENY\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("FAILED", denied.path("status").asText());
        assertEquals("DENIED", denied.path("approval").path("status").asText());
        assertEquals("外部写入审批被拒绝", denied.path("errorMessage").asText());
        assertEquals(0, mcp.calls.size());
        new RunWorker(persistedStore, proposingWrite(), mcp::execute).process(waiting.path("id").asText());
        assertEquals(0, mcp.calls.size());
    }

    @Test
    void should_notReplayApprovedWriteWhenRestartFindsUncertainExecution() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();
        mcp.crashAfterNextWrite = true;
        assertTrue(persistedStore.decideApproval(runId, approvalId, "owner", true));

        RunWorker firstWorker = new RunWorker(persistedStore, proposingWrite(), mcp::execute);
        assertThrows(SimulatedWorkerCrash.class, () -> firstWorker.process(runId));
        assertEquals(1, mcp.calls.size());

        persistedStore.expireLease(runId);
        RunWorker restartedWorker = new RunWorker(persistedStore, ignored -> {
            throw new AssertionError("The model must not regenerate approved parameters");
        }, mcp::execute);
        restartedWorker.process(runId);

        JsonNode reconciliation = detail(api, runId);
        assertEquals("WAITING_RECONCILIATION", reconciliation.path("status").asText());
        assertEquals("RECONCILIATION_REQUIRED", reconciliation.path("approval").path("status").asText());
        assertEquals("UNKNOWN", reconciliation.path("approval").path("reconciliationState").asText());
        assertTrue(reconciliation.path("approval").path("reconciliationReason").asText().contains("No external"));
        assertEquals(1, mcp.calls.size());
    }

    @Test
    void shouldRecordAMarkerVerifiedCreateAfterLostResponseWithoutCallingMcpAgain() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();
        assertTrue(persistedStore.decideApproval(runId, approvalId, "owner", true));
        mcp.crashAfterNextWrite = true;
        assertThrows(SimulatedWorkerCrash.class,
                () -> new RunWorker(persistedStore, proposingWrite(), mcp::execute).process(runId));
        persistedStore.expireLease(runId);

        new RunWorker(persistedStore, ignored -> {
            throw new AssertionError("reconciliation must not rerun the model");
        }, mcp::execute,
                (run, approval) -> org.xhy.application.run.service.ApprovedActionReconciliation
                        .created("https://github.com/kumiko2007/hi-agent/issues/42", "exact marker matched"))
                .process(runId);

        JsonNode completed = detail(api, runId);
        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals("https://github.com/kumiko2007/hi-agent/issues/42", completed.path("result").asText());
        assertEquals(approvalId, completed.path("approval").path("id").asText());
        assertEquals(1, mcp.calls.size());
        assertEquals(1, mcp.resourceCreates);
    }

    @Test
    void shouldRetrySamePersistedActionOnlyAfterOwnerProvidesEvidenceThatNoCreateOccurred() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();
        mcp.failBeforeCreateNextWrite = true;
        JsonNode uncertain = data(api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}", runId, approvalId)
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("WAITING_RECONCILIATION", uncertain.path("status").asText());
        assertEquals(0, mcp.resourceCreates);

        JsonNode completed = data(api.perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId,
                approvalId).contentType("application/json").content(
                        "{\"outcome\":\"CONFIRM_NOT_CREATED\",\"evidence\":\"MCP returned an explicit validation rejection before creating the Issue.\","
                                + "\"evidenceReference\":\"https://github.com/kumiko2007/hi-agent/issues/41\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals("EXECUTED", completed.path("approval").path("status").asText());
        assertEquals(approvalId, completed.path("approval").path("id").asText());
        assertEquals(2, mcp.calls.size());
        assertEquals(1, mcp.resourceCreates);
        assertEquals(mcp.calls.get(0), mcp.calls.get(1), "retry must reuse exact approved arguments and target");
        assertEquals("CONFIRM_NOT_CREATED", completed.path("approval").path("reconciliationOutcome").asText());
        assertEquals("owner", completed.path("approval").path("reconciliationActorId").asText());
        assertTrue(completed.path("approval").path("reconciliationEvidence").asText().contains("validation rejection"));
    }

    @Test
    void shouldRequireValidSameRepositoryEvidenceReferenceBeforeRetryingAnUncreatedWrite() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();
        mcp.failBeforeCreateNextWrite = true;
        api.perform(post("/agents/runs/{runId}/approvals/{approvalId}", runId, approvalId)
                .contentType("application/json").content("{\"decision\":\"APPROVE\"}"));
        assertEquals("WAITING_RECONCILIATION", detail(api, runId).path("status").asText());

        List<String> invalidReferences = List.of(
                "{\"outcome\":\"CONFIRM_NOT_CREATED\",\"evidence\":\"Reviewed repository activity\"}",
                "{\"outcome\":\"CONFIRM_NOT_CREATED\",\"evidence\":\"Reviewed repository activity\","
                        + "\"evidenceReference\":\"https://example.com/report/41\"}",
                "{\"outcome\":\"CONFIRM_NOT_CREATED\",\"evidence\":\"Reviewed repository activity\","
                        + "\"evidenceReference\":\"https://github.com/another/project/issues/41\"}",
                "{\"outcome\":\"CONFIRM_NOT_CREATED\",\"evidence\":\"Reviewed repository activity\","
                        + "\"evidenceReference\":\"https://github.com/kumiko2007/hi-agent/issues/41/\"}");
        for (String invalidReference : invalidReferences) {
            String response = api
                    .perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId, approvalId)
                            .contentType("application/json").content(invalidReference))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertNotEquals(200, json.readTree(response).path("code").asInt());
            assertEquals("WAITING_RECONCILIATION", detail(api, runId).path("status").asText());
        }
        String overlongReference = json.writeValueAsString(Map.of("outcome", "CONFIRM_NOT_CREATED", "evidence",
                "Reviewed repository activity", "evidenceReference", "a".repeat(513)));
        String overlongResponse = api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId, approvalId)
                        .contentType("application/json").content(overlongReference))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNotEquals(200, json.readTree(overlongResponse).path("code").asInt());
        assertEquals("WAITING_RECONCILIATION", detail(api, runId).path("status").asText());
        assertEquals(1, mcp.calls.size());
        assertEquals(0, mcp.resourceCreates);

        JsonNode completed = data(api.perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId,
                approvalId).contentType("application/json").content(
                        "{\"outcome\":\"CONFIRM_NOT_CREATED\",\"evidence\":\"A maintainer confirmed the request was rejected before creation.\","
                                + "\"evidenceReference\":\"https://github.com/kumiko2007/hi-agent/issues/41\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals(approvalId, completed.path("approval").path("id").asText());
        assertEquals("https://github.com/kumiko2007/hi-agent/issues/41",
                completed.path("approval").path("reconciliationEvidenceReference").asText());
        assertEquals("https://github.com/kumiko2007/hi-agent/issues/41",
                persistedStore.reconciliationEvents.get(0).getEvidenceReference());
        assertEquals(2, mcp.calls.size());
        assertEquals(1, mcp.resourceCreates);
    }

    @Test
    void shouldAcceptSupportedOpaqueEvidenceReferenceForOwnerRetry() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();
        mcp.failBeforeCreateNextWrite = true;
        api.perform(post("/agents/runs/{runId}/approvals/{approvalId}", runId, approvalId)
                .contentType("application/json").content("{\"decision\":\"APPROVE\"}"));

        JsonNode completed = data(api.perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId,
                approvalId).contentType("application/json").content(
                        "{\"outcome\":\"CONFIRM_NOT_CREATED\",\"evidence\":\"A provider delivery log confirms rejection.\","
                                + "\"evidenceReference\":\"delivery-log:record-52\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals("delivery-log:record-52",
                completed.path("approval").path("reconciliationEvidenceReference").asText());
        assertEquals("delivery-log:record-52", persistedStore.reconciliationEvents.get(0).getEvidenceReference());
        assertEquals(2, mcp.calls.size());
        assertEquals(1, mcp.resourceCreates);
    }

    @Test
    void shouldRecordAnOwnerVerifiedCreateOnlyForTheApprovedRepositoryWithoutAnotherMcpCall() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();
        mcp.failBeforeCreateNextWrite = true;
        api.perform(post("/agents/runs/{runId}/approvals/{approvalId}", runId, approvalId)
                .contentType("application/json").content("{\"decision\":\"APPROVE\"}"));

        String wrongRepository = api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId, approvalId)
                        .contentType("application/json")
                        .content("{\"outcome\":\"RECORD_CREATED\",\"evidence\":\"Reviewed repository activity\","
                                + "\"externalResourceId\":\"https://github.com/other/project/issues/42\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNotEquals(200, json.readTree(wrongRepository).path("code").asInt());
        assertEquals("WAITING_RECONCILIATION", detail(api, runId).path("status").asText());

        JsonNode completed = data(api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId, approvalId)
                        .contentType("application/json")
                        .content("{\"outcome\":\"RECORD_CREATED\",\"evidence\":\"Reviewed repository activity\","
                                + "\"externalResourceId\":\"https://github.com/kumiko2007/hi-agent/issues/42\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals("https://github.com/kumiko2007/hi-agent/issues/42", completed.path("result").asText());
        assertEquals("RECORD_CREATED", completed.path("approval").path("reconciliationOutcome").asText());
        assertEquals("owner", completed.path("approval").path("reconciliationActorId").asText());
        assertFalse(completed.path("approval").path("reconciliationAt").isMissingNode()
                || completed.path("approval").path("reconciliationAt").isNull());
        assertEquals(1, mcp.calls.size());
        assertEquals(0, mcp.resourceCreates);
    }

    @Test
    void shouldRequireOwnerAndEvidenceToStopWithoutDroppingTheUnresolvedApproval() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();
        mcp.failBeforeCreateNextWrite = true;
        api.perform(post("/agents/runs/{runId}/approvals/{approvalId}", runId, approvalId)
                .contentType("application/json").content("{\"decision\":\"APPROVE\"}"));

        UserContext.setCurrentUserId("other-user");
        String unauthorized = api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId, approvalId)
                        .contentType("application/json")
                        .content("{\"outcome\":\"STOP\",\"evidence\":\"owner decision\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(404, json.readTree(unauthorized).path("code").asInt());

        UserContext.setCurrentUserId("owner");
        String missingEvidence = api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId, approvalId)
                        .contentType("application/json").content("{\"outcome\":\"STOP\",\"evidence\":\" \"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNotEquals(200, json.readTree(missingEvidence).path("code").asInt());

        JsonNode stopped = data(api.perform(post("/agents/runs/{runId}/approvals/{approvalId}/reconciliation", runId,
                approvalId).contentType("application/json")
                .content("{\"outcome\":\"STOP\",\"evidence\":\"Owner stopped after reviewing GitHub activity.\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("CANCELLED", stopped.path("status").asText());
        assertEquals("RECONCILIATION_STOPPED", stopped.path("approval").path("status").asText());
        assertEquals("owner", stopped.path("approval").path("reconciliationActorId").asText());
        assertEquals(1, mcp.calls.size());
        assertEquals(0, mcp.resourceCreates);
        assertNotEquals("", stopped.path("approval").path("id").asText());
    }

    @Test
    void should_notAllowAnotherUserToDecideOrReadPendingApproval() throws Exception {
        MemoryRunStore persistedStore = new MemoryRunStore();
        ControlledMcpBoundary mcp = new ControlledMcpBoundary();
        MockMvc api = api(persistedStore, proposingWrite(), mcp);

        UserContext.setCurrentUserId("owner");
        JsonNode waiting = startAndReload(api);
        String runId = waiting.path("id").asText();
        String approvalId = waiting.path("approval").path("id").asText();

        UserContext.setCurrentUserId("other-user");
        JsonNode denied = json.readTree(api
                .perform(post("/agents/runs/{runId}/approvals/{approvalId}", runId, approvalId)
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode hidden = json.readTree(api.perform(get("/agents/runs/{runId}", runId)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertEquals(404, denied.path("code").asInt());
        assertEquals(404, hidden.path("code").asInt());
        assertEquals(0, mcp.calls.size());
        UserContext.setCurrentUserId("owner");
        assertEquals("PENDING", detail(api, runId).path("approval").path("status").asText());
    }

    private MockMvc api(MemoryRunStore store, RunModel model, ControlledMcpBoundary mcp) {
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        SessionRepository repository = (SessionRepository) Proxy.newProxyInstance(
                SessionRepository.class.getClassLoader(), new Class<?>[]{SessionRepository.class},
                (proxy, method, args) -> {
                    if ("selectOne".equals(method.getName())) {
                        return session;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        SessionDomainService sessions = new SessionDomainService(repository);
        RunWorker worker = new RunWorker(store, model, mcp::execute);
        RunAppService service = new RunAppService(sessions, store, worker::process, null, null, null,
                RunKnowledgeVersionResolver.none(), null,
                new GitHubApprovedActionReferenceValidator());
        return MockMvcBuilders.standaloneSetup(new PortalAgentRunController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private RunModel proposingWrite() {
        return new RunModel() {
            @Override
            public String execute(RunEntity run) {
                return "should not execute while approval is pending";
            }

            @Override
            public RunExecution executeWithActions(RunEntity run) {
                return RunExecution.awaitingApproval(new RunApprovalDraft("service-1", "create_issue",
                        "Create GitHub issue", "kumiko2007/hi-agent", ARGUMENTS, SCHEMA));
            }
        };
    }

    private JsonNode startAndReload(MockMvc api) throws Exception {
        String created = api
                .perform(post("/agents/sessions/session-1/runs").contentType("application/json")
                        .content("{\"goal\":\"Create an issue\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String runId = data(created).path("id").asText();
        return detail(api, runId);
    }

    private JsonNode detail(MockMvc api, String runId) throws Exception {
        return data(api.perform(get("/agents/runs/{runId}", runId)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode data(String response) throws Exception {
        JsonNode envelope = json.readTree(response);
        assertEquals(200, envelope.path("code").asInt(), response);
        return envelope.path("data");
    }

    private record McpCall(String serviceId, String actionName, String targetRepository, String arguments) {
    }

    private static final class ControlledMcpBoundary {
        private final List<McpCall> calls = new ArrayList<>();
        private boolean crashAfterNextWrite;
        private boolean failBeforeCreateNextWrite;
        private int resourceCreates;

        private String execute(RunEntity run, RunApprovalEntity approval) {
            calls.add(new McpCall(approval.getServiceId(), approval.getActionName(), approval.getTargetRepository(),
                    approval.getArguments()));
            if (failBeforeCreateNextWrite) {
                failBeforeCreateNextWrite = false;
                throw new IllegalStateException("simulated explicit rejection before create");
            }
            resourceCreates++;
            if (crashAfterNextWrite) {
                crashAfterNextWrite = false;
                throw new SimulatedWorkerCrash();
            }
            return "created issue 42";
        }
    }

    private static final class SimulatedWorkerCrash extends Error {
        private static final long serialVersionUID = 1L;
    }

    private static final class MemoryRunStore implements RunStore {
        private final Map<String, RunEntity> runs = new LinkedHashMap<>();
        private final Map<String, RunStepEntity> steps = new LinkedHashMap<>();
        private final Map<String, RunApprovalEntity> approvals = new LinkedHashMap<>();
        private final Map<String, String> owners = new LinkedHashMap<>();
        private final Map<String, Boolean> expired = new LinkedHashMap<>();
        private final List<RunReconciliationEntity> reconciliationEvents = new ArrayList<>();

        @Override
        public synchronized void createRunWithStep(RunEntity run, RunStepEntity step) {
            runs.put(run.getId(), copy(run));
            steps.put(run.getId(), copy(step));
        }

        @Override
        public synchronized boolean requestApproval(String runId, String token, RunApprovalEntity approval) {
            RunEntity run = runs.get(runId);
            if (run == null || !"RUNNING".equals(run.getStatus()) || !token.equals(owners.get(runId))) {
                return false;
            }
            run.waitForApproval();
            steps.get(runId).waitForApproval();
            approvals.put(runId, copy(approval));
            return true;
        }

        @Override
        public synchronized RunApprovalEntity findApprovalForRun(String runId) {
            return copy(approvals.get(runId));
        }

        @Override
        public synchronized boolean decideApproval(String runId, String approvalId, String userId, boolean approve) {
            RunEntity run = runs.get(runId);
            RunApprovalEntity approval = approvals.get(runId);
            if (run == null || approval == null || !userId.equals(run.getUserId())
                    || !"WAITING_APPROVAL".equals(run.getStatus()) || !approvalId.equals(approval.getId())
                    || !"PENDING".equals(approval.getStatus())) {
                return false;
            }
            approval.decide(approve, userId);
            if (approve) {
                run.setStatus("PENDING");
                steps.get(runId).setStatus("PENDING");
            } else {
                run.fail("外部写入审批被拒绝");
                steps.get(runId).fail("外部写入审批被拒绝");
            }
            return true;
        }

        @Override
        public synchronized boolean beginApprovedAction(String runId, String token, String approvalId) {
            RunEntity run = runs.get(runId);
            RunApprovalEntity approval = approvals.get(runId);
            if (run == null || approval == null || !"RUNNING".equals(run.getStatus())
                    || !token.equals(owners.get(runId)) || Boolean.TRUE.equals(expired.get(runId))
                    || !approvalId.equals(approval.getId()) || !"APPROVED".equals(approval.getStatus())) {
                return false;
            }
            approval.setStatus("EXECUTING");
            return true;
        }

        @Override
        public synchronized boolean recordApprovedActionResult(String runId, String token, String approvalId,
                String externalResourceId) {
            RunEntity run = runs.get(runId);
            RunApprovalEntity approval = approvals.get(runId);
            if (run == null || approval == null || !"RUNNING".equals(run.getStatus())
                    || !token.equals(owners.get(runId)) || Boolean.TRUE.equals(expired.get(runId))
                    || !approvalId.equals(approval.getId()) || !"EXECUTING".equals(approval.getStatus())
                    || externalResourceId == null || externalResourceId.isBlank()) {
                return false;
            }
            approval.recordExternalResource(externalResourceId);
            return true;
        }

        @Override
        public synchronized boolean waitForReconciliation(String runId, String token) {
            RunEntity run = runs.get(runId);
            RunApprovalEntity approval = approvals.get(runId);
            if (run == null || approval == null || !"RUNNING".equals(run.getStatus())
                    || !token.equals(owners.get(runId)) || Boolean.TRUE.equals(expired.get(runId))
                    || !"EXECUTING".equals(approval.getStatus())) {
                return false;
            }
            run.waitForReconciliation();
            steps.get(runId).waitForReconciliation();
            approval.requireReconciliation();
            owners.remove(runId);
            expired.remove(runId);
            return true;
        }

        @Override
        public synchronized boolean markReconciliationUnknown(String runId, String token, String approvalId,
                String reason) {
            if (!waitForReconciliation(runId, token)) {
                return false;
            }
            RunApprovalEntity approval = approvals.get(runId);
            if (!approvalId.equals(approval.getId())) {
                return false;
            }
            approval.recordReconciliation("UNKNOWN", "UNKNOWN", reason, reason, null);
            runs.get(runId).setErrorMessage(reason);
            return true;
        }

        @Override
        public synchronized boolean resolveReconciliation(String runId, String approvalId, String userId,
                String outcome, String evidence, String evidenceReference, String externalResourceId) {
            RunEntity run = runs.get(runId);
            RunApprovalEntity approval = approvals.get(runId);
            if (run == null || approval == null || !userId.equals(run.getUserId())
                    || !"WAITING_RECONCILIATION".equals(run.getStatus()) || !approvalId.equals(approval.getId())
                    || !"RECONCILIATION_REQUIRED".equals(approval.getStatus())) {
                return false;
            }
            switch (outcome) {
                case "RECORD_CREATED" -> {
                    approval.setStatus("EXECUTED");
                    approval.setExternalResourceId(externalResourceId);
                    approval.recordReconciliation("RESOLVED", outcome, null, evidence, evidenceReference, userId);
                    run.setStatus("PENDING");
                    steps.get(runId).setStatus("PENDING");
                }
                case "CONFIRM_NOT_CREATED" -> {
                    approval.setStatus("APPROVED");
                    approval.recordReconciliation("VERIFIED_NOT_CREATED", outcome, null, evidence, evidenceReference,
                            userId);
                    run.setStatus("PENDING");
                    steps.get(runId).setStatus("PENDING");
                }
                case "STOP" -> {
                    approval.setStatus("RECONCILIATION_STOPPED");
                    approval.recordReconciliation("RESOLVED", outcome, evidence, evidence, evidenceReference, userId);
                    run.setStatus("CANCELLED");
                    run.setErrorMessage(evidence);
                    steps.get(runId).cancel(evidence);
                }
                default -> throw new IllegalArgumentException(outcome);
            }
            reconciliationEvents.add(RunReconciliationEntity.create(runId, approvalId, outcome, userId, evidence,
                    evidenceReference, externalResourceId));
            return true;
        }

        @Override
        public synchronized RunEntity findOwned(String runId, String userId) {
            RunEntity run = runs.get(runId);
            return run != null && userId.equals(run.getUserId()) ? copy(run) : null;
        }

        @Override
        public synchronized RunEntity findForWorker(String runId) {
            return copy(runs.get(runId));
        }

        @Override
        public synchronized List<RunEntity> listOwnedBySession(String sessionId, String userId) {
            return runs.values().stream().filter(run -> sessionId.equals(run.getSessionId()))
                    .filter(run -> userId.equals(run.getUserId())).map(MemoryRunStore::copy).toList();
        }

        @Override
        public synchronized List<RunStepEntity> stepsForRun(String runId) {
            RunStepEntity step = steps.get(runId);
            return step == null ? List.of() : List.of(copy(step));
        }

        @Override
        public synchronized boolean cancelOwned(String runId, String userId) {
            RunEntity run = runs.get(runId);
            if (run == null || !userId.equals(run.getUserId())
                    || !("PENDING".equals(run.getStatus()) || "RUNNING".equals(run.getStatus())
                            || "WAITING_APPROVAL".equals(run.getStatus()))
                    || (approvals.containsKey(runId) && "EXECUTING".equals(approvals.get(runId).getStatus()))) {
                return false;
            }
            run.cancel();
            steps.get(runId).cancel("运行已取消");
            return true;
        }

        @Override
        public synchronized boolean claim(String runId, String token, int leaseSeconds) {
            RunEntity run = runs.get(runId);
            if (run == null || !("PENDING".equals(run.getStatus())
                    || ("RUNNING".equals(run.getStatus()) && Boolean.TRUE.equals(expired.get(runId))))) {
                return false;
            }
            run.start();
            steps.get(runId).start();
            owners.put(runId, token);
            expired.put(runId, false);
            return true;
        }

        private synchronized void expireLease(String runId) {
            expired.put(runId, true);
        }

        @Override
        public boolean renewLease(String runId, String token, int leaseSeconds) {
            return token.equals(owners.get(runId));
        }

        @Override
        public synchronized boolean complete(String runId, String token, String answer) {
            if (!"RUNNING".equals(runs.get(runId).getStatus()) || !token.equals(owners.get(runId))) {
                return false;
            }
            runs.get(runId).complete(answer);
            steps.get(runId).complete(answer);
            return true;
        }

        @Override
        public synchronized boolean fail(String runId, String token, String reason) {
            if (!"RUNNING".equals(runs.get(runId).getStatus()) || !token.equals(owners.get(runId))) {
                return false;
            }
            runs.get(runId).fail(reason);
            steps.get(runId).fail(reason);
            return true;
        }

        @Override
        public synchronized List<String> recoverableRunIds(int limit) {
            return runs.values().stream().filter(run -> "PENDING".equals(run.getStatus())).limit(limit)
                    .map(RunEntity::getId).toList();
        }

        private static RunEntity copy(RunEntity run) {
            if (run == null) {
                return null;
            }
            RunEntity result = new RunEntity();
            BeanUtils.copyProperties(run, result);
            return result;
        }

        private static RunStepEntity copy(RunStepEntity step) {
            if (step == null) {
                return null;
            }
            RunStepEntity result = new RunStepEntity();
            BeanUtils.copyProperties(step, result);
            return result;
        }

        private static RunApprovalEntity copy(RunApprovalEntity approval) {
            if (approval == null) {
                return null;
            }
            RunApprovalEntity result = new RunApprovalEntity();
            BeanUtils.copyProperties(approval, result);
            return result;
        }
    }
}
