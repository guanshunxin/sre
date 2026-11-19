package com.gsx.shop.catalog.mapper;

import com.gsx.shop.catalog.entity.Product;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 库存条件更新和流水均参与调用方事务，扣减失败必须回滚订单。 */
@Mapper
public interface ProductMapper {
    List<Product> list();

    Product find(long id);

    /** 先取得库存排他锁，避免订单外键共享锁在扣减时并发升级死锁。 */
    Product lock(long id);

    List<com.gsx.shop.catalog.entity.StockLevel> stocks();

    int reserve(@Param("id") long id, @Param("quantity") int quantity);

    int add(@Param("id") long id, @Param("quantity") int quantity);

    int movement(
            @Param("product") long product,
            @Param("order") Long order,
            @Param("delta") int delta,
            @Param("reason") String reason);
}
