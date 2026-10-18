package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunEntity;

/** 为一次简单运行生成最终回答的模型边界。 */
@FunctionalInterface
public interface RunModel {
    String execute(RunEntity run);

    /** Existing models return an answer; action-aware models can pause on a validated write proposal. */
    default RunExecution executeWithActions(RunEntity run) {
        return RunExecution.completed(execute(run));
    }

    /** Executes while optionally reporting safe, allowlisted milestones to the owning worker. */
    default RunExecution executeWithActions(RunEntity run, RunTimelineSink timeline) {
        return executeWithActions(run);
    }
}
