package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.xhy.domain.run.model.RunEntity;

import java.util.List;

@Mapper
public interface RunMapper extends BaseMapper<RunEntity> {
    @Update("""
            UPDATE agent_runs SET status = 'RUNNING', lease_token = #{token},
                lease_until = CURRENT_TIMESTAMP + (#{leaseSeconds} * INTERVAL '1 second'),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND deleted_at IS NULL
              AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP)
                OR (status = 'WAITING_RECONCILIATION' AND EXISTS (
                    SELECT 1 FROM agent_run_approvals approval WHERE approval.run_id = agent_runs.id
                      AND approval.status = 'RECONCILIATION_REQUIRED' AND approval.reconciliation_state = 'NEEDS_CHECK'
                      AND approval.deleted_at IS NULL
                )))
            """)
    int claim(@Param("runId") String runId, @Param("token") String token, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE agent_runs SET lease_until = CURRENT_TIMESTAMP + (#{leaseSeconds} * INTERVAL '1 second'),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND status = 'RUNNING' AND lease_token = #{token}
              AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL
            """)
    int renewLease(@Param("runId") String runId, @Param("token") String token, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE agent_runs SET status = 'COMPLETED', result = #{answer}, error_message = NULL,
                lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND status = 'RUNNING' AND lease_token = #{token}
              AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL
            """)
    int complete(@Param("runId") String runId, @Param("token") String token, @Param("answer") String answer);

    @Update("""
            UPDATE agent_runs SET status = 'FAILED', error_message = #{reason}, result = NULL,
                lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND status = 'RUNNING' AND lease_token = #{token}
              AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL
            """)
    int fail(@Param("runId") String runId, @Param("token") String token, @Param("reason") String reason);

    @Update("""
            UPDATE agent_runs SET status = 'WAITING_APPROVAL', lease_token = NULL, lease_until = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND status = 'RUNNING' AND lease_token = #{token}
              AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL
            """)
    int waitForApproval(@Param("runId") String runId, @Param("token") String token);

    @Select("SELECT id FROM agent_runs WHERE id = #{runId} AND status = 'RUNNING' "
            + "AND lease_token = #{token} AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL FOR UPDATE")
    String lockActiveLease(@Param("runId") String runId, @Param("token") String token);

    @Select("SELECT id FROM agent_runs WHERE id = #{runId} AND user_id = #{userId} "
            + "AND status = 'WAITING_RECONCILIATION' AND deleted_at IS NULL FOR UPDATE")
    String lockOwnedWaitingReconciliation(@Param("runId") String runId, @Param("userId") String userId);

    @Select("SELECT id FROM agent_runs WHERE id = #{runId} AND user_id = #{userId} AND deleted_at IS NULL "
            + "AND status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'WAITING_RECONCILIATION', "
            + "'WAITING_SCHEMA_REVIEW') FOR UPDATE")
    String lockOwnedForCancellation(@Param("runId") String runId, @Param("userId") String userId);

    @Update("""
            UPDATE agent_runs SET status = 'WAITING_RECONCILIATION', lease_token = NULL, lease_until = NULL,
                error_message = #{reason}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND status = 'RUNNING' AND lease_token = #{token}
              AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL
            """)
    int waitForReconciliation(@Param("runId") String runId, @Param("token") String token,
            @Param("reason") String reason);

    @Update("""
            UPDATE agent_runs SET status = 'WAITING_SCHEMA_REVIEW', lease_token = NULL, lease_until = NULL,
                error_message = #{reason}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND status = 'RUNNING' AND lease_token = #{token}
              AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL
            """)
    int waitForSchemaReview(@Param("runId") String runId, @Param("token") String token, @Param("reason") String reason);

    @Update("""
            UPDATE agent_runs SET status = 'PENDING', lease_token = NULL, lease_until = NULL,
                error_message = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND user_id = #{userId} AND status = 'WAITING_APPROVAL' AND deleted_at IS NULL
            """)
    int approveOwned(@Param("runId") String runId, @Param("userId") String userId);

    @Update("""
            UPDATE agent_runs SET status = 'FAILED', error_message = '外部写入审批被拒绝', result = NULL,
                lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND user_id = #{userId} AND status = 'WAITING_APPROVAL' AND deleted_at IS NULL
            """)
    int denyOwned(@Param("runId") String runId, @Param("userId") String userId);

    @Update("""
            UPDATE agent_runs SET status = 'CANCELLED', result = NULL, error_message = '运行已取消',
                lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND user_id = #{userId} AND deleted_at IS NULL
              AND status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL',
                  'WAITING_SCHEMA_REVIEW')
              AND NOT EXISTS (
                  SELECT 1 FROM agent_run_approvals approval
                  WHERE approval.run_id = agent_runs.id
                    AND approval.status IN ('EXECUTING', 'EXECUTED')
                    AND approval.deleted_at IS NULL
              )
            """)
    int cancelOwned(@Param("runId") String runId, @Param("userId") String userId);

    @Update("""
            UPDATE agent_runs SET status = 'PENDING', error_message = NULL, lease_token = NULL, lease_until = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND user_id = #{userId} AND status = 'WAITING_RECONCILIATION'
              AND deleted_at IS NULL
            """)
    int resumeReconciliation(@Param("runId") String runId, @Param("userId") String userId);

    @Update("""
            UPDATE agent_runs SET status = 'CANCELLED', result = NULL, error_message = #{reason},
                lease_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND user_id = #{userId} AND status = 'WAITING_RECONCILIATION'
              AND deleted_at IS NULL
            """)
    int stopReconciliation(@Param("runId") String runId, @Param("userId") String userId,
            @Param("reason") String reason);

    @Update("""
            UPDATE agent_runs SET status = 'PENDING', error_message = NULL, lease_token = NULL, lease_until = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{runId} AND status = 'RUNNING' AND lease_token = #{token}
              AND lease_until > CURRENT_TIMESTAMP AND deleted_at IS NULL
            """)
    int retryAfterVerifiedNotCreated(@Param("runId") String runId, @Param("token") String token);

    @Select("""
            SELECT id FROM agent_runs WHERE deleted_at IS NULL
              AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP)
                OR (status = 'WAITING_RECONCILIATION' AND EXISTS (
                    SELECT 1 FROM agent_run_approvals approval WHERE approval.run_id = agent_runs.id
                      AND approval.status = 'RECONCILIATION_REQUIRED' AND approval.reconciliation_state = 'NEEDS_CHECK'
                      AND approval.deleted_at IS NULL
                )))
            ORDER BY created_at ASC LIMIT #{limit}
            """)
    List<String> recoverableRunIds(@Param("limit") int limit);
}
