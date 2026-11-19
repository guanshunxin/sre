package com.gsx.shop.messaging.entity;
/** 数据库领取的待发消息，token 防止旧执行者提交结果。 */
public record OutboxEvent(long id, long orderId, String leaseToken) {}
