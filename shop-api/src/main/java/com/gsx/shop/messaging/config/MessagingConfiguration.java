package com.gsx.shop.messaging.config;

import com.gsx.shop.messaging.constants.MessagingConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** 私网单 broker 测试拓扑，两个分区供双实例消费；不是高可用 Kafka 集群。 */
@Configuration
public class MessagingConfiguration {
    @Bean
    NewTopic ordersTopic() {
        return TopicBuilder.name(MessagingConstants.ORDERS_TOPIC)
                .partitions(2)
                .replicas(1)
                .build();
    }
    /** 数据库暂时不可用时不得默认重试后丢弃业务消息；不确认，恢复后继续处理。 */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
