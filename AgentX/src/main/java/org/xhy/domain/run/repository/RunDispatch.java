package org.xhy.domain.run.repository;

/** 派发一次运行；运行状态仍以 PostgreSQL 为准。 */
@FunctionalInterface
public interface RunDispatch {
    void dispatch(String runId);
}
