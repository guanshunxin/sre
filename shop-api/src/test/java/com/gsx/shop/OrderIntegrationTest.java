package com.gsx.shop;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gsx.shop.common.exception.ShopException;
import com.gsx.shop.messaging.mapper.OutboxMapper;
import com.gsx.shop.order.client.DeliveryClient;
import com.gsx.shop.order.dto.CreateOrderRequest;
import com.gsx.shop.order.mapper.OrderMapper;
import com.gsx.shop.order.service.OrderService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;

/** 隔离真实 MySQL 验证库存、事务、幂等、消息去重与 fencing；运费仅在此事务测试中替身隔离。 */
@SpringBootTest(
        properties = {
            "spring.datasource.hikari.connection-timeout=10000",
            "workers.enabled=false",
            "spring.kafka.admin.auto-create=false",
            "spring.kafka.listener.auto-startup=false",
            "SHOP_API_TOKEN=test-api-token",
            "SHOP_METRICS_TOKEN=test-metrics-token",
            "SHOP_LAB_TOKEN=test-lab-token",
            "REDIS_PASSWORD=unused"
        })
class OrderIntegrationTest {
    static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.4.7").withDatabaseName("shop_test");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired
    OrderService orders;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OrderMapper mapper;

    @Autowired
    OutboxMapper outbox;

    @MockitoBean
    DeliveryClient delivery;

    private CreateOrderRequest request(long id, int quantity) {
        return new CreateOrderRequest(id, quantity, "LOCAL");
    }

    @Test
    void concurrentDuplicatesReserveExactlyOnceAndPayloadMismatchIsRejected() throws Exception {
        when(delivery.quote(anyString(), anyInt())).thenReturn(550L);
        String key = "duplicate-" + UUID.randomUUID();
        long before = jdbc.queryForObject("SELECT stock FROM product WHERE id=1", Long.class);
        var pool = Executors.newFixedThreadPool(6);
        try {
            var jobs = new ArrayList<Future<Long>>();
            for (int i = 0; i < 12; i++) jobs.add(pool.submit(() -> orders.create(key, request(1, 2)).id));
            var ids = new HashSet<Long>();
            for (var job : jobs) ids.add(job.get());
            assertThat(ids).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id=1", Long.class))
                .isEqualTo(before - 2);
        assertThatThrownBy(() -> orders.create(key, request(1, 3))).isInstanceOf(ShopException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_outbox WHERE order_id=(SELECT id FROM shop_order WHERE idempotency_key=?)",
                        Long.class,
                        key))
                .isEqualTo(1);
    }

    @Test
    void insufficientStockRollsBackOrderMovementAndOutbox() {
        when(delivery.quote(anyString(), anyInt())).thenReturn(550L);
        jdbc.update("UPDATE product SET stock=1 WHERE id=2");
        String key = "rollback-" + UUID.randomUUID();
        assertThatThrownBy(() -> orders.create(key, request(2, 2))).isInstanceOf(ShopException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE idempotency_key=?", Long.class, key))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id=2", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentIndependentOrdersCannotOversell() throws Exception {
        when(delivery.quote(anyString(), anyInt())).thenReturn(550L);
        jdbc.update("UPDATE product SET stock=3 WHERE id=3");
        var pool = Executors.newFixedThreadPool(6);
        int succeeded = 0;
        try {
            var jobs = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 9; i++)
                jobs.add(pool.submit(() -> {
                    try {
                        orders.create("stock-" + UUID.randomUUID(), request(3, 1));
                        return true;
                    } catch (ShopException e) {
                        return false;
                    }
                }));
            for (var j : jobs) if (j.get()) succeeded++;
        } finally {
            pool.shutdownNow();
        }
        assertThat(succeeded).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id=3", Integer.class))
                .isZero();
    }

    @Test
    void cancelledOrderCannotBeResurrectedByDuplicateMessages() {
        when(delivery.quote(anyString(), anyInt())).thenReturn(550L);
        var order = orders.create("cancel-" + UUID.randomUUID(), request(1, 1));
        orders.cancel(order.id);
        orders.cancel(order.id);
        long event = jdbc.queryForObject("SELECT id FROM order_outbox WHERE order_id=?", Long.class, order.id);
        orders.fulfill(event, order.id);
        orders.fulfill(event, order.id);
        assertThat(orders.get(order.id).status).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM stock_movement WHERE order_id=? AND reason='CANCEL'",
                        Integer.class,
                        order.id))
                .isEqualTo(1);
    }

    @Test
    void fulfillmentDeduplicatesAndExpiredLeaseCannotCommit() {
        when(delivery.quote(anyString(), anyInt())).thenReturn(550L);
        var order = orders.create("ready-" + UUID.randomUUID(), request(1, 1));
        long event = jdbc.queryForObject("SELECT id FROM order_outbox WHERE order_id=?", Long.class, order.id);
        orders.fulfill(event, order.id);
        orders.fulfill(event, order.id);
        assertThat(orders.get(order.id).status).isEqualTo("READY");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fulfillment_receipt WHERE event_id=?", Integer.class, event))
                .isEqualTo(1);
        jdbc.update(
                "UPDATE order_outbox SET lease_token='old',locked_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",
                event);
        assertThat(outbox.finish(event, "old", true)).isZero();
    }
}
