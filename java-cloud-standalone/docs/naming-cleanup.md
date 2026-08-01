# 命名与运维遗留清理（Phase 4）

## 已处理

| 项 | 处理 |
|----|------|
| 启动类包名 | 已是 `org.example.forum.{domain}`（如 `ForumAuthApplication`） |
| Feign 客户端包 | `org.example.forum.cloud.feign`（已从 `forumdemo.cloud.feign` 迁出） |
| 默认日志路径 | `application-core-defaults.yml`：`../logs/forum-core/forum-core.log`（不再用 `logs/backend`） |
| RabbitMQ vhost | 保留默认 `forum-demo` 以兼容现网；文档注明新环境使用 `SPRING_RABBITMQ_VIRTUAL_HOST=forum` |
| 禁止新增 `forumdemo` | ADR 已声明；新契约包使用 `org.example.forum.api.*` |

## 待手改（禁止脚本批量）

1. 已搬迁到各服务的 `org.example.forumdemo.service/impl|mapper|entity` → `org.example.forum.{domain}...`
2. `forum-core` 残留共享类随删除/收缩同步改名
3. 生产 Compose / 脚本中若仍写死 `forum-demo` vhost 或 `logs/backend`，与 Java 配置分开逐处改

## 原则

- 只在物理搬迁或单文件修改时改包名与 import
- 禁止 `sed` / IDE Replace in Path 全量执行（规范 07）
