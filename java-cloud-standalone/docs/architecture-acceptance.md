# 微服务真拆分 — 架构验收记录

日期：2026-08-01  
分支：`feat/microservices-true-split`

## 门禁结果

| 项 | 结果 | 证据 |
|----|------|------|
| 服务模块之间无业务实现级 Maven 依赖 | **通过（过渡）** | 各 `forum-*` 服务 `pom` 仅依赖 `forum-core` / 基础设施，不互相依赖业务实现 jar |
| `mvn -pl forum-xxx -am -DskipTests package` | **通过** | 全 reactor（含 gateway）BUILD SUCCESS；各服务 fat jar 已产出 |
| fat jar 不含他域业务 ServiceImpl | **通过** | 抽样：auth/content/economy/im/game/ai 均无交叉的 `Article/Points/User/Message/Game/Mascot*ServiceImpl` |
| 独立库就绪 | **脚手架通过 / 默认未切换** | 六库已建并完成表复制；默认 `DB_URL` 仍为 `forum_db`，可用环境变量切换 |
| `*-api` 无 `@FeignClient` | **通过** | Feign 客户端位于 `forum-core/.../cloud/feign` 与过渡 `forum-api` Points 客户端 |
| Feign name / app name / Gateway | **保持一致** | `forum-auth`…`forum-ai` 与既有 Gateway `lb://` 约定 |
| 主链路 | **未在本轮完整 E2E 复跑** | 代码层注册收藏夹 / 积分 / VIP / 商店 entitlement / mascot 偏好已 Feign 化；建议切库后跑 `scripts/run-e2e.ps1` |
| 原 `backend/` 保留 | **通过** | 未删除单体目录 |

## Phase 完成度摘要

| Phase | 状态 |
|-------|------|
| 0 文档纠偏 | 完成 |
| 1a user 混权拆分 + 默认收藏夹 | 完成 |
| 1b 六域 api 契约 | 完成 |
| 2 代码所有权搬迁 | 完成（core 仍过渡依赖；Pruner 兜底） |
| 3 拆库 | 库/账号/复制完成；默认切读写与撤权待运维开关 |
| 4 命名清理 | 日志路径已改；Rabbit vhost 兼容 `forum-demo`；Java 包名继续随文件手改 |
| 5 架构验收 | 本文件 |

## 明确未宣称「迁移完成」的原因

1. 各服务 Maven 仍依赖 `forum-core` 过渡库  
2. 默认数据源未强制独立库  
3. 大量业务包名仍为 `org.example.forumdemo`  
4. `DomainServicePruner` 尚未删除  

## 建议下一步

1. 将 `UserMapper` 身份读全部收口 auth Feign 后，服务默认 `DB_*` 切到 `forum_*_db` 并撤跨库权限  
2. 逐文件把已搬迁代码包名改为 `org.example.forum.{domain}`  
3. 删除 `DomainServicePruner`；收紧 `@MapperScan`  
4. 复跑多用户 E2E 与游戏结算事件
