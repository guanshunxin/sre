package com.gsx.shop.catalog.entity;
/** 商品真实库存与整数分价格；不缓存可售数量，避免缓存过期造成误导。 */
public record Product(long id, String name, long priceCents, int stock) {}
