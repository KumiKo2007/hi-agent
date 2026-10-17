package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunReconciliationEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.application.tool.service.McpSchemaMismatchException;

import java.util.List;
import java.util.Objects;

/** PostgreSQL 中的运行权威记录。 */
@Repository
public class MyBatisRunStore implements RunStore {
    private final RunMapper runs;
    private final RunStepMapper steps;
    private final RunApprovalMapper approvals;
    private final RunReconciliationMapper reconciliations;

    @Autowired
    public MyBatisRunStore(RunMapper runs, RunStepMapper steps, RunApprovalMapper approvals,
            RunReconciliationMapper reconciliations) {
        this.runs = runs;
        this.steps = steps;
        this.approvals = approvals;
        this.reconciliations = Objects.requireNonNull(reconciliations, "RunReconciliationMapper is required");
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
    public boolean requestApproval(String runId, String token, RunApprovalEntity approval) {
        if (runs.waitForApproval(runId, token) == 0) {
            return false;
        }
        approvals.insert(approval);
        if (steps.waitFirstStepForApproval(runId) != 1) {
            throw new IllegalStateException("运行步骤未能进入审批等待状态");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean requestPreauthorizedAction(String runId, String token, RunApprovalEntity approval) {
        if (runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        approvals.insert(approval);
        return true;
    }

    @Override
    public RunApprovalEntity findApprovalForRun(String runId) {
        return approvals.findByRunId(runId);
    }

    @Override
    @Transactional
    public boolean decideApproval(String runId, String approvalId, String userId, boolean approve) {
        int transitioned = approve ? runs.approveOwned(runId, userId) : runs.denyOwned(runId, userId);
        if (transitioned == 0) {
            return false;
        }
        String status = approve ? "APPROVED" : "DENIED";
        if (approvals.decidePending(runId, approvalId, userId, status) != 1) {
            throw new IllegalStateException("审批记录与运行状态不一致");
        }
        int stepChanged = approve ? steps.reopenFirstStepAfterApproval(runId) : steps.failFirstStep(runId, "外部写入审批被拒绝");
        if (stepChanged != 1) {
            throw new IllegalStateException("运行步骤未能记录审批决定");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean beginApprovedAction(String runId, String token, String approvalId) {
        if (runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        return approvals.beginExecution(runId, token, approvalId) == 1;
    }

    @Override
    @Transactional
    public boolean recordApprovedActionResult(String runId, String token, String approvalId,
            String externalResourceId) {
        if (externalResourceId == null || externalResourceId.isBlank()) {
            return false;
        }
        if (runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        return approvals.recordExternalResource(runId, token, approvalId, externalResourceId) == 1;
    }

    @Override
    @Transactional
    public boolean recordReconciledCreated(String runId, String token, String approvalId, String externalResourceId,
            String evidence) {
        if (externalResourceId == null || externalResourceId.isBlank() || runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        if (approvals.recordReconciledCreated(runId, approvalId, externalResourceId, evidence) != 1) {
            return false;
        }
        audit(runId, approvalId, "VERIFIED_CREATED", null, evidence, null, externalResourceId);
        return true;
    }

    @Override
    @Transactional
    public boolean markReconciliationUnknown(String runId, String token, String approvalId, String reason) {
        if (reason == null || reason.isBlank() || runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        if (runs.waitForReconciliation(runId, token, reason) != 1) {
            return false;
        }
        if (approvals.markReconciliationUnknown(runId, approvalId, reason) != 1) {
            throw new IllegalStateException("核对结果未能保存");
        }
        if (steps.waitFirstStepForReconciliation(runId, reason) != 1) {
            throw new IllegalStateException("运行步骤未能进入结果核对状态");
        }
        audit(runId, approvalId, "UNKNOWN", null, reason, null, null);
        return true;
    }

    @Override
    @Transactional
    public boolean retryAfterVerifiedNotCreated(String runId, String token, String approvalId, String evidence) {
        if (evidence == null || evidence.isBlank() || runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        if (approvals.recordVerifiedNotCreated(runId, approvalId, evidence) != 1) {
            return false;
        }
        if (runs.retryAfterVerifiedNotCreated(runId, token) != 1 || steps.requeueFirstStepAfterCheck(runId) != 1) {
            throw new IllegalStateException("已核实未创建，但运行未能重新排队");
        }
        audit(runId, approvalId, "VERIFIED_NOT_CREATED", null, evidence, null, null);
        return true;
    }

    @Override
    @Transactional
    public boolean resolveReconciliation(String runId, String approvalId, String userId, String outcome,
            String evidence, String evidenceReference, String externalResourceId) {
        if (runs.lockOwnedWaitingReconciliation(runId, userId) == null) {
            return false;
        }
        RunApprovalEntity approval = approvals.findByRunId(runId);
        if (approval == null || !approvalId.equals(approval.getId())
                || !"RECONCILIATION_REQUIRED".equals(approval.getStatus())) {
            return false;
        }
        boolean recorded;
        switch (outcome) {
            case "RECORD_CREATED" -> {
                if (externalResourceId == null || externalResourceId.isBlank()) {
                    return false;
                }
                recorded = approvals.resolveCreated(runId, approvalId, userId, externalResourceId, evidence,
                        evidenceReference) == 1;
                if (recorded && runs.resumeReconciliation(runId, userId) == 1) {
                    if (steps.reopenFirstStepAfterReconciliation(runId) != 1) {
                        throw new IllegalStateException("运行步骤未能恢复");
                    }
                } else if (recorded) {
                    throw new IllegalStateException("运行未能恢复");
                }
            }
            case "CONFIRM_NOT_CREATED" -> {
                recorded = approvals.resolveNotCreated(runId, approvalId, userId, evidence, evidenceReference) == 1;
                if (recorded && runs.resumeReconciliation(runId, userId) == 1) {
                    if (steps.reopenFirstStepAfterReconciliation(runId) != 1) {
                        throw new IllegalStateException("运行步骤未能恢复");
                    }
                } else if (recorded) {
                    throw new IllegalStateException("运行未能恢复");
                }
            }
            case "STOP" -> {
                recorded = approvals.resolveStop(runId, approvalId, userId, evidence, evidenceReference) == 1;
                if (recorded && runs.stopReconciliation(runId, userId, evidence) == 1) {
                    if (steps.stopFirstStepAfterReconciliation(runId, evidence) != 1) {
                        throw new IllegalStateException("运行步骤未能停止");
                    }
                } else if (recorded) {
                    throw new IllegalStateException("运行未能停止");
                }
            }
            default -> throw new IllegalArgumentException("未知的结果核对决定");
        }
        if (!recorded) {
            return false;
        }
        audit(runId, approvalId, outcome, userId, evidence, evidenceReference, externalResourceId);
        return true;
    }

    @Override
    @Transactional
    public boolean waitForReconciliation(String runId, String token) {
        if (runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        if (runs.waitForReconciliation(runId, token, "外部写入结果不确定，正在核对外部结果") == 0) {
            return false;
        }
        if (approvals.requireReconciliation(runId) != 1) {
            throw new IllegalStateException("审批记录未能进入结果核对状态");
        }
        if (steps.waitFirstStepForReconciliation(runId, "外部写入结果不确定，正在核对外部结果") != 1) {
            throw new IllegalStateException("运行步骤未能进入结果核对状态");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean waitForSchemaReview(String runId, String token) {
        if (runs.lockActiveLease(runId, token) == null) {
            return false;
        }
        String reason = McpSchemaMismatchException.USER_MESSAGE;
        if (runs.waitForSchemaReview(runId, token, reason) == 0) {
            return false;
        }
        RunApprovalEntity approval = approvals.findByRunId(runId);
        if (approval != null && "EXECUTING".equals(approval.getStatus()) && approvals.requireSchemaReview(runId) != 1) {
            throw new IllegalStateException("已批准动作未能进入工具结构核对状态");
        }
        if (steps.waitFirstStepForSchemaReview(runId, reason) != 1) {
            throw new IllegalStateException("运行步骤未能进入工具结构核对状态");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean cancelOwned(String runId, String userId) {
        if (runs.lockOwnedForCancellation(runId, userId) == null || approvals.hasProtectedExternalAction(runId)) {
            return false;
        }
        if (runs.cancelOwned(runId, userId) == 0) {
            return false;
        }
        approvals.cancelPending(runId);
        steps.cancelOpenSteps(runId);
        return true;
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

    private void audit(String runId, String approvalId, String outcome, String actorId, String evidence,
            String evidenceReference, String externalResourceId) {
        int inserted = reconciliations.insert(RunReconciliationEntity.create(runId, approvalId, outcome, actorId,
                evidence, evidenceReference, externalResourceId));
        if (inserted != 1) {
            throw new IllegalStateException("结果核对审计记录未能保存");
        }
    }
}
