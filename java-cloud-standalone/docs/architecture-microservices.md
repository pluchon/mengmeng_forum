# 微服务真拆分架构决策

## 1. 现状判定

当前 `java-cloud-standalone` 处于**真拆分主路径已完成**：

- 六个业务进程 + Gateway/Nacos 已运行
- 六域 `forum-*-api` 纯契约已建立；Feign 客户端在 `forum-platform`
- 各域 Service/Mapper 已物理归属对应服务；`UserMapper`/`ArticleMapper` 仅在 auth/content fat jar
- 默认连接 `forum_*_db` + 独立账号；跨库权限已撤销
- `DomainServicePruner` **已删除**
- 原 `forum-core` 已更名为 **`forum-platform`**（JWT/拦截器/Feign/远程适配/共享 VO，不含域 Mapper）
- 业务代码包名大量仍为 `org.example.forumdemo.*`（启动类/Feign 为 `org.example.forum.*`；禁止脚本批量改名）

结论：**六库 + Feign + 去 Pruner + 平台库收口已完成；存量 Java 包名手改演进。**

## 2. 官方依据

| 主题 | 要点 | 来源 |
|------|------|------|
| Maven multi-module | 只解决构建聚合与继承，不是微服务 | [Maven POM](https://maven.apache.org/guides/introduction/introduction-to-the-pom.html) |
| 共享代码 | Boot 可执行应用不应当依赖；共享逻辑放独立 library | [Spring Boot](https://docs.spring.io/spring-boot/how-to/build.html) |
| OpenFeign | 可共享无 `@FeignClient` 的 API；禁止共享带 `@FeignClient` 的客户端接口 | [OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html) |
| 数据边界 | Database per Service；跨服务用 Saga/Outbox，不用分布式大事务 | [Saga](https://microservices.io/patterns/data/saga.html) / [Outbox](https://microservices.io/patterns/data/transactional-outbox.html) |

## 3. 目标模块布局

```
java-cloud-standalone/
  forum-gateway/
  forum-common/                 # Result、JWT 工具、常量（无 Entity/Mapper/ServiceImpl）
  forum-auth-api/               # 纯 API + DTO/VO（无 @FeignClient）
  forum-content-api/
  forum-im-api/
  forum-game-api/
  forum-economy-api/
  forum-ai-api/
  forum-auth/                   # 独立 Boot + 本域 DB
  forum-content/                # 消费方自有 @FeignClient
  forum-im/
  forum-game/
  forum-economy/
  forum-ai/
```

包命名统一为 `org.example.forum.{domain}...`；禁止新增 `org.example.forumdemo`。旧包随物理搬迁逐文件改名，禁止脚本批量重构。

## 4. 数据权威归属

| 服务 | 数据库（目标） | 权威表 |
|------|----------------|--------|
| auth | `forum_auth_db` | `user`（仅身份）、`user_login_log`、`user_follow` |
| content | `forum_content_db` | 帖子/板块/收藏/标签/推荐/content outbox |
| im | `forum_im_db` | 私信/系统消息/群聊/`user_chat_emoji` |
| game | `forum_game_db` | `game_*` |
| economy | `forum_economy_db` | `points_wallet`、`points_log`、签到/抽奖/商店/成长/VIP |
| ai | `forum_ai_db` (+ 既有 PG 会话) | 漂流瓶/看板娘/AI 用量与创作 |

### 必须先拆的 `user` 混权字段

从 `user` 迁出到归属域：

- `points` → economy.`points_wallet`
- `vip_tier` / `vip_expire_at` → economy VIP 表
- `lottery_pity_draws` → economy 抽奖保底表
- `mascot_model_id` → ai 用户偏好表
- `article_count` 可由 auth 保留计数，仅通过内部 API 变更

## 5. 跨域协作规则

### 同步 HTTP（门禁 / 即时余额）

- 用户存在性、公开资料、发帖计数 ±1
- `requireFormalUser`
- 需要即时结果的积分扣加（商店/VIP/抽奖/AI 计费）

### 异步事件 + Outbox

- 注册赠分 / 成长建档 / 默认收藏夹 / RAG 索引
- 审核结果系统消息、标签反馈
- 游戏结算积分（先落 `game_settlement_event`）
- 通知类消费收口到 im

### 固化快照

帖子/回复/私信/群/漂流瓶/游戏房写入时固化昵称头像，禁止运行时跨库 JOIN 用户。

## 6. 禁止事项

- 宣称「迁移完成」但服务仍依赖**他域业务实现 jar** 或仍嵌入他域 Mapper
- 跨服务 compile 依赖他域业务实现 jar
- Entity / Mapper / ServiceImpl 放入 `forum-common`
- 把带 `@FeignClient` 的接口放进服务端也依赖的 `*-api`
- Gateway 写业务库
- 非归属域注入他域 Mapper
- 用脚本批量 rename / replace / move（项目规范 07）
- 在 `main` 直接开发提交（走 `feat/*` 分支）

## 7. 阶段与验收

| 阶段 | 完成标准 |
|------|----------|
| Phase 0 | 文档不再写「已收尾完成」；本 ADR 生效 |
| Phase 1a | `points_wallet` 成为积分权威；注册默认收藏夹有跨域契约 |
| Phase 1b | auth/economy/content 等 `*-api` 落地；Feign 客户端在消费方；不再 Feign 返回 Entity |
| Phase 2 | 各服务不再依赖整包 core（现为 `forum-platform`）；无 `DomainServicePruner`；classpath 上仅本域 Mapper |
| Phase 3 | 各服务独立库/账号；跨域只走 HTTP/MQ |
| Phase 4 | 新代码与已搬迁代码使用 `org.example.forum.*`（存量 `forumdemo` 逐文件手改） |
| Phase 5 | `-pl` 独立打包启动；依赖图与主链路验收通过 |

### 架构验收清单

详见 [`architecture-acceptance.md`](architecture-acceptance.md)。

- [x] 服务模块之间无业务实现级 Maven 依赖
- [x] 每服务可 `mvn -pl forum-xxx -am -DskipTests package`
- [x] 每服务默认只连接自己的数据库账号
- [x] `*-api` 中不存在 `@FeignClient`
- [x] Feign `name` / Gateway `lb://` / `spring.application.name` 一致
- [x] 主链路 E2E（切库后）
- [x] 原 `backend/` 仍可独立运行（并行保留）
