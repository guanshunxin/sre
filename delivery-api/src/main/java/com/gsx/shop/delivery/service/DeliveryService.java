package com.gsx.shop.delivery.service;
/** 有限配送区域的报价业务，金额单位为分。 */
public interface DeliveryService {
    /** 校验区域与数量，返回含基础费用和商品数量费用的运费。 */
    long quote(String region, int quantity);
}
