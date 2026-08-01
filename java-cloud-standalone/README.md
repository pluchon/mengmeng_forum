# java-cloud-standalone

萌萌论坛 **单机微服务** 父子工程（共享 MySQL `forum_db` + Nacos 注册发现 + Gateway + OpenFeign/LoadBalancer）。

Spring Boot **3.5.11** / Spring Cloud **2025.0.0** / Spring Cloud Alibaba **2025.0.0.0**，与仓库原 `backend` 版本对齐。

> **原 `backend/` 目录完整保留**，可继续作为单体运行；本工程是并行的微服务演进线。

## 模块

| 模块 | 端口 | 说明 |
|------|------|------|
| forum-gateway | 10086 | 统一入口（前端继续打此端口） |
| forum-auth | 10101 | 用户 / 登录 / 验证码 / 邮件短信（Controller 物理归属） |
| forum-content | 10102 | 帖子 / 板块 / 搜索 / 推荐 / 文件；MQ 消费与内容定时任务 |
| forum-im | 10103 | 私信 / 群聊 / 语音 / 通知；`/ws/notify` |
| forum-game | 10104 | 游戏 REST + 游戏 WebSocket |
| forum-economy | 10105 | 积分 / 签到 / VIP / 抽奖 / 商店 / 成长 |
| forum-ai | 10106 | AI / 吉祥物 / 漂流瓶 |
| forum-common / forum-api / forum-core | — | 公共库、Feign 契约、共享 Service/Mapper |

## 边界约定（当前阶段）

- **HTTP 入口**：各域 Controller 已物理迁入对应可启动模块，不再依赖运行时裁剪。
- **业务实现**：Service / Mapper / Entity 暂仍在 `forum-core`（共享库 + 同库），后续按域继续抽出。
- **积分写路径**：非 `economy` 进程通过 `PointsFeignClient` → `forum-economy` 内部接口落库；`economy` 本地走 `PointsServiceImpl`。

## 启动顺序

1. Docker：MySQL / Redis / RabbitMQ（已有 `forum-*-dev` 即可）
2. Nacos 3.2.3（仓库根目录 `nacos-server-3.2.3/nacos`）
3. 业务服务（auth / content / im / game / economy / ai）—— **economy 需先于依赖积分的跨域写操作**
4. forum-gateway

### Nacos（Windows）

```powershell
cd nacos-server-3.2.3\nacos\bin
.\startup.cmd -m standalone
```

控制台默认：`http://127.0.0.1:8848/nacos`（账号密码一般为 nacos/nacos）。

### Maven（IDEA 自带，未配 PATH 时）

```powershell
$env:JAVA_HOME = "C:\Java_soft\jdk-17"
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd"
cd java-cloud-standalone
& $mvn -DskipTests package
```

各服务在 IDEA 中运行对应 `*Application` 主类，或：

```powershell
& $mvn -pl forum-auth -am spring-boot:run
```

## 前端

开发代理 / 生产上游指向 **Gateway :10086**，API 路径保持不变（`/user`、`/article` 等）。

## Feign

- `UserFeignClient` → `forum-auth`：`/user/internal/{id}/exists`
- `PointsFeignClient` → `forum-economy`：余额 / 幂等查询 / 加减积分（含 sourceType、idempotencyKey）
