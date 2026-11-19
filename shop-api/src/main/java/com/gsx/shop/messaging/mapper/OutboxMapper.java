package com.gsx.shop.messaging.mapper;

import com.gsx.shop.messaging.entity.OutboxEvent;
import org.apache.ibatis.annotations.*;

/** 行锁领取与租约 fencing；不在事务内等待 Kafka 网络。 */
@Mapper
public interface OutboxMapper {
    int insert(long order);

    OutboxEvent claim();

    int lease(@Param("id") long id, @Param("token") String token);

    int finish(@Param("id") long id, @Param("token") String token, @Param("success") boolean success);

    long pendingCount();
}
