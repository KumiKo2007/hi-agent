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
    @Transactional
    public void updateRunAndStep(RunEntity run, RunStepEntity step) {
        runs.updateById(run);
        steps.updateById(step);
    }

    @Override
    public RunEntity findOwned(String runId, String userId) {
        return runs.selectOne(Wrappers.<RunEntity>lambdaQuery().eq(RunEntity::getId, runId)
                .eq(RunEntity::getUserId, userId));
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
}
