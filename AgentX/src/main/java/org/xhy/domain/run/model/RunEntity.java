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
    /** The ready knowledge snapshot selected when this run was created. */
    @TableField("knowledge_version_id")
    private String knowledgeVersionId;
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

    public void start() {
        status = "RUNNING";
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

    public String getKnowledgeVersionId() {
        return knowledgeVersionId;
    }

    public void setKnowledgeVersionId(String knowledgeVersionId) {
        this.knowledgeVersionId = knowledgeVersionId;
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
