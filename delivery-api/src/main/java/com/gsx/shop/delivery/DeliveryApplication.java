package com.gsx.shop.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 独立运费依赖，真实 HTTP 计算；不调用外部快递或产生物流订单。 */
@SpringBootApplication(scanBasePackages = "com.gsx.shop")
public class DeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class, args);
    }
}
