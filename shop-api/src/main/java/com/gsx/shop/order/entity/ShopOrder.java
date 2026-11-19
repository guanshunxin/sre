package com.gsx.shop.order.entity;

import java.time.LocalDateTime;

/** 可追溯订单快照，idempotencyKey 为唯一业务键，requestHash 防止跨请求复用。 */
public class ShopOrder {
    public Long id;
    public String idempotencyKey;
    public String requestHash;
    public long productId;
    public int quantity;
    public String region;
    public long totalCents;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime fulfilledAt;
}
