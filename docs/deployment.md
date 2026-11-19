# 服务器部署与 AI-SRE 接入

## 部署业务项目

建议先在独立测试服务器运行。首次使用本地示例配置不需要开放数据库、Redis 或 Kafka 端口，它们只在该 Compose 私网互通。

1. 拷贝整个项目并按 README 构建，或使用本机构建后的 `sample-shop-deployment.zip`。不要把开发机 `.env`、运行数据或 AI-SRE 密钥打包上传。
2. 在服务器运行 `node scripts/prepare.mjs`，生成新的三种访问口令和依赖密码。也可使用一次性 `node:22` Docker 容器执行这个脚本，宿主机无需常驻 Node 服务。
3. `.env` 设置 `WEB_BIND=0.0.0.0`、`WEB_PORT=18888`；将访问限制在测试人员或 HTTPS 反向代理后。业务口令只用于本测试项目，不是生产身份系统。
4. 指标端口默认只绑定本机。若 AI-SRE 在另一台服务器，将 `METRICS_BIND` 设为业务服务器的私网地址，并用防火墙只允许 AI-SRE/Prometheus 服务器访问 `19081`、`19082`、`19083`。
5. 默认启动 standard 环境，故障入口关闭。只在明确隔离的测试服务器叠加 `.runtime/lab-overrides.env` 开启演练。实际业务服务器不要运行演练脚本。

服务器未安装 Node 时，可以在项目目录执行一次：

```sh
docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/project" -w /project node:22-bookworm-slim node scripts/prepare.mjs
```

这样生成文件归当前宿主机用户所有；随后按下面命令构建/启动已包含 JAR 和页面的部署包。

```sh
docker compose --env-file .env -f infra/compose.yml up -d --build --wait
# 隔离环境演练时才使用：
docker compose --env-file .env --env-file .runtime/lab-overrides.env -f infra/compose.yml up -d --wait
```

单机 Compose 用于测试验证，不包含 Kafka HA/ACL/TLS、数据库备份计划或滚动升级编排。要承载真实客户，需要另行完善用户权限、TLS、数据保护和高可用，不能直接把实验配置当生产基线。

## 接入 AI-SRE

在 AI-SRE 创建服务 `shop-api / staging`，配置协作人员，再到“监控接入”填：

```text
http://业务服务器私网地址:19081/actuator/prometheus
http://业务服务器私网地址:19082/actuator/prometheus
```

认证选 Bearer，填本项目 `SHOP_METRICS_TOKEN`。请求范围可留空，或选 `/api/orders`。在页面勾选“允许平台连接以上新指标地址”，测试、保存并确认 Prometheus 已加载。当前平台将授权记在本服务的配置版本中，不需要修改主机名单或重启；旧版平台才使用环境变量白名单。

本机 Docker Desktop / Ubuntu WSL 场景已验证使用 `host.docker.internal` 加上述端口可以从平台容器访问商城。此时地址例如 `http://host.docker.internal:19081/actuator/prometheus`；另一台服务器仍填写平台可达的实际 IP/域名。

运费服务另建 `delivery-api / staging`，地址是 `19083/actuator/prometheus`。不要把两种不同业务服务混放进同一个托管配置，否则指标和告警归属会混淆。

导入源码时，可以将此目录放进独立 Git 仓库，或运行 `scripts/package.ps1 -Kind source` 上传 ZIP。目录根包含两个业务模块与公共代码；源码分析应选择项目根快照，以便关联订单调用与运费实现。包不含 `.env`、依赖、运行数据或 JAR。

## 日志怎么用

`docker compose ... logs shop-a shop-b delivery-api` 是实际结构化日志，`requestId` 用于串联一次订单和下游报价；数据库/依赖错误不会把 SQL、凭据或第三方正文返回浏览器。

受取证口令保护的 `/internal/observations/logs?start=UTC时间&end=UTC时间&limit=50` 返回当前实例真实请求的时间、路由模板、状态、耗时和请求 ID，最多 200 条，最多查询 24 小时。进程仅保留最新 2000 条，重启历史丢失。它没有自动接入 AI-SRE 现有 demo 专用日志工具；通用日志适配或集中日志是后续工作，不能宣称平台 Agent 现在已读到此项目全部日志。

## 启停与升级

停止保留数据：`docker compose --env-file .env -f infra/compose.yml stop`。不要在保留订单时执行 `down -v`。更新源码后重跑构建和 `up -d --build --wait`；Flyway 保留历史迁移，只添加新版本。两实例共享商品与订单库是本项目业务布局，不接入 AI-SRE 的业务数据库。
