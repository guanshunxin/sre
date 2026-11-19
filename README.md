# 巡航商店 · 独立业务与故障实训项目

这是可独立部署的订单库存项目，包含真实商品目录、库存、幂等下单、取消订单、异步备货和管理页面。用于给 AI-SRE 提供可核查的业务、指标、日志和源码；不依赖 AI-SRE 的数据库、密钥或运行服务。

它包含两实例 Java 订单服务、独立 Java 运费服务、Vue 3 页面、MySQL、Redis、Kafka 和独立 Prometheus。金额用整数分。订单、扣库存、流水和 Outbox 在同一事务内提交；Kafka 至少一次投递，消费者在数据库去重。“已备货”是本系统状态，不表示外部支付或真实快递已发货。

## 启动

需要 Java 21、Maven、Node ≥22.12、Docker Compose。以下命令在本目录运行：

```powershell
node scripts/prepare.mjs
mvn -s scripts/maven-settings.xml verify
npm ci --prefix frontend
npm run build --prefix frontend
docker compose --env-file .env -f infra/compose.yml up -d --build --wait
```

打开 <http://127.0.0.1:18888>，填写本目录 `.env` 的 `SHOP_API_TOKEN`。口令只留在页面内存，不写浏览器存储。初始三个商品来自 MySQL 迁移；页面不预填假订单、指标或库存。Redis 缓存目录说明与价格，库存始终从 MySQL 查询。

默认不开故障入口。要在隔离测试环境演练，显式叠加：

```powershell
docker compose --env-file .env --env-file .runtime/lab-overrides.env -f infra/compose.yml up -d --build --wait
node scripts/verify.mjs --run
node scripts/traffic.mjs --seconds=120 --concurrency=4 --mode=mixed
```

页面的“故障演练”还需填写 `SHOP_LAB_TOKEN`。HTTP 故障最多 60 秒；数据库锁/连接池最多 20 秒，CPU 最多 5 秒，内存最多 32 MiB/30 秒。演练按实例生效，暂停所有消费需同时暂停 A、B。`prod` / `production` 即使误设开关也不注册演练端点。

真实依赖停机由验证脚本执行，只操作 `gsx-sample-shop` 项目，退出时请求恢复服务和清除场景，不删除卷。不要把整套验证脚本用于承载实际用户的环境。具体场景、指标与限制见 [故障验收表](docs/scenarios.md)。

## 部署、监控与源码

- [服务器部署与 AI-SRE 接入](docs/deployment.md)：端口、口令、网络访问、源码上传和日志边界。
- `powershell -File scripts/package.ps1 -Kind source`：生成可供 Git 导入前检查或 AI-SRE ZIP 上传的源码包，排除配置、依赖、构建产物和运行数据。
- `powershell -File scripts/package.ps1 -Kind deployment`：构建后生成部署包，含两个 JAR、已构建页面与 Compose；目标服务器只需 Docker 和 Node 初始化脚本，不必安装 Maven。
- 独立 Prometheus：<http://127.0.0.1:19094>，真实抓取三个带 Bearer 认证的指标端点，并加载七条告警规则。这里没有配置 Alertmanager 或飞书，不会自动向原故障群发送演练消息。
- [本轮实际验收记录](docs/verification.md)：7 项后端测试、真实依赖启停与故障验证、桌面/手机页面结果和已知边界。
- 停止：`docker compose --env-file .env -f infra/compose.yml stop`，保留所有卷。首次生成的 `.env`、`.runtime/*.env` 和数据库卷都需私下备份。

## 技术与实现边界

沿用上级仓库验证过的 Java 21 / Spring Boot 3.5.16、MyBatis 3.0.5、Flyway 11.15.0、Vue 3.5.44 / TypeScript 5.9.3。具体依赖锁定在 POM 与 package-lock。参考 SmartRAG 的 Controller/Service 职责划分，没有复制旧项目业务数据、密钥或 JPA 实现；SQL 均在 Mapper XML。项目有独立 Maven 父工程，移动本目录即可继续开发。

这是业务测试样本，覆盖常见故障，不承诺覆盖所有生产故障。没有真实用户体系、多租户权限、支付/快递集成、Kafka 集群 HA、磁盘满、真实 OOM 或任意命令执行。CPU 是单线程有界负载，内存场景是安全范围的真实分配。日志输出结构化 JSON，附带请求关联 ID；受保护日志接口只保留每进程最近 2000 条，重启丢失历史，不等同于集中日志平台。
