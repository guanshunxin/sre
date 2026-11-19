package com.gsx.shop.messaging.worker;

import com.gsx.shop.messaging.constants.MessagingConstants;
import com.gsx.shop.messaging.service.MessageService;
import com.gsx.shop.order.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 仅负责调度和消息确认；订单状态、事务及去重由 Service 完成。 */
@Component
@ConditionalOnProperty(name = "workers.enabled", havingValue = "true", matchIfMissing = true)
public class OrderMessageWorker {
    private final MessageService messages;
    private final OrderService orders;

    public OrderMessageWorker(MessageService messages, OrderService orders) {
        this.messages = messages;
        this.orders = orders;
    }

    @Scheduled(fixedDelay = 500)
    public void publish() {
        messages.publishOne();
    }

    @KafkaListener(id = "fulfillment", topics = MessagingConstants.ORDERS_TOPIC)
    public void receive(String body) {
        if (!body.matches("[1-9][0-9]{0,17}:[1-9][0-9]{0,17}"))
            throw new IllegalArgumentException("Invalid order event");
        String[] parts = body.split(":");
        orders.fulfill(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }
}
