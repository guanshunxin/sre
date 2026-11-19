package com.gsx.shop.delivery.controller;

import com.gsx.shop.delivery.service.DeliveryService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 运费 HTTP 入口，只调用业务服务。 */
@RestController
public class DeliveryController {
    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/api/delivery/quote")
    public Map<String, Long> quote(@RequestParam String region, @RequestParam int quantity) {
        return Map.of("feeCents", service.quote(region, quantity));
    }
}
