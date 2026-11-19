package com.gsx.shop.metrics.worker;

import com.gsx.shop.metrics.service.BusinessMetricsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 独立有界采样，不让业务数据库故障阻止指标端点返回。 */
@Component
public class BusinessMetricsWorker {
    private final BusinessMetricsService service;

    public BusinessMetricsWorker(BusinessMetricsService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 3000)
    public void sample() {
        service.sample();
    }
}
