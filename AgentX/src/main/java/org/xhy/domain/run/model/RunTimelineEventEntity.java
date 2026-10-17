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

/** Append-only, safe owner-visible event. Free-form prompts, tool arguments, and outputs have no field here. */
@TableName("agent_run_timeline_events")
public class RunTimelineEventEntity extends BaseEntity {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("run_id")
    private String runId;
    @TableField("step_id")
    private String stepId;
    @TableField("event_type")
    private String eventType;
    @TableField("event_status")
    private String status;
    @TableField("safe_summary")
    private String summary;
    @TableField("details_json")
    private String detailsJson;

    public RunTimelineEventEntity() {
    }

    public static RunTimelineEventEntity create(String runId, String stepId, RunTimelineEventType eventType,
            String status, RunTimelineEventData data) {
        if (runId == null || runId.isBlank() || eventType == null || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Timeline event requires a run, type, and status");
        }
        RunTimelineEventEntity event = new RunTimelineEventEntity();
        event.id = UUID.randomUUID().toString();
        event.runId = runId;
        event.stepId = stepId;
        event.eventType = eventType.name();
        event.status = status;
        event.summary = summaryFor(eventType, status);
        try {
            event.detailsJson = JSON.writeValueAsString(data == null ? RunTimelineEventData.empty() : data);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Safe timeline details could not be serialized", exception);
        }
        event.createdAt = LocalDateTime.now();
        event.updatedAt = event.createdAt;
        return event;
    }

    /** Worker context adds the persisted model-step ID without changing the event's safe payload. */
    public RunTimelineEventEntity attachStep(String stepId) {
        if (this.stepId == null || this.stepId.isBlank()) {
            this.stepId = stepId;
        }
        return this;
    }

    private static String summaryFor(RunTimelineEventType type, String status) {
        return switch (type) {
            case RUN_CREATED -> "目标运行已创建";
            case RUN_ATTEMPT_STARTED -> "运行工作者开始处理";
            case KNOWLEDGE_RETRIEVAL -> switch (status) {
                case "COMPLETED" -> "已检索固定资料";
                case "FAILED" -> "固定资料检索失败";
                default -> "固定资料依据不足";
            };
            case KNOWLEDGE_SOURCE -> "使用了固定资料来源";
            case MODEL_CALL -> "FAILED".equals(status) ? "模型调用失败" : "模型调用完成";
            case TOOL_ACTION -> toolSummary(status);
            case APPROVAL_REQUESTED -> "外部写入等待所有者审批";
            case APPROVAL_DECISION ->
                "APPROVED".equals(status) || "PREAUTHORIZED".equals(status) ? "所有者已批准外部写入" : "所有者已拒绝外部写入";
            case RECONCILIATION -> reconciliationSummary(status);
            case TOOL_SCHEMA_REVIEW -> "工具结构发生变化，运行已暂停";
            case RUN_COMPLETED -> "运行已完成";
            case RUN_FAILED -> "运行失败";
            case RUN_CANCELLED -> "运行已取消";
        };
    }

    private static String toolSummary(String status) {
        return switch (status) {
            case "PROPOSED" -> "模型提出了外部动作";
            case "EXECUTING" -> "已批准的外部动作正在执行";
            case "COMPLETED", "READ_COMPLETED" -> "工具动作已返回结果";
            case "RECONCILIATION_REQUIRED" -> "外部动作结果不确定，等待核对";
            default -> "工具动作失败";
        };
    }

    private static String reconciliationSummary(String status) {
        return switch (status) {
            case "VERIFIED_CREATED" -> "已核实外部资源已创建";
            case "VERIFIED_NOT_CREATED" -> "已核实外部资源未创建";
            case "STOPPED" -> "所有者停止了外部动作恢复";
            case "NEEDS_CHECK" -> "等待核对外部动作结果";
            default -> "外部动作结果仍不确定";
        };
    }

    public String getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public String getStepId() {
        return stepId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }
}
