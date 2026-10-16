package org.xhy.domain.run.repository;

import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;

import java.util.List;

/** 持久化运行及其步骤的边界。 */
public interface RunStore {
    void createRunWithStep(RunEntity run, RunStepEntity step);
    RunEntity findOwned(String runId, String userId);
    RunEntity findForWorker(String runId);
    List<RunEntity> listOwnedBySession(String sessionId, String userId);
    List<RunStepEntity> stepsForRun(String runId);

    /** 以数据库原子更新取得运行的限时所有权。 */
    boolean claim(String runId, String token, int leaseSeconds);

    /** 仅当前所有者可以续约，过期的令牌不能复活。 */
    boolean renewLease(String runId, String token, int leaseSeconds);

    /** 只有当前所有者可提交最终状态与步骤。 */
    boolean complete(String runId, String token, String answer);
    boolean fail(String runId, String token, String reason);

    /** 从权威状态找出漏发或租约已过期的运行。 */
    List<String> recoverableRunIds(int limit);
}
