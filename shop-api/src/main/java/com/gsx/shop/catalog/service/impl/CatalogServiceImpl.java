package com.gsx.shop.catalog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsx.shop.catalog.constants.CatalogConstants;
import com.gsx.shop.catalog.entity.*;
import com.gsx.shop.catalog.mapper.ProductMapper;
import com.gsx.shop.catalog.service.CatalogService;
import com.gsx.shop.common.exception.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 目录说明/价格读穿 Redis 缓存，库存每次查真实数据库；缓存错误明确失败，不伪装命中。 */
@Service
public class CatalogServiceImpl implements CatalogService {
    private final ProductMapper mapper;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final ObjectMapper json;

    public CatalogServiceImpl(
            ProductMapper mapper, StringRedisTemplate redis, MeterRegistry metrics, ObjectMapper json) {
        this.mapper = mapper;
        this.redis = redis;
        this.metrics = metrics;
        this.json = json;
    }

    public List<Product> list() {
        String cached = redis.opsForValue().get(CatalogConstants.CACHE_KEY);
        metrics.counter("shop_cache_requests", "result", cached == null ? "miss" : "hit")
                .increment();
        List<ProductMetadata> metadata;
        try {
            if (cached == null) {
                metadata = mapper.list().stream()
                        .map(p -> new ProductMetadata(p.id(), p.name(), p.priceCents()))
                        .toList();
                redis.opsForValue()
                        .set(CatalogConstants.CACHE_KEY, json.writeValueAsString(metadata), Duration.ofMinutes(5));
            } else metadata = Arrays.asList(json.readValue(cached, ProductMetadata[].class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new ShopException(ShopError.DEPENDENCY_UNAVAILABLE);
        }
        var stocks = mapper.stocks().stream().collect(Collectors.toMap(StockLevel::id, StockLevel::stock));
        return metadata.stream()
                .map(p -> new Product(p.id(), p.name(), p.priceCents(), stocks.get(p.id())))
                .toList();
    }

    @Transactional
    public void restock(long product, int quantity) {
        if (quantity < 1 || quantity > 10000) throw new ShopException(ShopError.INVALID);
        if (mapper.add(product, quantity) != 1) throw new ShopException(ShopError.NOT_FOUND);
        if (mapper.movement(product, null, quantity, "RESTOCK") != 1)
            throw new IllegalStateException("Inventory movement missing");
    }
}
