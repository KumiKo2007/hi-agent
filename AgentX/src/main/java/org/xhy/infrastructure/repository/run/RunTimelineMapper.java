package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.xhy.domain.run.model.RunTimelineEventEntity;

import java.util.List;

@Mapper
public interface RunTimelineMapper extends BaseMapper<RunTimelineEventEntity> {
    @Select("""
            SELECT id, run_id, step_id, event_type, event_status AS status, safe_summary AS summary,
                   details_json, created_at, updated_at, deleted_at
            FROM agent_run_timeline_events
            WHERE run_id = #{runId} AND deleted_at IS NULL
            ORDER BY event_order ASC
            """)
    List<RunTimelineEventEntity> selectByRunId(@Param("runId") String runId);
}
