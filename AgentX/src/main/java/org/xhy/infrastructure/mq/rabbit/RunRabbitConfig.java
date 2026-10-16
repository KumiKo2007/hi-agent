package org.xhy.infrastructure.mq.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xhy.domain.run.constant.RunQueueNames;

/** 持久队列仅保存运行编号，不作为运行权威状态。 */
@Configuration
public class RunRabbitConfig {
    @Bean
    public DirectExchange agentRunExchange() {
        return new DirectExchange(RunQueueNames.EXCHANGE, true, false);
    }

    @Bean
    public Queue agentRunQueue() {
        return new Queue(RunQueueNames.QUEUE, true);
    }

    @Bean
    public Binding agentRunBinding(Queue agentRunQueue, DirectExchange agentRunExchange) {
        return BindingBuilder.bind(agentRunQueue).to(agentRunExchange).with(RunQueueNames.ROUTING_KEY);
    }
}
