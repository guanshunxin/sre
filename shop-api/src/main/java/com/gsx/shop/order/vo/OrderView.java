package com.gsx.shop.order.vo;

import com.gsx.shop.order.entity.ShopOrder;
import java.time.LocalDateTime;

/** 公开订单投影，不回传持久化幂等键或内部请求摘要。 */
public record OrderView(
        long id,
        long productId,
        int quantity,
        String region,
        long totalCents,
        String status,
        LocalDateTime createdAt,
        LocalDateTime fulfilledAt) {
    public static OrderView from(ShopOrder row) {
        return new OrderView(
                row.id,
                row.productId,
                row.quantity,
                row.region,
                row.totalCents,
                row.status,
                row.createdAt,
                row.fulfilledAt);
    }
}
