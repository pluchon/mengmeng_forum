# java-cloud-standalone

萌萌论坛 **单机微服务** 父子工程（Nacos 注册发现 + Gateway + OpenFeign/LoadBalancer）。

Spring Boot **3.5.11** / Spring Cloud **2025.0.0** / Spring Cloud Alibaba **2025.0.0.0**，与仓库原 `backend` 版本对齐。

> **原 `backend/` 目录完整保留**，可继续作为单体运行；本工程是并行的微服务演进线。
>
> **交付状态（务必读清）**
> - 已完成：进程切分、Gateway/Nacos、六域 `*-api` 契约、按域物理搬迁 Service/Mapper/Entity、**默认独立库 `forum_*_db` + 独立账号撤权**、关键跨域 Feign、本地主链路 E2E（`scripts/run-e2e.ps1` pass=62 fail=0）。
> - **后续非阻塞**：去掉对整包 `forum-core` 的 Maven 依赖、删除 `DomainServicePruner`、包名全面改为 `org.example.forum.*`、收紧 `@MapperScan`。
> - 真拆分目标与阶段见 [`docs/architecture-microservices.md`](docs/architecture-microservices.md)；验收证据见 [`docs/architecture-acceptance.md`](docs/architecture-acceptance.md)。

## 模块

| 模块 | 端口 | 说明 |
|------|------|------|
| forum-gateway | 10086 | 统一入口（前端继续打此端口） |
| forum-auth | 10101 | 用户 / 登录 / 验证码 / 邮件短信 |
| forum-content | 10102 | 帖子 / 板块 / 搜索 / 推荐 / 文件；内容侧 MQ 与定时任务 |
| forum-im | 10103 | 私信 / 群聊 / 语音 / 通知；`/ws/notify` |
| forum-game | 10104 | 游戏 REST + 游戏 WebSocket；消费 `q-game-finished` |
| forum-economy | 10105 | 积分 / 签到 / VIP / 抽奖 / 商店 / 成长 |
| forum-ai | 10106 | AI / 吉祥物 / 漂流瓶 |
| forum-common | — | 跨服务真正可复用的常量/工具（禁止放 Entity/Mapper/ServiceImpl） |
| forum-*-api | — | 各域纯契约（接口 + DTO/VO，**无** `@FeignClient`） |
| forum-core | — | **过渡期**共享实现库；目标是按域搬空后删除 |

## 目标边界（真拆分）

- **HTTP**：各域 Controller 物理归属对应可启动模块。
- **代码所有权**：Service / Mapper / Entity 归属各服务模块，禁止再依赖整包 `forum-core`。
- **契约**：每服务配套 `forum-{domain}-api`；共享层只有无 `@FeignClient` 的 API + DTO；消费方自有 Feign 客户端。
- **数据**：每服务独立数据库（本地可先同一 MySQL 多库 + 独立账号，再生产拆实例）。
- **跨域**：同步门禁走 HTTP；副作用走 Outbox/MQ；展示用快照，禁止跨库 Mapper。

## 过渡期现状（将逐步拆除）

- `forum-core` 仍为过渡共享库（JWT/拦截器/少量共享实现）；各域业务实现已主要落在对应服务模块。
- `DomainServicePruner` 仅兜底残留共享 Bean，目标删除。
- 部分身份读仍依赖 core 内 `UserMapper`；拆库撤权前需继续 Feign 化。
- `forum.domain=monolith` 仅为不裁剪兼容开关，**禁止当作终态架构**。

## 启动顺序

1. Docker：MySQL / Redis / RabbitMQ（如 `forum-*-dev`）
2. Nacos 3.2.3（`nacos-server-3.2.3/nacos`）
3. 打包并启动业务服务：

```powershell
$env:JAVA_HOME = "C:\Java_soft\jdk-17"
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd"
cd java-cloud-standalone
& $mvn -DskipTests package
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-all.ps1
```

停止：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-all.ps1
```

建议启动顺序已内置在 `start-all.ps1`：auth → economy → im → content → game → ai → gateway。

## 验收

### 架构验收（真拆分门禁）

见 [`docs/architecture-microservices.md`](docs/architecture-microservices.md) 验收清单：独立打包、库隔离、契约形态、依赖图。

### 功能 E2E（多账号）

前置：Gateway `:10086` 可达；Redis 容器名 `forum-redis-dev`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-e2e.ps1
```

报告：`test-output/cloud-e2e/report.json`。

说明：私信内容审核依赖本机 AI Hub；未启动时业务码 `1125` 记为环境依赖。

## 前端

开发代理 / 生产上游指向 **Gateway :10086**。
