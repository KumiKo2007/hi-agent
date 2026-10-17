package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.xhy.domain.run.model.RunApprovalEntity;

@Mapper
public interface RunApprovalMapper extends BaseMapper<RunApprovalEntity> {
    @Select("SELECT * FROM agent_run_approvals WHERE run_id = #{runId} AND deleted_at IS NULL")
    RunApprovalEntity findByRunId(@Param("runId") String runId);

    @Update("""
            UPDATE agent_run_approvals SET status = #{status}, approval_source = 'USER', decided_by = #{userId},
                decided_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status = 'PENDING' AND deleted_at IS NULL
            """)
    int decidePending(@Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("userId") String userId, @Param("status") String status);

    @Update("""
            UPDATE agent_run_approvals SET status = 'EXECUTING', reconciliation_state = 'NONE', updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status = 'APPROVED' AND deleted_at IS NULL
              AND EXISTS (
                  SELECT 1 FROM agent_runs run WHERE run.id = #{runId} AND run.status = 'RUNNING'
                    AND run.lease_token = #{token} AND run.lease_until > CURRENT_TIMESTAMP AND run.deleted_at IS NULL
              )
            """)
    int beginExecution(@Param("runId") String runId, @Param("token") String token,
            @Param("approvalId") String approvalId);

    @Update("""
            UPDATE agent_run_approvals SET status = 'RECONCILIATION_REQUIRED', reconciliation_state = 'NEEDS_CHECK',
                updated_at = CURRENT_TIMESTAMP
            WHERE run_id = #{runId} AND status = 'EXECUTING' AND deleted_at IS NULL
            """)
    int requireReconciliation(@Param("runId") String runId);

    @Update("""
            UPDATE agent_run_approvals SET status = 'RECONCILIATION_REQUIRED',
                reconciliation_state = 'UNKNOWN', reconciliation_outcome = 'UNKNOWN',
                reconciliation_reason = #{reason}, reconciliation_evidence = #{reason},
                reconciliation_evidence_reference = NULL, reconciliation_actor_id = NULL,
                reconciliation_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status IN ('EXECUTING', 'RECONCILIATION_REQUIRED')
              AND deleted_at IS NULL
            """)
    int markReconciliationUnknown(@Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("reason") String reason);

    @Update("""
            UPDATE agent_run_approvals SET status = 'SCHEMA_REVIEW_REQUIRED', updated_at = CURRENT_TIMESTAMP
            WHERE run_id = #{runId} AND status = 'EXECUTING' AND deleted_at IS NULL
            """)
    int requireSchemaReview(@Param("runId") String runId);

    @Select("SELECT EXISTS (SELECT 1 FROM agent_run_approvals WHERE run_id = #{runId} "
            + "AND status IN ('EXECUTING', 'EXECUTED') AND deleted_at IS NULL)")
    boolean hasProtectedExternalAction(@Param("runId") String runId);

    @Update("""
            UPDATE agent_run_approvals SET status = 'EXECUTED', external_resource_id = #{externalResourceId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status IN ('EXECUTING', 'RECONCILIATION_REQUIRED')
              AND deleted_at IS NULL
              AND EXISTS (
                  SELECT 1 FROM agent_runs run WHERE run.id = #{runId} AND run.status = 'RUNNING'
                    AND run.lease_token = #{token} AND run.lease_until > CURRENT_TIMESTAMP AND run.deleted_at IS NULL
              )
            """)
    int recordExternalResource(@Param("runId") String runId, @Param("token") String token,
            @Param("approvalId") String approvalId, @Param("externalResourceId") String externalResourceId);

    @Update("""
            UPDATE agent_run_approvals SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
            WHERE run_id = #{runId} AND status IN ('PENDING', 'APPROVED') AND deleted_at IS NULL
            """)
    int cancelPending(@Param("runId") String runId);

    @Update("""
            UPDATE agent_run_approvals SET status = 'EXECUTED', external_resource_id = #{externalResourceId},
                reconciliation_state = 'RESOLVED', reconciliation_outcome = 'VERIFIED_CREATED',
                reconciliation_reason = NULL, reconciliation_evidence = #{evidence},
                reconciliation_evidence_reference = NULL, reconciliation_actor_id = NULL,
                reconciliation_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status IN ('EXECUTING', 'RECONCILIATION_REQUIRED')
              AND deleted_at IS NULL
            """)
    int recordReconciledCreated(@Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("externalResourceId") String externalResourceId, @Param("evidence") String evidence);

    @Update("""
            UPDATE agent_run_approvals SET status = 'EXECUTED', external_resource_id = #{externalResourceId},
                reconciliation_state = 'RESOLVED', reconciliation_outcome = 'RECORD_CREATED',
                reconciliation_reason = NULL, reconciliation_evidence = #{evidence},
                reconciliation_evidence_reference = #{evidenceReference},
                reconciliation_actor_id = #{userId}, reconciliation_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status IN ('EXECUTING', 'RECONCILIATION_REQUIRED')
              AND deleted_at IS NULL
            """)
    int resolveCreated(@Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("userId") String userId, @Param("externalResourceId") String externalResourceId,
            @Param("evidence") String evidence, @Param("evidenceReference") String evidenceReference);

    @Update("""
            UPDATE agent_run_approvals SET status = 'APPROVED', reconciliation_state = 'VERIFIED_NOT_CREATED',
                reconciliation_outcome = 'CONFIRM_NOT_CREATED', reconciliation_reason = NULL,
                reconciliation_evidence = #{evidence},
                reconciliation_evidence_reference = #{evidenceReference}, reconciliation_actor_id = #{userId},
                reconciliation_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status = 'RECONCILIATION_REQUIRED'
              AND deleted_at IS NULL
            """)
    int resolveNotCreated(@Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("userId") String userId, @Param("evidence") String evidence,
            @Param("evidenceReference") String evidenceReference);

    @Update("""
            UPDATE agent_run_approvals SET status = 'RECONCILIATION_STOPPED', reconciliation_state = 'RESOLVED',
                reconciliation_outcome = 'STOP', reconciliation_reason = #{evidence},
                reconciliation_evidence = #{evidence},
                reconciliation_evidence_reference = #{evidenceReference}, reconciliation_actor_id = #{userId},
                reconciliation_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status = 'RECONCILIATION_REQUIRED'
              AND deleted_at IS NULL
            """)
    int resolveStop(@Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("userId") String userId, @Param("evidence") String evidence,
            @Param("evidenceReference") String evidenceReference);

    @Update("""
            UPDATE agent_run_approvals SET status = 'APPROVED', reconciliation_state = 'VERIFIED_NOT_CREATED',
                reconciliation_outcome = 'VERIFIED_NOT_CREATED', reconciliation_reason = NULL,
                reconciliation_evidence = #{evidence}, reconciliation_evidence_reference = NULL,
                reconciliation_actor_id = NULL,
                reconciliation_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{approvalId} AND run_id = #{runId} AND status = 'RECONCILIATION_REQUIRED'
              AND deleted_at IS NULL
            """)
    int recordVerifiedNotCreated(@Param("runId") String runId, @Param("approvalId") String approvalId,
            @Param("evidence") String evidence);
}
