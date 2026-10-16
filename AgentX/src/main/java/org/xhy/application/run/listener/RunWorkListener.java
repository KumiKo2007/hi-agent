package org.xhy.application.run.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.xhy.application.run.service.RunWorker;
import org.xhy.domain.run.constant.RunQueueNames;

/** 队列可重复投递；真正的领取与结果提交由数据库约束。 */
@Component
public class RunWorkListener {
    private final RunWorker worker;

    public RunWorkListener(RunWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = RunQueueNames.QUEUE, ackMode = "AUTO")
    public void receive(String runId) {
        worker.process(runId);
    }
}
