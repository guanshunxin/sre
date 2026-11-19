package com.gsx.shop.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 两个独立应用共用的边界配置，不包含业务数据库或消息状态。 */
@Configuration
@EnableConfigurationProperties(AccessProperties.class)
public class CommonConfiguration {}
