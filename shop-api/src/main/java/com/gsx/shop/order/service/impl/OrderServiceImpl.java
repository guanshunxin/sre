package com.gsx.shop.order.service.impl;

import com.gsx.shop.catalog.mapper.ProductMapper;
import com.gsx.shop.common.exception.*;
import com.gsx.shop.messaging.mapper.OutboxMapper;
import com.gsx.shop.order.client.DeliveryClient;
import com.gsx.shop.order.dto.CreateOrderRequest;
import com.gsx.shop.order.entity.ShopOrder;
import com.gsx.shop.order.enums.OrderStatus;
import com.gsx.shop.order.mapper.OrderMapper;
import com.gsx.shop.order.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** MySQL 唯一键为幂等权威；本地事务外调用运费服务，Kafka 通过持久化 Outbox 最终发送。 */
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orders;
    private final ProductMapper products;
    private final OutboxMapper outbox;
    private final DeliveryClient delivery;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;

    public OrderServiceImpl(
            OrderMapper orders,
            ProductMapper products,
            OutboxMapper outbox,
            DeliveryClient delivery,
            PlatformTransactionManager manager,
            MeterRegistry metrics) {
        this.orders = orders;
        this.products = products;
        this.outbox = outbox;
        this.delivery = delivery;
        this.tx = new TransactionTemplate(manager);
        this.metrics = metrics;
    }

    public ShopOrder create(String key, CreateOrderRequest request) {
        if (key == null || !key.matches("[a-zA-Z0-9_-]{8,80}")) throw new ShopException(ShopError.INVALID);
        String hash = request.productId() + ":" + request.quantity() + ":" + request.region();
        var existing = orders.byKey(key);
        if (existing != null) return match(existing, hash);
        var product = products.find(request.productId());
        if (product == null) throw new ShopException(ShopError.NOT_FOUND);
        long shipping = delivery.quote(request.region(), request.quantity());
        try {
            var result = tx.execute(status -> {
                // 必须早于订单 INSERT 的外键检查取得排他锁，消除同 SKU 的共享锁升级环。
                var locked = products.lock(product.id());
                if (locked == null) throw new ShopException(ShopError.NOT_FOUND);
                var row = new ShopOrder();
                row.idempotencyKey = key;
                row.requestHash = hash;
                row.productId = product.id();
                row.quantity = request.quantity();
                row.region = request.region();
                row.totalCents = Math.addExact(Math.multiplyExact(locked.priceCents(), request.quantity()), shipping);
                row.status = OrderStatus.ACCEPTED.name();
                if (orders.insert(row) != 1) throw new IllegalStateException("Order insert missing");
                if (products.reserve(product.id(), request.quantity()) != 1)
                    throw new ShopException(ShopError.STOCK_EMPTY);
                if (products.movement(product.id(), row.id, -request.quantity(), "RESERVE") != 1
                        || outbox.insert(row.id) != 1) throw new IllegalStateException("Order side records missing");
                return orders.find(row.id);
            });
            metrics.counter("shop_orders_created").increment();
            return result;
        } catch (DuplicateKeyException conflict) {
            var winner = orders.byKey(key);
            if (winner == null) throw conflict;
            return match(winner, hash);
        }
    }

    private ShopOrder match(ShopOrder order, String hash) {
        if (!order.requestHash.equals(hash)) throw new ShopException(ShopError.IDEMPOTENCY_CONFLICT);
        return order;
    }

    public List<ShopOrder> list() {
        return orders.recent();
    }

    public ShopOrder get(long id) {
        var row = orders.find(id);
        if (row == null) throw new ShopException(ShopError.NOT_FOUND);
        return row;
    }

    @Transactional
    public ShopOrder cancel(long id) {
        var row = orders.lock(id);
        if (row == null) throw new ShopException(ShopError.NOT_FOUND);
        if (row.status.equals(OrderStatus.CANCELLED.name())) return row;
        if (!row.status.equals(OrderStatus.ACCEPTED.name())) throw new ShopException(ShopError.CANNOT_CANCEL);
        if (orders.cancel(id) != 1
                || products.add(row.productId, row.quantity) != 1
                || products.movement(row.productId, id, row.quantity, "CANCEL") != 1)
            throw new IllegalStateException("Cancellation conflict");
        return orders.find(id);
    }

    @Transactional
    public void fulfill(long event, long order) {
        // 先锁订单再登记事件：与取消共享锁顺序，已取消订单不会被消息重新置为 READY。
        var row = orders.lock(order);
        if (row == null) throw new ShopException(ShopError.NOT_FOUND);
        if (orders.received(event) > 0) return;
        if (orders.receipt(event, order) != 1) throw new IllegalStateException("Receipt insert missing");
        if (row.status.equals(OrderStatus.ACCEPTED.name())) {
            if (orders.ready(order) != 1) throw new IllegalStateException("Fulfillment conflict");
            metrics.counter("shop_orders_ready").increment();
        }
    }
}
