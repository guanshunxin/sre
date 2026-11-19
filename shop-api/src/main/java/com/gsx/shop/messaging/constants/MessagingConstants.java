package com.gsx.shop.messaging.constants;
/** 本项目消息契约，不向现有 AI-SRE Kafka 发送数据。 */
public final class MessagingConstants {
    public static final String ORDERS_TOPIC = "shop-orders-v1";

    private MessagingConstants() {}
}
