package com.gsx.shop.lab.service;

import com.gsx.shop.lab.dto.LabRequest;
import java.util.Map;

/** 仅隔离 demo 可开始资源演练，每实例独立控制，取消和过期都会请求释放资源。 */
public interface LabService {
    /** 启动一个有界场景；已有资源任务未结束时拒绝叠加。 */
    Map<String, Object> start(LabRequest request);
    /** 返回真实活动任务数和消费容器暂停状态。 */
    Map<String, Object> status();
    /** 请求取消，不把已发取消信号说成所有资源已经释放。 */
    Map<String, Object> clear();
    /** 由调度入口调整 Kafka 容器，普通消息不丢弃。 */
    void reconcileConsumer();
}
