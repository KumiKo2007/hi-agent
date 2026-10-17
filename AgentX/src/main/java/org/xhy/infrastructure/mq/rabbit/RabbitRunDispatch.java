package org.xhy.infrastructure.mq.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.xhy.domain.run.constant.RunQueueNames;
import org.xhy.domain.run.repository.RunDispatch;

/** RabbitMQ 适配器只派发可重复的运行编号。 */
@Component
public class RabbitRunDispatch implements RunDispatch {
    private final RabbitTemplate rabbit;

    public RabbitRunDispatch(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void dispatch(String runId) {
        rabbit.convertAndSend(RunQueueNames.EXCHANGE, RunQueueNames.ROUTING_KEY, runId);
    }
}
