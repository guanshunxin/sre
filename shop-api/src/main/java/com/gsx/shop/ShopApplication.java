package com.gsx.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 可独立多实例部署的订单库存业务入口，与 AI-SRE 无运行依赖。 */
@SpringBootApplication
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        com.gsx.shop.order.config.DeliveryProperties.class)
@EnableScheduling
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
