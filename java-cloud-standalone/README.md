# java-cloud-standalone

萌萌论坛 **单机微服务** 父子工程（共享 MySQL `forum_db` + Nacos 注册发现 + Gateway + OpenFeign/LoadBalancer）。

Spring Boot **3.5.11** / Spring Cloud **2025.0.0** / Spring Cloud Alibaba **2025.0.0.0**，与仓库原 `backend` 版本对齐。

> **原 `backend/` 目录完整保留**，可继续作为单体运行；本工程是并行的微服务演进线。

## 模块

| 模块 | 端口 | 说明 |
|------|------|------|
| forum-gateway | 10086 | 统一入口（前端继续打此端口） |
| forum-auth | 10101 | 用户 / 登录 / 验证码 / 邮件短信 |
| forum-content | 10102 | 帖子 / 板块 / 搜索 / 推荐 / 文件；MQ 与内容定时任务 |
| forum-im | 10103 | 私信 / 群聊 / 语音 / 通知；`/ws/notify` |
| forum-game | 10104 | 游戏 REST + 游戏 WebSocket |
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
- **WebSocket**：`/ws/notify` 仅 `forum-im`；游戏 WS 由 `game-runtime` 独立装配。
- **共享库**：Entity / Mapper / Service 接口仍在 `forum-core`（同库策略下不强制物理搬迁 Service 源码）。
- **原 `backend/`**：完整保留，互不删除。

## 冒烟（Gateway :10086）

已验证：七服务端口监听、登录（验证码票据）、`/points/wallet`、`/growth/overview`、`/user/getUserByIdForLogin`、`/article/getHotArticleList`、`/search/article`、`/user/internal/{id}/exists`。

## 启动顺序

1. Docker：MySQL / Redis / RabbitMQ
2. Nacos 3.2.3（`nacos-server-3.2.3/nacos`）
3. 业务服务：建议先 **auth + economy + im**，再 content / game / ai
4. forum-gateway

### Maven（IDEA 自带）

```powershell
$env:JAVA_HOME = "C:\Java_soft\jdk-17"
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd"
cd java-cloud-standalone
& $mvn -DskipTests package
```

## 前端

开发代理 / 生产上游指向 **Gateway :10086**。
