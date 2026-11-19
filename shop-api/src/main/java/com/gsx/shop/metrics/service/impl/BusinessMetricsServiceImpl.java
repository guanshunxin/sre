package com.gsx.shop.metrics.service.impl;

import com.gsx.shop.messaging.mapper.OutboxMapper;
import com.gsx.shop.metrics.service.BusinessMetricsService;
import com.gsx.shop.order.mapper.OrderMapper;
import io.micrometer.core.instrument.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** DB 业务指标异步采样，NaN 明确表示不可用；Hikari/JVM 指标仍可独立抓取。 */
@Service
public class BusinessMetricsServiceImpl implements BusinessMetricsService {
    private final OutboxMapper outbox;
    private final OrderMapper orders;
    private volatile double pending = Double.NaN, accepted = Double.NaN;
    private volatile Instant at = Instant.EPOCH;

    public BusinessMetricsServiceImpl(OutboxMapper outbox, OrderMapper orders, MeterRegistry registry) {
        this.outbox = outbox;
        this.orders = orders;
        Gauge.builder("shop_outbox_pending", this, s -> s.pending).register(registry);
        Gauge.builder("shop_orders_waiting", this, s -> s.accepted).register(registry);
        Gauge.builder(
                        "shop_business_sample_age_seconds",
                        this,
                        s -> Math.max(0, Instant.now().getEpochSecond() - s.at.getEpochSecond()))
                .register(registry);
    }

    public void sample() {
        try {
            pending = outbox.pendingCount();
            accepted = orders.acceptedCount();
            at = Instant.now();
        } catch (org.springframework.dao.DataAccessException unavailable) {
            pending = Double.NaN;
            accepted = Double.NaN;
        }
    }

    public Map<String, Object> current() {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", Double.isFinite(pending) && Double.isFinite(accepted) ? "SUCCESS" : "UNAVAILABLE");
        values.put("sampledAt", at);
        values.put("outboxPending", Double.isFinite(pending) ? (long) pending : null);
        values.put("ordersWaiting", Double.isFinite(accepted) ? (long) accepted : null);
        return values;
    }
}
