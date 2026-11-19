package com.gsx.shop.order.service;

import com.gsx.shop.order.dto.CreateOrderRequest;
import com.gsx.shop.order.entity.ShopOrder;
import java.util.List;

/** 订单、库存及异步备货的业务契约。 */
public interface OrderService {
    /** 同一键同一内容复用结果；并发不会重复扣减，同一键不同内容拒绝。 */
    ShopOrder create(String key, CreateOrderRequest request);
    /** 最近一百条实际订单。 */
    List<ShopOrder> list();
    /** 读取订单和实际备货状态。 */
    ShopOrder get(long id);
    /** 仅待备货订单可取消，释放库存与状态转换原子提交。 */
    ShopOrder cancel(long id);
    /** 消费者按事件 ID 幂等处理；本地备货不包含外部支付/发货。 */
    void fulfill(long event, long order);
}
