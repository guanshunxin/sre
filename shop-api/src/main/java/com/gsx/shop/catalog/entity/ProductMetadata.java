package com.gsx.shop.catalog.entity;
/** 可缓存的目录说明与价格；不包含可售库存。 */
public record ProductMetadata(long id, String name, long priceCents) {}
