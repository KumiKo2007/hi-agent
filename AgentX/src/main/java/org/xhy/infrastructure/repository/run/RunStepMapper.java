package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.xhy.domain.run.model.RunStepEntity;

@Mapper
public interface RunStepMapper extends BaseMapper<RunStepEntity> {
    @Update("""
            UPDATE agent_run_steps SET status = 'RUNNING', result = NULL, error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE run_id = #{runId} AND sequence_no = 1 AND deleted_at IS NULL
            """)
    int startFirstStep(@Param("runId") String runId);

    @Update("""
            UPDATE agent_run_steps SET status = 'COMPLETED', result = #{answer}, error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE run_id = #{runId} AND sequence_no = 1 AND deleted_at IS NULL
            """)
    int completeFirstStep(@Param("runId") String runId, @Param("answer") String answer);

    @Update("""
            UPDATE agent_run_steps SET status = 'FAILED', error_message = #{reason}, result = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE run_id = #{runId} AND sequence_no = 1 AND deleted_at IS NULL
            """)
    int failFirstStep(@Param("runId") String runId, @Param("reason") String reason);
}
