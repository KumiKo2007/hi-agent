package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.application.tool.service.McpSchemaMismatchException;
import org.xhy.application.tool.service.McpToolExecutionException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 通过提交、读取和派发边界验证运行可恢复行为。 */
class RunDeliveryTest {

    @Test
    void should_completeSamePersistedRunAfterWorkerRestartAndDuplicateDelivery() {
        MemoryStore database = new MemoryStore();
        List<String> deliveries = new ArrayList<>();
        SessionDomainService sessions = mock(SessionDomainService.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);
        AtomicInteger modelCalls = new AtomicInteger();
        RunModel model = run -> {
            modelCalls.incrementAndGet();
            return "完成：" + run.getGoal();
        };

        RunAppService api = new RunAppService(sessions, database, deliveries::add);
        String runId = api.start("session-1", "处理资料", "owner").getId();
        assertEquals("PENDING", api.detail(runId, "owner").getStatus());
        assertEquals(List.of(runId), deliveries);

        // 创建运行的进程已经消失；新进程只凭数据库与队列消息继续。
        RunWorker restartedWorker = new RunWorker(database, model);
        restartedWorker.process(runId);
        restartedWorker.process(runId);

        assertEquals("COMPLETED", api.detail(runId, "owner").getStatus());
        assertEquals("完成：处理资料", api.detail(runId, "owner").getResult());
        assertEquals(1, modelCalls.get());
        assertEquals(1, api.detail(runId, "owner").getSteps().size());
    }

    @Test
    void should_requeuePersistedWorkWhenFirstDeliveryWasLost() {
        MemoryStore database = new MemoryStore();
        SessionDomainService sessions = mock(SessionDomainService.class);
        SessionEntity session = new SessionEntity();
        session.setId("session-1");
        session.setAgentId("agent-1");
        when(sessions.getSession("session-1", "owner")).thenReturn(session);
        RunAppService api = new RunAppService(sessions, database, ignored -> {
            throw new IllegalStateException("RabbitMQ 暂时不可用");
        });

        String runId = api.start("session-1", "处理资料", "owner").getId();
        assertEquals("PENDING", api.detail(runId, "owner").getStatus());
        List<String> recovered = new ArrayList<>();
        new RunRecoveryService(database, recovered::add).recover();
        assertEquals(List.of(runId), recovered);
        new RunWorker(database, run -> "结果").process(recovered.get(0));
        assertEquals("COMPLETED", api.detail(runId, "owner").getStatus());
    }

    @Test
    void shouldPersistCreatorPreauthorizationBeforeOneTimeExternalExecution() {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "创建 issue");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        RunApprovalDraft draft = new RunApprovalDraft("github", "create_issue", "创建 issue", "owner/repo",
                "{\"title\":\"hello\"}", Map.of("type", "object"));
        AtomicInteger executorCalls = new AtomicInteger();
        RunWorker worker = new RunWorker(database, new RunModel() {
            @Override
            public String execute(RunEntity ignored) {
                throw new AssertionError("action-aware execution should be used");
            }

            @Override
            public RunExecution executeWithActions(RunEntity ignored) {
                return RunExecution.preauthorizedWrite(draft);
            }
        }, (claimedRun, approval) -> {
            executorCalls.incrementAndGet();
            assertEquals("EXECUTING", database.findApprovalForRun(claimedRun.getId()).getStatus());
            assertEquals("CREATOR_PREAUTHORIZATION", approval.getApprovalSource());
            assertEquals("owner", approval.getDecidedBy());
            return "issue created";
        });

        worker.process(run.getId());
        worker.process(run.getId());

        RunApprovalEntity persisted = database.findApprovalForRun(run.getId());
        assertEquals(1, executorCalls.get());
        assertEquals("EXECUTED", persisted.getStatus());
        assertEquals("CREATOR_PREAUTHORIZATION", persisted.getApprovalSource());
        assertEquals("issue created", persisted.getExternalResourceId());
        assertEquals("COMPLETED", database.findOwned(run.getId(), "owner").getStatus());
        assertEquals("issue created", database.findOwned(run.getId(), "owner").getResult());
    }

    @Test
    void shouldReusePersistedExternalResourceAfterWorkerRestartWithoutRepeatingTheWrite() {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "创建 issue");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        assertTrue(database.claim(run.getId(), "first-worker", 90));
        RunApprovalDraft draft = new RunApprovalDraft("github", "create_issue", "创建 issue", "owner/repo",
                "{\"title\":\"hello\"}", Map.of("type", "object"));
        RunApprovalEntity approval = RunApprovalEntity.createPreauthorized(run.getId(), draft, "owner");
        assertTrue(database.requestPreauthorizedAction(run.getId(), "first-worker", approval));
        assertTrue(database.beginApprovedAction(run.getId(), "first-worker", approval.getId()));
        assertTrue(database.recordApprovedActionResult(run.getId(), "first-worker", approval.getId(), "issue-42"));
        database.expireLease(run.getId());
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger executorCalls = new AtomicInteger();

        new RunWorker(database, ignored -> {
            modelCalls.incrementAndGet();
            throw new AssertionError("persisted external result must be reused");
        }, (ignoredRun, ignoredApproval) -> {
            executorCalls.incrementAndGet();
            throw new AssertionError("persisted external result must not trigger another write");
        }).process(run.getId());

        assertEquals(0, modelCalls.get());
        assertEquals(0, executorCalls.get());
        assertEquals("issue-42", database.findOwned(run.getId(), "owner").getResult());
        assertEquals("COMPLETED", database.findOwned(run.getId(), "owner").getStatus());
    }

    @Test
    void shouldReconcileAnApprovedWriteWhenMcpReturnsAToolError() {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "创建 issue");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        RunApprovalDraft draft = new RunApprovalDraft("github", "create_issue", "创建 issue", "owner/repo", "{}",
                Map.of("type", "object"));
        AtomicInteger calls = new AtomicInteger();
        RunWorker worker = new RunWorker(database, new RunModel() {
            @Override
            public String execute(RunEntity ignored) {
                throw new AssertionError("action-aware execution should be used");
            }

            @Override
            public RunExecution executeWithActions(RunEntity ignored) {
                return RunExecution.preauthorizedWrite(draft);
            }
        }, (claimedRun, approval) -> {
            calls.incrementAndGet();
            throw new McpToolExecutionException();
        });

        worker.process(run.getId());

        assertEquals(1, calls.get());
        assertEquals("WAITING_RECONCILIATION", database.findOwned(run.getId(), "owner").getStatus());
        assertEquals("RECONCILIATION_REQUIRED", database.findApprovalForRun(run.getId()).getStatus());
    }

    @Test
    void shouldRequireAnExplicitResolutionInsteadOfPlainCancellationForAnUncertainWrite() {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "创建 issue");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        RunApprovalDraft draft = new RunApprovalDraft("github", "create_issue", "创建 issue", "owner/repo", "{}",
                Map.of("type", "object"));
        new RunWorker(database, new RunModel() {
            @Override
            public String execute(RunEntity ignored) {
                throw new AssertionError("action-aware execution should be used");
            }

            @Override
            public RunExecution executeWithActions(RunEntity ignored) {
                return RunExecution.preauthorizedWrite(draft);
            }
        }, (claimedRun, approval) -> {
            throw new McpToolExecutionException();
        }).process(run.getId());

        assertEquals("WAITING_RECONCILIATION", database.findOwned(run.getId(), "owner").getStatus());
        assertFalse(database.cancelOwned(run.getId(), "owner"));

        assertEquals("WAITING_RECONCILIATION", database.findOwned(run.getId(), "owner").getStatus());
        assertEquals("RECONCILIATION_REQUIRED", database.findApprovalForRun(run.getId()).getStatus());
    }

    @Test
    void should_fenceStaleWorkerAfterLeaseIsReclaimed() throws Exception {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "处理资料");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        RunWorker oldWorker = new RunWorker(database, ignored -> {
            oldStarted.countDown();
            try {
                if (!releaseOld.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("测试模型等待超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return "过期结果";
        });
        Thread oldThread = new Thread(() -> oldWorker.process(run.getId()));
        oldThread.start();
        assertEquals(true, oldStarted.await(5, TimeUnit.SECONDS));
        AtomicInteger duplicateCalls = new AtomicInteger();
        new RunWorker(database, ignored -> {
            duplicateCalls.incrementAndGet();
            return "重复结果";
        }).process(run.getId());
        assertEquals(0, duplicateCalls.get());

        // 租约到期后新工作者接管；原工作者晚到的结果不得覆盖新结果。
        database.expireLease(run.getId());
        new RunWorker(database, ignored -> "有效结果").process(run.getId());
        releaseOld.countDown();
        oldThread.join(5000);
        assertEquals("有效结果", database.findOwned(run.getId(), "owner").getResult());
        assertEquals("有效结果", database.stepsForRun(run.getId()).get(0).getResult());
    }

    @Test
    void should_recoverWhenFinalDatabaseCommitFailsInsteadOfRecordingModelFailure() {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "处理资料");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        database.failNextCompletion = true;

        RunWorker worker = new RunWorker(database, ignored -> "模型已回答");
        assertThrows(IllegalStateException.class, () -> worker.process(run.getId()));
        assertEquals("RUNNING", database.findOwned(run.getId(), "owner").getStatus());

        database.expireLease(run.getId());
        worker.process(run.getId());
        assertEquals("COMPLETED", database.findOwned(run.getId(), "owner").getStatus());
    }

    @Test
    void should_fenceAnActiveWorkerAfterOwnerCancelsTheRun() throws Exception {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "取消执行中的目标");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        CountDownLatch modelStarted = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        Thread workerThread = new Thread(() -> new RunWorker(database, ignored -> {
            modelStarted.countDown();
            try {
                assertTrue(releaseModel.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return "过期结果";
        }).process(run.getId()));
        workerThread.start();

        assertTrue(modelStarted.await(5, TimeUnit.SECONDS));
        assertTrue(database.cancelOwned(run.getId(), "owner"));
        releaseModel.countDown();
        workerThread.join(5000);

        assertEquals("CANCELLED", database.findOwned(run.getId(), "owner").getStatus());
        assertNull(database.findOwned(run.getId(), "owner").getResult());
        assertEquals("CANCELLED", database.stepsForRun(run.getId()).get(0).getStatus());
    }

    @Test
    void shouldPersistSpecificSchemaDriftPauseInsteadOfGenericModelFailure() {
        MemoryStore database = new MemoryStore();
        RunEntity run = RunEntity.create("owner", "session-1", "agent-1", "查询资料");
        database.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));

        new RunWorker(database, ignored -> {
            throw new McpSchemaMismatchException();
        }).process(run.getId());

        assertEquals("WAITING_SCHEMA_REVIEW", database.findOwned(run.getId(), "owner").getStatus());
        assertEquals(McpSchemaMismatchException.USER_MESSAGE,
                database.findOwned(run.getId(), "owner").getErrorMessage());
        assertEquals("WAITING_SCHEMA_REVIEW", database.stepsForRun(run.getId()).get(0).getStatus());
    }

    private static final class MemoryStore implements RunStore {
        private final Map<String, RunEntity> runs = new LinkedHashMap<>();
        private final Map<String, RunStepEntity> steps = new LinkedHashMap<>();
        private final Map<String, String> owners = new LinkedHashMap<>();
        private final Map<String, Boolean> expired = new LinkedHashMap<>();
        private final Map<String, RunApprovalEntity> approvals = new LinkedHashMap<>();
        private boolean failNextCompletion;

        @Override
        public synchronized void createRunWithStep(RunEntity run, RunStepEntity step) {
            runs.put(run.getId(), copy(run));
            steps.put(run.getId(), copy(step));
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
        public synchronized boolean cancelOwned(String runId, String userId) {
            RunEntity run = runs.get(runId);
            if (run == null || !userId.equals(run.getUserId())
                    || !("PENDING".equals(run.getStatus()) || "RUNNING".equals(run.getStatus())
                            || "WAITING_APPROVAL".equals(run.getStatus())
                            || "WAITING_SCHEMA_REVIEW".equals(run.getStatus()))) {
                return false;
            }
            run.cancel();
            steps.get(runId).cancel("运行已取消");
            return true;
        }

        @Override
        public synchronized List<RunEntity> listOwnedBySession(String sessionId, String userId) {
            return runs.values().stream().filter(run -> sessionId.equals(run.getSessionId()))
                    .filter(run -> userId.equals(run.getUserId())).map(MemoryStore::copy).toList();
        }

        @Override
        public synchronized List<RunStepEntity> stepsForRun(String runId) {
            return List.of(copy(steps.get(runId)));
        }

        @Override
        public boolean requestApproval(String runId, String token, RunApprovalEntity approval) {
            return false;
        }

        @Override
        public synchronized boolean requestPreauthorizedAction(String runId, String token, RunApprovalEntity approval) {
            if (!token.equals(owners.get(runId)) || approvals.containsKey(runId)
                    || !"RUNNING".equals(runs.get(runId).getStatus())) {
                return false;
            }
            approvals.put(runId, copy(approval));
            return true;
        }

        @Override
        public synchronized RunApprovalEntity findApprovalForRun(String runId) {
            return copy(approvals.get(runId));
        }

        @Override
        public boolean decideApproval(String runId, String approvalId, String userId, boolean approve) {
            return false;
        }

        @Override
        public synchronized boolean beginApprovedAction(String runId, String token, String approvalId) {
            RunApprovalEntity approval = approvals.get(runId);
            if (approval == null || !token.equals(owners.get(runId)) || !approvalId.equals(approval.getId())
                    || !"APPROVED".equals(approval.getStatus()) || !"RUNNING".equals(runs.get(runId).getStatus())) {
                return false;
            }
            approval.setStatus("EXECUTING");
            return true;
        }

        @Override
        public synchronized boolean recordApprovedActionResult(String runId, String token, String approvalId,
                String externalResourceId) {
            RunApprovalEntity approval = approvals.get(runId);
            if (approval == null || !token.equals(owners.get(runId)) || !approvalId.equals(approval.getId())
                    || !"EXECUTING".equals(approval.getStatus()) || !"RUNNING".equals(runs.get(runId).getStatus())
                    || externalResourceId == null || externalResourceId.isBlank()) {
                return false;
            }
            approval.recordExternalResource(externalResourceId);
            return true;
        }

        @Override
        public boolean waitForReconciliation(String runId, String token) {
            synchronized (this) {
                if (!"RUNNING".equals(runs.get(runId).getStatus()) || !renewLease(runId, token, 90)) {
                    return false;
                }
                runs.get(runId).waitForReconciliation();
                steps.get(runId).waitForReconciliation();
                RunApprovalEntity approval = approvals.get(runId);
                if (approval != null && "EXECUTING".equals(approval.getStatus())) {
                    approval.requireReconciliation();
                }
                owners.remove(runId);
                return true;
            }
        }

        @Override
        public synchronized boolean waitForSchemaReview(String runId, String token) {
            if (!"RUNNING".equals(runs.get(runId).getStatus()) || !renewLease(runId, token, 90)) {
                return false;
            }
            runs.get(runId).setStatus("WAITING_SCHEMA_REVIEW");
            runs.get(runId).setErrorMessage(McpSchemaMismatchException.USER_MESSAGE);
            steps.get(runId).setStatus("WAITING_SCHEMA_REVIEW");
            steps.get(runId).setErrorMessage(McpSchemaMismatchException.USER_MESSAGE);
            RunApprovalEntity approval = approvals.get(runId);
            if (approval != null && "EXECUTING".equals(approval.getStatus())) {
                approval.setStatus("SCHEMA_REVIEW_REQUIRED");
            }
            owners.remove(runId);
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

        @Override
        public synchronized boolean renewLease(String runId, String token, int leaseSeconds) {
            return token.equals(owners.get(runId)) && !Boolean.TRUE.equals(expired.get(runId));
        }

        @Override
        public synchronized boolean complete(String runId, String token, String answer) {
            if (failNextCompletion) {
                failNextCompletion = false;
                throw new IllegalStateException("数据库提交暂时失败");
            }
            if (!"RUNNING".equals(runs.get(runId).getStatus()) || !renewLease(runId, token, 90)) {
                return false;
            }
            runs.get(runId).complete(answer);
            steps.get(runId).complete(answer);
            return true;
        }

        @Override
        public synchronized boolean fail(String runId, String token, String reason) {
            if (!"RUNNING".equals(runs.get(runId).getStatus()) || !renewLease(runId, token, 90)) {
                return false;
            }
            runs.get(runId).fail(reason);
            steps.get(runId).fail(reason);
            return true;
        }

        @Override
        public synchronized List<String> recoverableRunIds(int limit) {
            return runs.values().stream()
                    .filter(run -> "PENDING".equals(run.getStatus())
                            || ("RUNNING".equals(run.getStatus()) && Boolean.TRUE.equals(expired.get(run.getId()))))
                    .limit(limit).map(RunEntity::getId).toList();
        }

        void expireLease(String runId) {
            expired.put(runId, true);
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
