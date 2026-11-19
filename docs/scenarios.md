# 故障验收表

先建立正常请求基线，再单独启用一个场景，观察真实请求、指标、日志及业务结果。故障控制返回“已受理”不代表 Prometheus 告警已经触发。

| 场景 | 操作及实际影响 | 应观察的证据 | 恢复与业务断言 |
|---|---|---|---|
| 正常订单 | 页面选商品下单 | MySQL 订单、库存流水、Outbox、Kafka 消费收据 | 同一订单最终 READY |
| 重复下单 | A/B 并发使用相同幂等键 | 返回同一订单 ID，库存只扣一次 | 同键不同内容 409 |
| 库存竞争 | 隔离 MySQL 测试并发抢少量库存 | 成功数不超过库存，剩余库存非负 | 不足返回 409，订单/消息一起回滚 |
| 取消与消息重放 | 暂停消费后取消订单，再恢复消费 | 取消流水一次，消费收据去重 | CANCELLED 不会重新变 READY |
| HTTP 5xx | 在 A/B 设置 HTTP_5XX | 真实 503、请求日志、5xx 速率告警 | 删除场景或 TTL 到期后停止注入 |
| 慢请求 | SLOW 给业务处理增加 1.5 秒 | 实际响应耗时、P95/P99、直方图 | 最多 60 秒，健康和取证不加延迟 |
| 运费依赖超时 | 运费服务 SLOW，再下订单 | 下游实际慢请求、订单侧 504 和相同请求 ID | 不创建订单或预留库存；客户端可同键重试 |
| 数据库行锁 | DB_LOCK 锁 SKU 1 | InnoDB 真锁等待、下单超时、503 | 最长 20 秒释放；事务回滚 |
| 连接池压力 | DB_POOL 持有 6 个真实连接 | Hikari active/pending、获取超时、503 | 指标端点仍可返回，取消后释放连接 |
| CPU / 内存 | 单线程计算、32 MiB 分配 | 真实 process CPU / JVM heap | CPU ≤5 秒，内存持有 ≤30 秒；不做 OOM |
| 消费积压 | 暂停两个实例的消费者，持续下单 | 数据库待备货数、Kafka lag、积压规则 | 恢复后继续消费，不丢单、不重复扣库存 |
| Redis 不可用 | 验证脚本实际停止 Redis | 目录接口 503，独立订单列表仍可读 | Redis 恢复后缓存读写恢复 |
| Kafka 不可用 | 实际停止 broker 后下单 | 订单受理，Outbox 未发送数增加 | broker 恢复后按相同事件 ID 重发并备货 |
| MySQL 不可用 | 实际停止数据库 | 业务 503、业务采样 UNAVAILABLE、Hikari 状态 | liveness / 指标仍独立，数据库恢复后重连 |
| 单实例宕机 | 停止 A | up=0、真实实例不可用告警；GET 经 B 返回 | POST 不自动重试，客户端保留幂等键 |
| 生产保护 | prod profile +误设 lab.enabled | 对故障入口请求返回 404 | 业务和监控口令分离，取证口令不能下单 |

`node scripts/verify.mjs --run` 执行多数真实场景；库存极小值的防超卖和事务竞争由隔离 MySQL JUnit 验证，不修改正在运行的库存来冒充测试数据库。结果保存在 `.runtime/verification.json`，失败也保留原因，不能仅凭服务启动写“全部通过”。

指标说明：

- 通用：`http_server_requests_seconds_*`、`process_cpu_usage`、`jvm_*`、`hikaricp_*`。
- 业务：`shop_orders_created_total`、`shop_orders_ready_total`、`shop_cache_requests_total{result="hit|miss"}`。
- 真实 DB 快照：`shop_orders_waiting`、`shop_outbox_pending`、`shop_business_sample_age_seconds`。双实例共享数据库，聚合使用 **max**，不能 sum 后重复计数；它们不冒充 Kafka offset lag。
- Prometheus 规则在 `infra/alert-rules.yml`。独立规则用于本项目验收；接入 AI-SRE 后，其托管规则/恢复口径以平台监控版本为准，业务专项规则不能默认等同于可自动关闭的事件。

长时间缺流量可能无法计算请求百分位；停机后缺样本不等于正常。先运行流量脚本，复核 samples 和采集时间，按真实指标判断。短时故障解除后，一分钟 rate 窗口内仍可能暂时保留错误率，这是历史样本，不是伪造新错误。
