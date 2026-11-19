package com.gsx.shop.lab.worker;

import com.gsx.shop.lab.service.LabService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 只触发暂停状态协调，到期自动恢复真实 Kafka 消费容器。 */
@Component
public class LabConsumerWorker {
    private final LabService service;

    public LabConsumerWorker(LabService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 500)
    public void poll() {
        service.reconcileConsumer();
    }
}
