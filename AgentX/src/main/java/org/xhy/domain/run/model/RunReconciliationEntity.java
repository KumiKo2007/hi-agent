package org.xhy.domain.run.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.xhy.infrastructure.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** Append-only audit record for automatic checks and owner reconciliation decisions. */
@TableName("agent_run_reconciliations")
public class RunReconciliationEntity extends BaseEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("run_id")
    private String runId;
    @TableField("approval_id")
    private String approvalId;
    @TableField("outcome")
    private String outcome;
    @TableField("actor_id")
    private String actorId;
    @TableField("evidence")
    private String evidence;
    @TableField("evidence_reference")
    private String evidenceReference;
    @TableField("external_resource_id")
    private String externalResourceId;

    public static RunReconciliationEntity create(String runId, String approvalId, String outcome, String actorId,
            String evidence, String evidenceReference, String externalResourceId) {
        RunReconciliationEntity event = new RunReconciliationEntity();
        event.id = UUID.randomUUID().toString();
        event.runId = runId;
        event.approvalId = approvalId;
        event.outcome = outcome;
        event.actorId = actorId;
        event.evidence = evidence;
        event.evidenceReference = evidenceReference;
        event.externalResourceId = externalResourceId;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = event.createdAt;
        return event;
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
    public String getApprovalId() {
        return approvalId;
    }
    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }
    public String getOutcome() {
        return outcome;
    }
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
    public String getActorId() {
        return actorId;
    }
    public void setActorId(String actorId) {
        this.actorId = actorId;
    }
    public String getEvidence() {
        return evidence;
    }
    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }
    public String getEvidenceReference() {
        return evidenceReference;
    }
    public void setEvidenceReference(String evidenceReference) {
        this.evidenceReference = evidenceReference;
    }
    public String getExternalResourceId() {
        return externalResourceId;
    }
    public void setExternalResourceId(String externalResourceId) {
        this.externalResourceId = externalResourceId;
    }
}
