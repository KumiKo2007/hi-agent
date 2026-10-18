package org.xhy.application.run.dto;

import java.time.LocalDateTime;

/** User-visible fixed MCP write proposal and its decision. */
public class RunApprovalDTO {
    private String id;
    private String serviceId;
    private String actionName;
    private String actionDescription;
    private String targetRepository;
    private String arguments;
    private String status;
    private String approvalSource;
    private String decidedBy;
    private LocalDateTime decidedAt;
    private String externalResourceId;
    private String reconciliationState;
    private String reconciliationOutcome;
    private String reconciliationReason;
    private String reconciliationEvidence;
    private String reconciliationEvidenceReference;
    private String reconciliationActorId;
    private LocalDateTime reconciliationAt;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
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
