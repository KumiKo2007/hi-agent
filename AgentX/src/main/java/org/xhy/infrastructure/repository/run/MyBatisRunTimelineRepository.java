package org.xhy.infrastructure.repository.run;

import org.springframework.stereotype.Repository;
import org.xhy.domain.run.model.RunTimelineEventEntity;
import org.xhy.domain.run.repository.RunTimelineRepository;

import java.util.List;

/** PostgreSQL append-only run event store. */
@Repository
public class MyBatisRunTimelineRepository implements RunTimelineRepository {
    private final RunTimelineMapper mapper;

    public MyBatisRunTimelineRepository(RunTimelineMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(RunTimelineEventEntity event) {
        if (mapper.insert(event) != 1) {
            throw new IllegalStateException("运行时间线事件未能保存");
        }
    }

    @Override
    public List<RunTimelineEventEntity> eventsForRun(String runId) {
        return mapper.selectByRunId(runId);
    }
}
