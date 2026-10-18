package org.xhy.application.run.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.domain.run.repository.RunTimelineRepository;
import org.xhy.domain.run.model.RunFailureCode;
import org.xhy.application.tool.service.McpSchemaMismatchException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 依据 PostgreSQL 中的状态领取工作；队列消息只表示可能有工作。 */
@Service
public class RunWorker {
    private static final Logger logger = LoggerFactory.getLogger(RunWorker.class);
    private static final int LEASE_SECONDS = 90;
    private final RunStore store;
    private final RunModel model;
    private final ApprovedRunActionExecutor approvedActions;
    private final ApprovedActionReconciler reconciler;
    private final RunTimelineRepository timeline;
    private final Map<String, String> activeClaims = new ConcurrentHashMap<>();

    public RunWorker(RunStore store, RunModel model) {
        this(store, model, (run, approval) -> {
            throw new IllegalStateException("Approved MCP action executor is not configured");
        }, (run, approval) -> ApprovedActionReconciliation
                .unknown("No external reconciliation adapter is configured; owner review is required."), null);
    }

    public RunWorker(RunStore store, RunModel model, ApprovedRunActionExecutor approvedActions) {
        this(store, model, approvedActions, (run, approval) -> ApprovedActionReconciliation
                .unknown("No external reconciliation adapter is configured; owner review is required."), null);
    }

    public RunWorker(RunStore store, RunModel model, ApprovedRunActionExecutor approvedActions,
            ApprovedActionReconciler reconciler) {
        this(store, model, approvedActions, reconciler, null);
    }

    @Autowired
    public RunWorker(RunStore store, RunModel model, ApprovedRunActionExecutor approvedActions,
            ApprovedActionReconciler reconciler, RunTimelineRepository timeline) {
        this.store = store;
        this.model = model;
        this.approvedActions = approvedActions;
        this.reconciler = reconciler;
        this.timeline = timeline;
    }

    /** 重复消息会在原子领取失败后退出，不执行模型。 */
    public void process(String runId) {
        String token = UUID.randomUUID().toString();
        if (!store.claim(runId, token, LEASE_SECONDS)) {
            return;
        }
        activeClaims.put(runId, token);
        try {
            RunEntity run = store.findForWorker(runId);
            if (run == null) {
                logger.warn("已领取运行但找不到记录：{}", runId);
                return;
            }
            String stepId = firstStepId(runId);
            append(RunTimelineEvents.attemptStarted(runId, stepId));
            RunApprovalEntity approval = store.findApprovalForRun(runId);
            String answer;
            boolean approvedActionStarted = false;
            try {
                if (approval != null && "APPROVED".equals(approval.getStatus())) {
                    if (!store.beginApprovedAction(runId, token, approval.getId())) {
                        logger.info("已批准动作未能取得一次性执行权：{}", runId);
                        return;
                    }
                    approvedActionStarted = true;
                    append(RunTimelineEvents.toolAction(runId, stepId, approval.getServiceId(),
                            approval.getActionName(), approval.getTargetRepository(), "EXECUTING", null, null));
                    answer = approvedActions.execute(run, approval);
                    if (answer == null || answer.isBlank()) {
                        store.waitForReconciliation(runId, token);
                        append(RunTimelineEvents.toolAction(runId, stepId, approval.getServiceId(),
                                approval.getActionName(), approval.getTargetRepository(), "RECONCILIATION_REQUIRED",
                                null, null));
                        return;
                    }
                    if (!store.recordApprovedActionResult(runId, token, approval.getId(), answer)) {
                        logger.info("已批准动作结果未能持久化，等待结果核对：{}", runId);
                        store.waitForReconciliation(runId, token);
                        append(RunTimelineEvents.toolAction(runId, stepId, approval.getServiceId(),
                                approval.getActionName(), approval.getTargetRepository(), "RECONCILIATION_REQUIRED",
                                null, null));
                        return;
                    }
                    append(RunTimelineEvents.toolAction(runId, stepId, approval.getServiceId(),
                            approval.getActionName(), approval.getTargetRepository(), "COMPLETED", answer, null));
                } else if (approval != null && "EXECUTED".equals(approval.getStatus())) {
                    if (approval.getExternalResourceId() == null || approval.getExternalResourceId().isBlank()) {
                        store.waitForReconciliation(runId, token);
                        return;
                    }
                    answer = approval.getExternalResourceId();
                } else if (approval != null && ("EXECUTING".equals(approval.getStatus())
                        || "RECONCILIATION_REQUIRED".equals(approval.getStatus()))) {
                    ApprovedActionReconciliation result = reconciler.reconcile(run, approval);
                    if (result == null) {
                        result = ApprovedActionReconciliation.unknown("外部结果核对未返回结论");
                    }
                    if (result.outcome() == ApprovedActionReconciliation.Outcome.VERIFIED_CREATED) {
                        if (result.externalResourceId() == null || result.externalResourceId().isBlank()
                                || !store.recordReconciledCreated(runId, token, approval.getId(),
                                        result.externalResourceId(),
                                        RunTimelineSanitizer.redactText(result.evidence()))) {
                            store.markReconciliationUnknown(runId, token, approval.getId(), "找到外部标记，但未能安全保存资源结果");
                            return;
                        }
                        append(RunTimelineEvents.reconciliation(runId, stepId, approval, "VERIFIED_CREATED", null));
                        answer = result.externalResourceId();
                    } else if (result.outcome() == ApprovedActionReconciliation.Outcome.VERIFIED_NOT_CREATED) {
                        if (!store.retryAfterVerifiedNotCreated(runId, token, approval.getId(),
                                RunTimelineSanitizer.redactText(result.evidence()))) {
                            store.markReconciliationUnknown(runId, token, approval.getId(), "未能保存未创建证据，保持等待以避免重复写入");
                        }
                        append(RunTimelineEvents.reconciliation(runId, stepId, approval, "VERIFIED_NOT_CREATED", null));
                        return;
                    } else {
                        String reason = result.evidence() == null || result.evidence().isBlank()
                                ? "外部结果无法核实；不会重发写入。"
                                : RunTimelineSanitizer.redactText(result.evidence());
                        if (!store.markReconciliationUnknown(runId, token, approval.getId(), reason)) {
                            logger.info("运行已由其他工作者接管，未能记录结果核对：{}", runId);
                        }
                        append(RunTimelineEvents.reconciliation(runId, stepId, approval, "UNKNOWN", null));
                        return;
                    }
                } else if (approval == null) {
                    RunExecution execution = model.executeWithActions(run, event -> append(event.attachStep(stepId)));
                    if (execution.approvalRequest() != null) {
                        if (execution.preauthorized()) {
                            RunApprovalEntity request = RunApprovalEntity.createPreauthorized(runId,
                                    execution.approvalRequest(), run.getUserId());
                            if (!store.requestPreauthorizedAction(runId, token, request)) {
                                logger.info("运行已由其他工作者接管，忽略过期创建者预授权：{}", runId);
                                return;
                            }
                            append(RunTimelineEvents.approvalDecision(runId, stepId, request));
                            if (!store.beginApprovedAction(runId, token, request.getId())) {
                                logger.info("创建者预授权动作未能取得一次性执行权：{}", runId);
                                return;
                            }
                            approvedActionStarted = true;
                            append(RunTimelineEvents.toolAction(runId, stepId, request.getServiceId(),
                                    request.getActionName(), request.getTargetRepository(), "EXECUTING", null, null));
                            answer = approvedActions.execute(run, request);
                            if (answer == null || answer.isBlank()) {
                                store.waitForReconciliation(runId, token);
                                append(RunTimelineEvents.toolAction(runId, stepId, request.getServiceId(),
                                        request.getActionName(), request.getTargetRepository(),
                                        "RECONCILIATION_REQUIRED", null, null));
                                return;
                            }
                            if (!store.recordApprovedActionResult(runId, token, request.getId(), answer)) {
                                logger.info("创建者预授权动作结果未能持久化，等待结果核对：{}", runId);
                                store.waitForReconciliation(runId, token);
                                append(RunTimelineEvents.toolAction(runId, stepId, request.getServiceId(),
                                        request.getActionName(), request.getTargetRepository(),
                                        "RECONCILIATION_REQUIRED", null, null));
                                return;
                            }
                            append(RunTimelineEvents.toolAction(runId, stepId, request.getServiceId(),
                                    request.getActionName(), request.getTargetRepository(), "COMPLETED", answer, null));
                        } else {
                            RunApprovalEntity request = RunApprovalEntity.create(runId, execution.approvalRequest());
                            if (!store.requestApproval(runId, token, request)) {
                                logger.info("运行已由其他工作者接管，忽略过期审批请求：{}", runId);
                            } else {
                                append(RunTimelineEvents.approvalRequested(runId, stepId, request));
                            }
                            return;
                        }
                    } else {
                        answer = execution.answer();
                    }
                } else {
                    if (store.fail(runId, token, RunFailureCode.EXECUTION_FAILED.safeMessage())) {
                        append(RunTimelineEvents.runFailed(runId, stepId, RunFailureCode.EXECUTION_FAILED));
                    }
                    return;
                }
            } catch (McpSchemaMismatchException exception) {
                if (!store.waitForSchemaReview(runId, token)) {
                    logger.info("MCP 工具结构变化后运行已失去当前租约，未能记录暂停状态：{}", runId);
                } else {
                    append(RunTimelineEvents.schemaReview(runId, stepId));
                }
                return;
            } catch (Exception exception) {
                RunFailureCode failureCode = RunFailureCode.classify(exception);
                boolean transitioned;
                if (approvedActionStarted) {
                    transitioned = store.waitForReconciliation(runId, token);
                    if (transitioned) {
                        RunApprovalEntity currentApproval = store.findApprovalForRun(runId);
                        append(RunTimelineEvents.toolAction(runId, stepId,
                                currentApproval == null ? null : currentApproval.getServiceId(),
                                currentApproval == null ? null : currentApproval.getActionName(),
                                currentApproval == null ? null : currentApproval.getTargetRepository(),
                                "RECONCILIATION_REQUIRED", null, null));
                    }
                } else {
                    transitioned = store.fail(runId, token, failureCode.safeMessage());
                    if (transitioned) {
                        append(RunTimelineEvents.runFailed(runId, stepId, failureCode));
                    }
                }
                if (!transitioned) {
                    logger.info("运行失败结果已过期，等待当前工作者处理：{}", runId);
                }
                return;
            }
            if (answer == null || answer.isBlank()) {
                if (approvedActionStarted) {
                    store.waitForReconciliation(runId, token);
                } else {
                    if (store.fail(runId, token, RunFailureCode.EXECUTION_FAILED.safeMessage())) {
                        append(RunTimelineEvents.runFailed(runId, stepId, RunFailureCode.EXECUTION_FAILED));
                    }
                }
                return;
            }
            String safeAnswer = RunTimelineSanitizer.redactText(answer);
            if (!store.complete(runId, token, safeAnswer)) {
                logger.info("运行已由其他工作者接管，忽略过期结果：{}", runId);
            } else {
                append(RunTimelineEvents.runCompleted(runId, stepId));
            }
        } finally {
            activeClaims.remove(runId, token);
        }
    }

    private String firstStepId(String runId) {
        var steps = store.stepsForRun(runId);
        return steps == null || steps.isEmpty() ? null : steps.get(0).getId();
    }

    private void append(org.xhy.domain.run.model.RunTimelineEventEntity event) {
        if (timeline == null || event == null) {
            return;
        }
        try {
            timeline.append(event);
        } catch (RuntimeException exception) {
            logger.warn("运行时间线事件暂不可用：{}", event.getRunId());
        }
    }

    /** 模型调用可较慢，独立于消费者线程续租。 */
    @Scheduled(fixedDelayString = "${agent.run.lease-renew-ms:30000}")
    public void renewActiveClaims() {
        activeClaims.forEach((runId, token) -> {
            try {
                if (!store.renewLease(runId, token, LEASE_SECONDS)) {
                    activeClaims.remove(runId, token);
                }
            } catch (Exception exception) {
                logger.warn("运行续租暂时失败：{}", runId);
            }
        });
    }
}
