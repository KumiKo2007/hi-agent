package org.xhy.application.run.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.xhy.domain.run.repository.RunDispatch;
import org.xhy.domain.run.repository.RunStore;

/** 定期从 PostgreSQL 找回漏发或失去工作者的运行。 */
@Service
public class RunRecoveryService {
    private static final Logger logger = LoggerFactory.getLogger(RunRecoveryService.class);
    private final RunStore store;
    private final RunDispatch dispatch;

    public RunRecoveryService(RunStore store, RunDispatch dispatch) {
        this.store = store;
        this.dispatch = dispatch;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${agent.run.recovery-ms:10000}")
    public void recover() {
        try {
            for (String runId : store.recoverableRunIds(100)) {
                dispatch.dispatch(runId);
            }
        } catch (Exception exception) {
            logger.warn("运行恢复扫描暂时失败，将在下一周期重试");
        }
    }
}
