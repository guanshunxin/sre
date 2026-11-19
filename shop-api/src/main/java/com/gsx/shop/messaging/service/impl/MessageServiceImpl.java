package com.gsx.shop.messaging.service.impl;

import com.gsx.shop.messaging.constants.MessagingConstants;
import com.gsx.shop.messaging.entity.OutboxEvent;
import com.gsx.shop.messaging.mapper.OutboxMapper;
import com.gsx.shop.messaging.service.MessageService;
import com.gsx.shop.messaging.warning.MessageWarning;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 至少一次发布：超时不能断言未发送，重领保留相同事件 ID，由数据库消费收据去重。 */
@Service
public class MessageServiceImpl implements MessageService {
    private final OutboxMapper mapper;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;

    public MessageServiceImpl(
            OutboxMapper mapper, KafkaTemplate<String, String> kafka, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.kafka = kafka;
        this.tx = new TransactionTemplate(manager);
    }

    public void publishOne() {
        OutboxEvent event = tx.execute(s -> {
            var row = mapper.claim();
            if (row == null) return null;
            String token = UUID.randomUUID().toString();
            if (mapper.lease(row.id(), token) != 1) throw new IllegalStateException("Outbox claim conflict");
            return new OutboxEvent(row.id(), row.orderId(), token);
        });
        if (event == null) return;
        boolean success = false;
        try {
            kafka.send(
                            MessagingConstants.ORDERS_TOPIC,
                            String.valueOf(event.orderId()),
                            event.id() + ":" + event.orderId())
                    .get(5, TimeUnit.SECONDS);
            success = true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException
                | org.apache.kafka.common.KafkaException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn(
                            "{}; eventId={}, errorType={}",
                            MessageWarning.KAFKA_PUBLISH_FAILED,
                            event.id(),
                            failure.getClass().getSimpleName());
        }
        // 只有仍持有同一租约才提交；失效租约已发出的重复消息不会二次扣库存/备货。
        mapper.finish(event.id(), event.leaseToken(), success);
    }
}
