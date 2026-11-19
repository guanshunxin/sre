package com.gsx.shop.messaging.service;
/** 可靠消息意图服务，投递成功和消费者完成备货分别记录。 */
public interface MessageService {
    /** 短事务领取，事务外发送，未知发送结果允许重复投递并由消费者去重。 */
    void publishOne();
}
