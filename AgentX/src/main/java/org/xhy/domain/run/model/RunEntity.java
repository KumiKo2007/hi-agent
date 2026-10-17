package org.xhy.domain.run.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.xhy.infrastructure.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** 一个目标对应的一次独立运行。 */
@TableName("agent_runs")
public class RunEntity extends BaseEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("user_id")
    private String userId;
    @TableField("session_id")
    private String sessionId;
    @TableField("agent_id")
    private String agentId;
    /** 运行创建时固定的已发布 Agent 版本。 */
    @TableField("agent_version_id")
    private String agentVersionId;
    @TableField("agent_version_number")
    private String agentVersionNumber;
    /** 运行创建时固定的模型连接标识。 */
    @TableField("model_id")
    private String modelId;
    @TableField("model_connection_type")
    private String modelConnectionType;
    /** The run can tighten the creator's action preauthorizations, never loosen them. */
    @TableField("approval_policy")
    private String approvalPolicy = RunApprovalPolicy.CREATOR_DEFAULT.name();
    /** The ready knowledge snapshot selected when this run was created. */
    @TableField("knowledge_version_id")
    private String knowledgeVersionId;
    /** Target repository selected and authorized when the run was created. */
    @TableField("target_repository")
    private String targetRepository;
    @TableField("goal")
    private String goal;
    @TableField("status")
    private String status;
    @TableField("result")
    private String result;
    @TableField("error_message")
    private String errorMessage;

    public static RunEntity create(String userId, String sessionId, String agentId, String goal) {
        return create(userId, sessionId, agentId, goal, null);
    }

    /** Creates a run while pinning the knowledge snapshot used by its retrieval steps. */
    public static RunEntity create(String userId, String sessionId, String agentId, String goal,
            String knowledgeVersionId) {
        RunEntity run = new RunEntity();
        run.id = UUID.randomUUID().toString();
        run.userId = userId;
        run.sessionId = sessionId;
        run.agentId = agentId;
        run.knowledgeVersionId = knowledgeVersionId;
        run.goal = goal;
        run.status = "PENDING";
        run.createdAt = LocalDateTime.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    /** 创建已经固定 Agent 和模型连接的运行快照。 */
    public static RunEntity create(String userId, String sessionId, String agentId, String goal, String agentVersionId,
            String agentVersionNumber, String modelId, String modelConnectionType) {
        RunEntity run = create(userId, sessionId, agentId, goal);
        run.agentVersionId = agentVersionId;
        run.agentVersionNumber = agentVersionNumber;
        run.modelId = modelId;
        run.modelConnectionType = modelConnectionType;
        return run;
    }

    public void start() {
        status = "RUNNING";
        updatedAt = LocalDateTime.now();
    }

    public void waitForApproval() {
        status = "WAITING_APPROVAL";
        updatedAt = LocalDateTime.now();
    }

    public void waitForReconciliation() {
        status = "WAITING_RECONCILIATION";
        updatedAt = LocalDateTime.now();
    }

    public void complete(String answer) {
        status = "COMPLETED";
        result = answer;
        updatedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        status = "FAILED";
        errorMessage = reason;
        updatedAt = LocalDateTime.now();
    }

    /** 标记运行已取消；工作者随后不能再提交结果。 */
    public void cancel() {
        status = "CANCELLED";
        result = null;
        errorMessage = "运行已取消";
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
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
    public String getModelId() {
        return modelId;
    }
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
    public String getModelConnectionType() {
        return modelConnectionType;
    }
    public void setModelConnectionType(String modelConnectionType) {
        this.modelConnectionType = modelConnectionType;
    }

    public String getApprovalPolicy() {
        return approvalPolicy == null ? RunApprovalPolicy.CREATOR_DEFAULT.name() : approvalPolicy;
    }

    public void setApprovalPolicy(String approvalPolicy) {
        this.approvalPolicy = RunApprovalPolicy.fromValue(approvalPolicy).name();
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
}
