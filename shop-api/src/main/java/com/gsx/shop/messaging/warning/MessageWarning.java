package com.gsx.shop.messaging.warning;
/** 稳定警告标识；不得记录 broker 认证信息或外部响应原文。 */
public enum MessageWarning {
    KAFKA_PUBLISH_FAILED,
    RESOURCE_EXERCISE_FAILED
}
