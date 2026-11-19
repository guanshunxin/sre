# Sample Shop 开发约束

这是独立部署的订单库存测试业务项目，包名 `com.gsx.shop`，不依赖 AI-SRE 私有代码或配置。遵守上级开发规范：Controller → Service/Impl → Mapper/XML、中文职责和关键事务注释、真实测试、必要幂等和并发保障。借鉴 SmartRAG 的职责划分，SQL 使用本仓库 MyBatis 方式，不复制 JPA 或旧依赖。

金额用分，库存扣减必须带条件；订单、库存流水与 Outbox 原子提交。Kafka 至少一次投递，消费者在数据库去重，不能声称外部支付或物流已完成。请求日志只记录路由模板、时间、状态、耗时与关联 ID，不记录口令或请求正文。

故障接口只允许 `demo` 且 `lab.enabled=true`，生产 profile 强制不可用；必须独立鉴权、有界并自动失效。依赖停机脚本只能操作 Compose 项目 `gsx-sample-shop`。不连接现有 AI-SRE 数据库、真实飞书或 LLM，不自动加入监控生产路由。
