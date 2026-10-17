package org.xhy.domain.run.constant;

/** 运行工作通知的稳定 RabbitMQ 地址。 */
public final class RunQueueNames {
    public static final String EXCHANGE = "agent.run.exchange";
    public static final String QUEUE = "agent.run.work";
    public static final String ROUTING_KEY = "agent.run.work";

    private RunQueueNames() {
    }
}
