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
              AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP))
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

    @Select("""
            SELECT id FROM agent_runs WHERE deleted_at IS NULL
              AND (status = 'PENDING' OR (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP))
            ORDER BY created_at ASC LIMIT #{limit}
            """)
    List<String> recoverableRunIds(@Param("limit") int limit);
}
