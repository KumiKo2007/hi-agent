package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final class MemoryStore implements RunStore {
        private final Map<String, RunEntity> runs = new LinkedHashMap<>();
        private final Map<String, RunStepEntity> steps = new LinkedHashMap<>();
        private final Map<String, String> owners = new LinkedHashMap<>();
        private final Map<String, Boolean> expired = new LinkedHashMap<>();
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
        public synchronized List<RunEntity> listOwnedBySession(String sessionId, String userId) {
            return runs.values().stream().filter(run -> sessionId.equals(run.getSessionId()))
                    .filter(run -> userId.equals(run.getUserId())).map(MemoryStore::copy).toList();
        }

        @Override
        public synchronized List<RunStepEntity> stepsForRun(String runId) {
            return List.of(copy(steps.get(runId)));
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
            if (!renewLease(runId, token, 90)) {
                return false;
            }
            runs.get(runId).complete(answer);
            steps.get(runId).complete(answer);
            return true;
        }

        @Override
        public synchronized boolean fail(String runId, String token, String reason) {
            if (!renewLease(runId, token, 90)) {
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
    }
}
