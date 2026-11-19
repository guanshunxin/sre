package com.gsx.shop.metrics.controller;

import com.gsx.shop.metrics.service.BusinessMetricsService;
import org.springframework.web.bind.annotation.*;

/** 业务页面显示数据库快照的真实采样状态，不把 Kafka 积压当成 HTTP 故障。 */
@RestController
public class BusinessMetricsController {
    private final BusinessMetricsService service;

    public BusinessMetricsController(BusinessMetricsService service) {
        this.service = service;
    }

    @GetMapping("/api/overview")
    public Object get() {
        return service.current();
    }
}
