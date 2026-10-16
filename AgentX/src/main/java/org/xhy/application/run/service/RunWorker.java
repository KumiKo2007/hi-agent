package org.xhy.application.run.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.repository.RunStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 依据 PostgreSQL 中的状态领取工作；队列消息只表示可能有工作。 */
@Service
public class RunWorker {
    private static final Logger logger = LoggerFactory.getLogger(RunWorker.class);
    private static final int LEASE_SECONDS = 90;
    private final RunStore store;
    private final RunModel model;
    private final Map<String, String> activeClaims = new ConcurrentHashMap<>();

    public RunWorker(RunStore store, RunModel model) {
        this.store = store;
        this.model = model;
    }

    /** 重复消息会在原子领取失败后退出，不执行模型。 */
    public void process(String runId) {
        String token = UUID.randomUUID().toString();
        if (!store.claim(runId, token, LEASE_SECONDS)) {
            return;
        }
        activeClaims.put(runId, token);
        try {
            RunEntity run = store.findForWorker(runId);
            if (run == null) {
                logger.warn("已领取运行但找不到记录：{}", runId);
                return;
            }
            String answer;
            try {
                answer = model.execute(run);
            } catch (Exception exception) {
                if (!store.fail(runId, token, "模型执行失败")) {
                    logger.info("运行失败结果已过期，等待当前工作者处理：{}", runId);
                }
                return;
            }
            if (answer == null || answer.isBlank()) {
                store.fail(runId, token, "模型未返回结果");
                return;
            }
            if (!store.complete(runId, token, answer)) {
                logger.info("运行已由其他工作者接管，忽略过期结果：{}", runId);
            }
        } finally {
            activeClaims.remove(runId, token);
        }
    }

    /** 模型调用可较慢，独立于消费者线程续租。 */
    @Scheduled(fixedDelayString = "${agent.run.lease-renew-ms:30000}")
    public void renewActiveClaims() {
        activeClaims.forEach((runId, token) -> {
            try {
                if (!store.renewLease(runId, token, LEASE_SECONDS)) {
                    activeClaims.remove(runId, token);
                }
            } catch (Exception exception) {
                logger.warn("运行续租暂时失败：{}", runId);
            }
        });
    }
}
