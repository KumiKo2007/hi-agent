package org.xhy.domain.run.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.xhy.infrastructure.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** 运行中的可识别动作。 */
@TableName("agent_run_steps")
public class RunStepEntity extends BaseEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("run_id")
    private String runId;
    @TableField("sequence_no")
    private Integer sequenceNo;
    @TableField("kind")
    private String kind;
    @TableField("status")
    private String status;
    @TableField("result")
    private String result;
    @TableField("error_message")
    private String errorMessage;

    public static RunStepEntity modelCall(String runId) {
        RunStepEntity step = new RunStepEntity();
        step.id = UUID.randomUUID().toString();
        step.runId = runId;
        step.sequenceNo = 1;
        step.kind = "MODEL_CALL";
        step.status = "PENDING";
        step.createdAt = LocalDateTime.now();
        step.updatedAt = step.createdAt;
        return step;
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

    /** 取消尚未完成的动作，避免刷新后仍显示为可继续执行。 */
    public void cancel(String reason) {
        status = "CANCELLED";
        result = null;
        errorMessage = reason;
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
    public Integer getSequenceNo() {
        return sequenceNo;
    }
    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }
    public String getKind() {
        return kind;
    }
    public void setKind(String kind) {
        this.kind = kind;
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
