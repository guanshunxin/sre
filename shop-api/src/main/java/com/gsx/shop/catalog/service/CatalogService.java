package com.gsx.shop.catalog.service;

import com.gsx.shop.catalog.entity.Product;
import java.util.List;

/** 商品目录及库存维护契约；Redis 仅缓存说明，真实库存仍来自 MySQL。 */
public interface CatalogService {
    /** 读取真实库存与缓存说明，缓存依赖故障必须明确暴露。 */
    List<Product> list();
    /** 补货与库存流水同事务提交。 */
    void restock(long product, int quantity);
}
