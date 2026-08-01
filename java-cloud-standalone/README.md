# java-cloud-standalone

萌萌论坛 **单机微服务** 父子工程（Nacos 注册发现 + Gateway + OpenFeign/LoadBalancer）。

Spring Boot **3.5.11** / Spring Cloud **2025.0.0** / Spring Cloud Alibaba **2025.0.0.0**，与仓库原 `backend` 版本对齐。

> **原 `backend/` 目录完整保留**，可继续作为单体运行；本工程是并行的微服务演进线。
>
> **交付状态**
> - 已完成：六进程 + Gateway/Nacos、六域 `*-api`、域内 Service/Mapper 物理归属、**`forum-platform` 替代 `forum-core`**、删除 `DomainServicePruner`、默认独立库 `forum_*_db`、跨域 Feign、主链路 E2E。
> - 包名：启动类与 Feign 在 `org.example.forum.*`；存量业务代码包名 `org.example.forumdemo` 禁止脚本批量改名，随文件手改演进。
> - 详情见 [`docs/architecture-microservices.md`](docs/architecture-microservices.md)、[`docs/architecture-acceptance.md`](docs/architecture-acceptance.md)。

## 模块

| 模块 | 端口 | 说明 |
|------|------|------|
| forum-gateway | 10086 | 统一入口（前端继续打此端口） |
| forum-auth | 10101 | 用户 / 登录 / 验证码 / 邮件短信 |
| forum-content | 10102 | 帖子 / 板块 / 搜索 / 推荐 / 文件 |
| forum-im | 10103 | 私信 / 群聊 / 语音 / 通知；`/ws/notify` |
| forum-game | 10104 | 游戏 REST + WebSocket |
| forum-economy | 10105 | 积分 / 签到 / VIP / 抽奖 / 商店 / 成长 |
| forum-ai | 10106 | AI / 吉祥物 / 漂流瓶 |
| forum-common | — | 域名常量、特性开关等轻量公共 |
| forum-*-api | — | 各域纯契约（接口 + DTO/VO，**无** `@FeignClient`） |
| forum-platform | — | 跨域支撑：JWT/拦截器/Feign 客户端/远程适配/共享 VO（**不含**各域 Mapper） |

## 目标边界（真拆分）

- **HTTP**：各域 Controller 物理归属对应可启动模块。
- **代码所有权**：Service / Mapper 归属各服务模块；平台库不承载域 Mapper。
- **契约**：每服务配套 `forum-{domain}-api`；Feign 客户端在 `forum-platform`（或消费方）。
- **数据**：每服务独立数据库（本地同一 MySQL 多库 + 独立账号）。
- **跨域**：同步门禁走 HTTP；副作用走 Outbox/MQ；展示用快照。

## 本地启动

见 `scripts/start-all.ps1`；E2E：`scripts/run-e2e.ps1`（Gateway `:10086`）。
