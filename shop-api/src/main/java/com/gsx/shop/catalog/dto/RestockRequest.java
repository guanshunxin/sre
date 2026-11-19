package com.gsx.shop.catalog.dto;

import jakarta.validation.constraints.*;

/** 单次人工补货数量，不接受负数扣库存。 */
public record RestockRequest(@Min(1) @Max(10000) int quantity) {}
