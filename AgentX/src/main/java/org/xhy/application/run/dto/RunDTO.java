package org.xhy.application.run.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 使用者可见的运行结果。 */
public class RunDTO {
    private String id;
    private String sessionId;
    private String agentId;
    private String agentVersionId;
    private String agentVersionNumber;
    private String modelConnectionType;
    private String approvalPolicy;
    /** Ready knowledge version pinned at run creation time, if the agent uses knowledge. */
    private String knowledgeVersionId;
    private String targetRepository;
    private String goal;
    private String status;
    private String result;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RunStepDTO> steps;
    private RunApprovalDTO approval;
    private List<RunTimelineEventDTO> timeline;
    private RunModelUsageDTO modelUsage;
    private String nextAction;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getSessionId() {
        return sessionId;
    }
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    public String getAgentId() {
        return agentId;
    }
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    public String getAgentVersionId() {
        return agentVersionId;
    }
    public void setAgentVersionId(String agentVersionId) {
        this.agentVersionId = agentVersionId;
    }
    public String getAgentVersionNumber() {
        return agentVersionNumber;
    }
    public void setAgentVersionNumber(String agentVersionNumber) {
        this.agentVersionNumber = agentVersionNumber;
    }
    public String getModelConnectionType() {
        return modelConnectionType;
    }
    public void setModelConnectionType(String modelConnectionType) {
        this.modelConnectionType = modelConnectionType;
    }

    public String getApprovalPolicy() {
        return approvalPolicy;
    }

    public void setApprovalPolicy(String approvalPolicy) {
        this.approvalPolicy = approvalPolicy;
    }

    public String getKnowledgeVersionId() {
        return knowledgeVersionId;
    }

    public void setKnowledgeVersionId(String knowledgeVersionId) {
        this.knowledgeVersionId = knowledgeVersionId;
    }
    public String getTargetRepository() {
        return targetRepository;
    }
    public void setTargetRepository(String targetRepository) {
        this.targetRepository = targetRepository;
    }
    public String getGoal() {
        return goal;
    }
    public void setGoal(String goal) {
        this.goal = goal;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public List<RunStepDTO> getSteps() {
        return steps;
    }
    public void setSteps(List<RunStepDTO> steps) {
        this.steps = steps;
    }
    public RunApprovalDTO getApproval() {
        return approval;
    }
    public void setApproval(RunApprovalDTO approval) {
        this.approval = approval;
    }
    public List<RunTimelineEventDTO> getTimeline() {
        return timeline;
    }
    public void setTimeline(List<RunTimelineEventDTO> timeline) {
        this.timeline = timeline;
    }
    public RunModelUsageDTO getModelUsage() {
        return modelUsage;
    }
    public void setModelUsage(RunModelUsageDTO modelUsage) {
        this.modelUsage = modelUsage;
    }
    public String getNextAction() {
        return nextAction;
    }
    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }
}
