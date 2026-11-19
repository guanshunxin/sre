package com.gsx.shop.delivery.service.impl;

import com.gsx.shop.common.exception.*;
import com.gsx.shop.delivery.service.DeliveryService;
import org.springframework.stereotype.Service;

/** 运费计算使用整数金额，业务拒绝与服务器故障分开。 */
@Service
public class DeliveryServiceImpl implements DeliveryService {
    public long quote(String region, int quantity) {
        if (quantity < 1 || quantity > 20) throw new ShopException(ShopError.INVALID);
        long base =
                switch (region) {
                    case "LOCAL" -> 500;
                    case "REMOTE" -> 1500;
                    default -> throw new ShopException(ShopError.INVALID);
                };
        return base + quantity * 50L;
    }
}
