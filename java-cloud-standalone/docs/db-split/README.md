# 数据库拆分说明

## 策略

1. **本地默认**：同一 MySQL 容器内多个库 + 独立账号（本目录 `V20260801010__create_service_databases.sql`）。
2. **生产目标**：每服务独立数据库实例。
3. **过渡**：业务代码先按域切断 Mapper 访问；数据可先复制再切读写。

## 表归属

见 [`architecture-microservices.md`](../architecture-microservices.md)。

## 迁移步骤（每个域）

1. 在目标库执行该域表结构（从 `create.sql` / 增量 migration 抽取）。
2. 从 `forum_db` 导出该域表数据并导入目标库。
3. 修改对应服务 `application.yml` 的 datasource URL/账号。
4. 撤销该服务账号对其它库的权限。
5. 冒烟：本域读写 + 跨域 Feign。

## 配置开关

各服务可通过环境变量覆盖：

```text
DB_URL=jdbc:mysql://127.0.0.1:33306/forum_xxx_db?...
DB_USERNAME=forum_xxx
DB_PASSWORD=...
```

未切换前仍默认连接共享 `forum_db`（兼容过渡）。

## 已落地（本地 MySQL `forum-mysql-dev`）

1. 六库 + 六账号：`V20260801010__create_service_databases.sql`
2. 表归属清单：`table-ownership.md`
3. 从 `forum_db` 复制结构与数据：
   - `copy-tables-from-forum-db.sql`（economy/auth/content 主体）
   - `copy-tables-remaining.sql`（content 补齐 + im/game/ai；`user_favorite_folder` 排除虚拟列 `default_marker`）

校验抽样（复制后本地实测）：`forum_economy_db.points_wallet`、`forum_auth_db.user`、`forum_content_db.article` 行数与共享库一致量级。

## 切读写

各服务设置环境变量后重启即可切到独立库（示例 economy）：

```powershell
$env:DB_URL = "jdbc:mysql://127.0.0.1:33306/forum_economy_db?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "forum_economy"
$env:DB_PASSWORD = "forum_economy_pass"
```

**注意**：过渡期仍有部分服务进程通过 `forum-core` 访问 `UserMapper` 等共享表；完全撤销跨库权限前，须确认该服务 classpath 上不再有他域 Mapper，且身份/资料读已全部 Feign 化。回滚时把 `DB_*` 指回 `forum_db`。

## 跨域一致性（已/待）

| 模式 | 现状 |
|------|------|
| 同步 Feign | Points / Growth / UserInternal / FavoriteFolder / ShopEntitlement / VipTier / AiUsage / MascotPreference |
| Outbox + MQ | im `forum_outbox_message`；game `game_settlement_event` 由 game 消费后投递积分 |
| 快照 | 帖子/消息等展示字段继续写入时固化；禁止新增长期跨库 JOIN |
