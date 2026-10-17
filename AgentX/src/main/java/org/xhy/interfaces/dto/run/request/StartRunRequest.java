package org.xhy.interfaces.dto.run.request;

import jakarta.validation.constraints.NotBlank;

/** 提交给 Agent 的一个目标。 */
public class StartRunRequest {
    @NotBlank(message = "目标不可为空")
    private String goal;
    private String approvalPolicy;
    private String knowledgeBaseId;
    private String targetRepository;

    public String getGoal() {
        return goal;
    }
    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getApprovalPolicy() {
        return approvalPolicy;
    }

    public void setApprovalPolicy(String approvalPolicy) {
        this.approvalPolicy = approvalPolicy;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getTargetRepository() {
        return targetRepository;
    }

    public void setTargetRepository(String targetRepository) {
        this.targetRepository = targetRepository;
    }
}
