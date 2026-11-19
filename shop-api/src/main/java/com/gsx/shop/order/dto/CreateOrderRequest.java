package com.gsx.shop.order.dto;

import jakarta.validation.constraints.*;

/** 单 SKU 订单；区域不包含真实收件人或地址。 */
public record CreateOrderRequest(
        @Min(1) long productId,
        @Min(1) @Max(20) int quantity,
        @NotNull @Pattern(regexp = "LOCAL|REMOTE") String region) {}
