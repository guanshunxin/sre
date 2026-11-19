package com.gsx.shop.order.mapper;

import com.gsx.shop.order.entity.ShopOrder;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 订单唯一键去重、领取时行锁和消费者条件更新。 */
@Mapper
public interface OrderMapper {
    ShopOrder find(long id);

    ShopOrder lock(long id);

    ShopOrder byKey(String key);

    int insert(ShopOrder order);

    List<ShopOrder> recent();

    int cancel(long id);

    int ready(long id);

    int receipt(@Param("event") long event, @Param("order") long order);

    int received(long event);

    long acceptedCount();
}
