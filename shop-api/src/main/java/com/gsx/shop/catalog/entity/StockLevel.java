package com.gsx.shop.catalog.entity;
/** 每次从数据库读取的实际可售数量。 */
public record StockLevel(long id, int stock) {}
