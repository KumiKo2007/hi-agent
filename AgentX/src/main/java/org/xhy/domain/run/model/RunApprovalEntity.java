package org.xhy.domain.run.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xhy.infrastructure.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable MCP write parameters and the owner's decision for one run. */
@TableName("agent_run_approvals")
public class RunApprovalEntity extends BaseEntity {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("run_id")
    private String runId;
    @TableField("service_id")
    private String serviceId;
    @TableField("action_name")
    private String actionName;
    @TableField("action_description")
    private String actionDescription;
    @TableField("target_repository")
    private String targetRepository;
    @TableField("arguments_json")
    private String arguments;
    @TableField("schema_json")
    private String schemaJson;
    @TableField("status")
    private String status;
    @TableField("approval_source")
    private String approvalSource;
    @TableField("decided_by")
    private String decidedBy;
    @TableField("decided_at")
    private LocalDateTime decidedAt;
    /** Opaque external resource handle returned by the MCP write action after success. */
    @TableField("external_resource_id")
    private String externalResourceId;
    @TableField("reconciliation_state")
    private String reconciliationState = "NONE";
    @TableField("reconciliation_outcome")
    private String reconciliationOutcome;
    @TableField("reconciliation_reason")
    private String reconciliationReason;
    @TableField("reconciliation_evidence")
    private String reconciliationEvidence;
    @TableField("reconciliation_evidence_reference")
    private String reconciliationEvidenceReference;
    @TableField("reconciliation_actor_id")
    private String reconciliationActorId;
    @TableField("reconciliation_at")
    private LocalDateTime reconciliationAt;

    public static RunApprovalEntity create(String runId, RunApprovalDraft proposal) {
        RunApprovalEntity approval = new RunApprovalEntity();
        approval.id = proposal.intentId();
        approval.runId = runId;
        approval.serviceId = proposal.serviceId();
        approval.actionName = proposal.actionName();
        approval.actionDescription = proposal.actionDescription();
        approval.targetRepository = proposal.targetRepository();
        approval.arguments = proposal.arguments();
        try {
            approval.schemaJson = JSON.writeValueAsString(proposal.schema());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("工具动作结构无法持久化", exception);
        }
        approval.status = "PENDING";
        approval.approvalSource = "USER";
        approval.createdAt = LocalDateTime.now();
        approval.updatedAt = approval.createdAt;
        return approval;
    }

    public static RunApprovalEntity createPreauthorized(String runId, RunApprovalDraft proposal, String creatorId) {
        RunApprovalEntity approval = create(runId, proposal);
        approval.status = "APPROVED";
        approval.approvalSource = "CREATOR_PREAUTHORIZATION";
        approval.decidedBy = creatorId;
        approval.decidedAt = LocalDateTime.now();
        approval.updatedAt = approval.decidedAt;
        return approval;
    }

    public void decide(boolean approve, String userId) {
        status = approve ? "APPROVED" : "DENIED";
        decidedBy = userId;
        decidedAt = LocalDateTime.now();
        updatedAt = decidedAt;
    }

    public void requireReconciliation() {
        status = "RECONCILIATION_REQUIRED";
        reconciliationState = "NEEDS_CHECK";
        updatedAt = LocalDateTime.now();
    }

    public void recordReconciliation(String state, String outcome, String reason, String evidence, String actorId) {
        recordReconciliation(state, outcome, reason, evidence, null, actorId);
    }

    public void recordReconciliation(String state, String outcome, String reason, String evidence,
            String evidenceReference, String actorId) {
        reconciliationState = state;
        reconciliationOutcome = outcome;
        reconciliationReason = reason;
        reconciliationEvidence = evidence;
        reconciliationEvidenceReference = evidenceReference;
        reconciliationActorId = actorId;
        reconciliationAt = LocalDateTime.now();
        updatedAt = reconciliationAt;
    }

    public void recordExternalResource(String externalResourceId) {
        if (!"EXECUTING".equals(status)) {
            throw new IllegalStateException("只有执行中的已批准动作可以记录外部资源");
        }
        if (externalResourceId == null || externalResourceId.isBlank()) {
            throw new IllegalArgumentException("外部资源标识不能为空");
        }
        this.externalResourceId = externalResourceId;
        status = "EXECUTED";
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getRunId() {
        return runId;
    }
    public void setRunId(String runId) {
        this.runId = runId;
    }
    public String getServiceId() {
        return serviceId;
    }
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
    public String getActionName() {
        return actionName;
    }
    public void setActionName(String actionName) {
        this.actionName = actionName;
    }
    public String getActionDescription() {
        return actionDescription;
    }
    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }
    public String getTargetRepository() {
        return targetRepository;
    }
    public void setTargetRepository(String targetRepository) {
        this.targetRepository = targetRepository;
    }
    public String getArguments() {
        return arguments;
    }
    public void setArguments(String arguments) {
        this.arguments = arguments;
    }
    public String getSchemaJson() {
        return schemaJson;
    }
    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getApprovalSource() {
        return approvalSource;
    }
    public void setApprovalSource(String approvalSource) {
        this.approvalSource = approvalSource;
    }
    public String getDecidedBy() {
        return decidedBy;
    }
    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }
    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }
    public String getExternalResourceId() {
        return externalResourceId;
    }
    public void setExternalResourceId(String externalResourceId) {
        this.externalResourceId = externalResourceId;
    }
    public String getReconciliationState() {
        return reconciliationState;
    }
    public void setReconciliationState(String reconciliationState) {
        this.reconciliationState = reconciliationState;
    }
    public String getReconciliationOutcome() {
        return reconciliationOutcome;
    }
    public void setReconciliationOutcome(String reconciliationOutcome) {
        this.reconciliationOutcome = reconciliationOutcome;
    }
    public String getReconciliationReason() {
        return reconciliationReason;
    }
    public void setReconciliationReason(String reconciliationReason) {
        this.reconciliationReason = reconciliationReason;
    }
    public String getReconciliationEvidence() {
        return reconciliationEvidence;
    }
    public void setReconciliationEvidence(String reconciliationEvidence) {
        this.reconciliationEvidence = reconciliationEvidence;
    }
    public String getReconciliationEvidenceReference() {
        return reconciliationEvidenceReference;
    }
    public void setReconciliationEvidenceReference(String reconciliationEvidenceReference) {
        this.reconciliationEvidenceReference = reconciliationEvidenceReference;
    }
    public String getReconciliationActorId() {
        return reconciliationActorId;
    }
    public void setReconciliationActorId(String reconciliationActorId) {
        this.reconciliationActorId = reconciliationActorId;
    }
    public LocalDateTime getReconciliationAt() {
        return reconciliationAt;
    }
    public void setReconciliationAt(LocalDateTime reconciliationAt) {
        this.reconciliationAt = reconciliationAt;
    }
}
