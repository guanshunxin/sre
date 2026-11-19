# 本轮验收记录

日期：2026-11-18。所有注入和依赖启停均发生在独立 `gsx-sample-shop`，没有调用模型、接入原 AI-SRE 告警路由或发送飞书消息。

## 已运行

- Java 21 执行独立 Maven reactor 的 `spotless:apply verify`：7 项测试通过、无跳过。5 项使用隔离 MySQL Testcontainers，验证跨线程重复键、防超卖、库存不足回滚、取消与消息去重、租约失效；2 项验证运费及生产边界。运费在事务单测中被替身隔离，在后面的容器验收使用真实 HTTP。
- `npm ci --prefix frontend`、`npm run build --prefix frontend`：TypeScript 检查及最终构建通过，依赖已锁定。
- `docker compose ... up -d --build --wait`：独立 MySQL、Redis、Kafka、两个订单实例、运费服务、页面代理和 Prometheus 实际启动。
- `node scripts/verify.mjs --run`：完整通过，记录在本地 `.runtime/verification.json`。实际执行重复下单、真实 Kafka 备货、取消与积压、HTTP 5xx、慢请求、运费超时、行锁、连接池耗尽、有界内存/CPU、Redis/Kafka/MySQL 停机、单实例退出及恢复。
- Prometheus 实际抓取三个 Bearer 认证端点，验证 HTTP 错误率、备货积压和实例不可用规则真实进入 firing；行锁/连接池检查使用真实 SQL 与 Hikari 指标，不用伪造的指标正文。
- Edge 桌面 1600×1050 / 手机 390×844 实际查看商品、创建订单、查看订单和读取演练状态；5 张截图在 `screenshots/`，口令已遮盖。无页面脚本错误、横向溢出、API 响应密钥泄漏或浏览器持久化口令。
- 原 AI-SRE 运行只读复查通过：原六个采集目标健康，飞书连接 CONNECTED，知识索引可用，demo 无故障，未重放历史付费任务。

## 真实测试发现并修正的问题

并发订单最初因外键检查持有商品共享锁，随后扣库存升级排他锁而出现死锁；现在在插订单之前先锁商品，保留库存条件更新，测试并发通过。运费调用使用完整 900ms Future 等待预算，超时取消等待并返回明确 504，发生在库存事务之前。手机图标导航补充了可访问名称，已重新构建和验收。

## 交付边界

这是可部署的真实业务样本，已有数据库、缓存、消息和 HTTP 流程；不是具备账户、支付、物流、容灾等能力的生产商城。Kafka 是私网单 broker。库存极小值竞争在隔离测试数据库验证，运行验证脚本保留带验证幂等键的真实订单，不清空用户数据。

日志是每实例最多 2000 条的真实请求记录和 Docker JSON 日志；没有集中日志/链路平台，也没有自动接通 AI-SRE 的通用日志工具。独立 Prometheus 没有配置消息通知出口。服务器部署后的网络、防火墙、凭据和实际 AI-SRE 接入，需要在目标环境再次验收。

可选重跑页面验收：在 `.runtime/qa` 安装 `playwright@1.64.1`，Windows 使用已安装 Edge，Linux 需安装对应 Chromium，然后执行 `node scripts/verify-frontend.mjs`；也可通过 `PLAYWRIGHT_MODULE` 指向已有 Playwright 模块文件。依赖目录与浏览器工具不会进入源码包。

实现参考：[Boot 3.5 指标](https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html)、[Spring Kafka 消费监听](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html)、[MySQL 8.4 锁定读](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)。这些资料用于指标、消费和行锁语义，项目版本沿用当前仓库兼容基线。
