package org.xhy.domain.run.repository;

import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;

import java.util.List;

/** 持久化运行及其步骤的边界。 */
public interface RunStore {
    void createRunWithStep(RunEntity run, RunStepEntity step);
    void updateRunAndStep(RunEntity run, RunStepEntity step);
    RunEntity findOwned(String runId, String userId);
    List<RunEntity> listOwnedBySession(String sessionId, String userId);
    List<RunStepEntity> stepsForRun(String runId);
}
