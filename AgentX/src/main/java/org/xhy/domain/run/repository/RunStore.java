package org.xhy.domain.run.repository;

import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunStepEntity;

import java.util.List;

/** 持久化运行及其步骤的边界。 */
public interface RunStore {
    void createRunWithStep(RunEntity run, RunStepEntity step);
    RunEntity findOwned(String runId, String userId);
    RunEntity findForWorker(String runId);
    List<RunEntity> listOwnedBySession(String sessionId, String userId);
    List<RunStepEntity> stepsForRun(String runId);

    /** Atomically pauses the current worker and persists the exact write request. */
    boolean requestApproval(String runId, String token, RunApprovalEntity approval);

    /** Persists an automatic approval while the current worker still owns the run lease. */
    default boolean requestPreauthorizedAction(String runId, String token, RunApprovalEntity approval) {
        return false;
    }

    RunApprovalEntity findApprovalForRun(String runId);

    /** Records one owner decision and moves approved work back to the durable queue. */
    boolean decideApproval(String runId, String approvalId, String userId, boolean approve);

    /** Fences a one-time approved action before invoking the external side effect. */
    boolean beginApprovedAction(String runId, String token, String approvalId);

    /** Persists the successful external resource handle before the worker submits its final run result. */
    boolean recordApprovedActionResult(String runId, String token, String approvalId, String externalResourceId);

    /** Persists a positive, marker-backed reconciliation without invoking the write executor. */
    default boolean recordReconciledCreated(String runId, String token, String approvalId, String externalResourceId,
            String evidence) {
        return recordApprovedActionResult(runId, token, approvalId, externalResourceId);
    }

    /** Leaves the run waiting and makes an UNKNOWN check ineligible for automatic resend. */
    default boolean markReconciliationUnknown(String runId, String token, String approvalId, String reason) {
        return waitForReconciliation(runId, token);
    }

    /** Requeues the same persisted intent only after authoritative evidence proves no write occurred. */
    default boolean retryAfterVerifiedNotCreated(String runId, String token, String approvalId, String evidence) {
        return false;
    }

    /** Owner-only terminal or retry decision. RETRY is allowed only with non-empty evidence. */
    default boolean resolveReconciliation(String runId, String approvalId, String userId, String outcome,
            String evidence, String evidenceReference, String externalResourceId) {
        return false;
    }

    /** Stops retrying when a worker restarts after an approved side effect may have begun. */
    boolean waitForReconciliation(String runId, String token);

    /** Pauses safely when the model-visible MCP schema no longer matches execution-time state. */
    default boolean waitForSchemaReview(String runId, String token) {
        return false;
    }

    /** 只有运行所有者可以取消尚未完成的运行。 */
    boolean cancelOwned(String runId, String userId);

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
