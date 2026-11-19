package com.gsx.shop.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 固定运费上游的部署配置，不接受 HTTP 调用方提供任意地址。 */
@ConfigurationProperties("delivery")
public record DeliveryProperties(String baseUrl) {}
