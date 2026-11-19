package com.gsx.shop.metrics.service;

import java.util.Map;

/** 真实业务积压采样；采集失败记录不可用，不能补成零。 */
public interface BusinessMetricsService {
    /** 更新数据库快照，不在 Prometheus 抓取线程中阻塞数据库。 */
    void sample();
    /** 返回采样时间和真实队列/订单数量。 */
    Map<String, Object> current();
}
