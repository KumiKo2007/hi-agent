package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;

import java.util.List;

/** PostgreSQL 中的运行权威记录。 */
@Repository
public class MyBatisRunStore implements RunStore {
    private final RunMapper runs;
    private final RunStepMapper steps;

    public MyBatisRunStore(RunMapper runs, RunStepMapper steps) {
        this.runs = runs;
        this.steps = steps;
    }

    @Override
    @Transactional
    public void createRunWithStep(RunEntity run, RunStepEntity step) {
        runs.insert(run);
        steps.insert(step);
    }

    @Override
    public RunEntity findOwned(String runId, String userId) {
        return runs.selectOne(
                Wrappers.<RunEntity>lambdaQuery().eq(RunEntity::getId, runId).eq(RunEntity::getUserId, userId));
    }

    @Override
    public RunEntity findForWorker(String runId) {
        return runs.selectById(runId);
    }

    @Override
    public List<RunEntity> listOwnedBySession(String sessionId, String userId) {
        return runs.selectList(Wrappers.<RunEntity>lambdaQuery().eq(RunEntity::getSessionId, sessionId)
                .eq(RunEntity::getUserId, userId).orderByDesc(RunEntity::getCreatedAt));
    }

    @Override
    public List<RunStepEntity> stepsForRun(String runId) {
        return steps.selectList(Wrappers.<RunStepEntity>lambdaQuery().eq(RunStepEntity::getRunId, runId)
                .orderByAsc(RunStepEntity::getSequenceNo));
    }

    @Override
    @Transactional
    public boolean claim(String runId, String token, int leaseSeconds) {
        if (runs.claim(runId, token, leaseSeconds) == 0) {
            return false;
        }
        if (steps.startFirstStep(runId) != 1) {
            throw new IllegalStateException("运行缺少模型步骤");
        }
        return true;
    }

    @Override
    public boolean renewLease(String runId, String token, int leaseSeconds) {
        return runs.renewLease(runId, token, leaseSeconds) == 1;
    }

    @Override
    @Transactional
    public boolean complete(String runId, String token, String answer) {
        if (runs.complete(runId, token, answer) == 0) {
            return false;
        }
        if (steps.completeFirstStep(runId, answer) != 1) {
            throw new IllegalStateException("运行缺少模型步骤");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean fail(String runId, String token, String reason) {
        if (runs.fail(runId, token, reason) == 0) {
            return false;
        }
        if (steps.failFirstStep(runId, reason) != 1) {
            throw new IllegalStateException("运行缺少模型步骤");
        }
        return true;
    }

    @Override
    public List<String> recoverableRunIds(int limit) {
        return runs.recoverableRunIds(limit);
    }
}
