# java-cloud-standalone

萌萌论坛 **单机微服务** 父子工程（共享 MySQL `forum_db` + Nacos 注册发现 + Gateway + OpenFeign/LoadBalancer）。

Spring Boot **3.5.11** / Spring Cloud **2025.0.0** / Spring Cloud Alibaba **2025.0.0.0**，与仓库原 `backend` 版本对齐。

> **原 `backend/` 目录完整保留**，可继续作为单体运行；本工程是并行的微服务演进线，交付状态：**本阶段已收尾完成**。

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
| forum-common / forum-api / forum-core | — | 公共库、Feign 契约、共享 Entity/Mapper/接口与基础设施 |

## 边界约定

- **HTTP**：各域 Controller 物理归属对应可启动模块。
- **业务 Bean**：`DomainServicePruner` 按 `forum.domain` 只装载本域 `service.impl.*`；另保留少量共享实现（登录鉴权、关注读、AiHub、FileService、热帖分、VipCenter、WS Redis 推送、Feign remote）。
- **跨域写读（Feign）**：
  - 积分 → `PointsFeignClient` → `forum-economy`
  - 用户查询/发帖计数 → `UserInternalFeignClient` → `forum-auth`
  - 成长建档/正式用户校验 → `GrowthInternalFeignClient` → `forum-economy`
  - 系统消息创建 → `SystemMessageInternalFeignClient` → `forum-im`
- **MQ**：内容审核/通知由 `forum-content`（`mq-consumer=true`）消费；对局结束结算由 `forum-game` 的 `GameFinishedMqConsumer` 消费。
- **WebSocket**：`/ws/notify` 仅 `forum-im`；游戏 WS 由 `game-runtime` 独立装配。
- **共享库**：Entity / Mapper / Service 接口仍在 `forum-core`（同库策略下不强制物理搬迁 Service 源码）。
- **`forum.domain=monolith`**：仅作「不裁剪」兼容开关，**无**独立 monolith 启动模块。
- **原 `backend/`**：完整保留，互不删除。

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

## 验收 E2E（多账号）

前置：Gateway `:10086` 可达；Redis 容器名 `forum-redis-dev`（脚本用其注入一次性验证码票据）。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-e2e.ps1
```

报告写入仓库忽略目录：`test-output/cloud-e2e/report.json`。

覆盖：Gateway 健康、验证码、注册/登录（3 账号）、关注、积分/签到/成长/VIP/抽奖、兴趣画像、收藏与标签、IM 会话/发信/系统消息/群/语音 ICE、游戏资料、漂流瓶、Feign 内部接口、登出。

说明：私信内容审核依赖本机 AI Hub（默认 `127.0.0.1:5000`）；未启动时业务码 `1125` 记为环境依赖，不判为微服务拆分失败。

## 前端

开发代理 / 生产上游指向 **Gateway :10086**。
