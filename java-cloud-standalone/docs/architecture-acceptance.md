# 微服务真拆分 — 架构验收记录

日期：2026-08-01  
分支：`feat/microservices-true-split`

## 门禁结果

| 项 | 结果 | 证据 |
|----|------|------|
| 服务模块之间无业务实现级 Maven 依赖 | **通过（过渡）** | 各 `forum-*` 服务 `pom` 仅依赖 `forum-core` / 基础设施，不互相依赖业务实现 jar |
| `mvn -pl forum-xxx -am -DskipTests package` | **通过** | 全 reactor（含 gateway）BUILD SUCCESS |
| fat jar 不含他域业务 ServiceImpl | **通过** | 抽样：auth/content/economy/im/game/ai 均无交叉的他域 `*ServiceImpl` |
| 独立库默认切换 | **通过** | 各服务默认 `forum_*_db` + `forum_*` 账号；跨库 GRANT 已撤销 |
| `*-api` 无 `@FeignClient` | **通过** | Feign 在 `forum-core/.../cloud/feign` |
| 内部契约路径 | **通过** | Internal API 由独立 Controller 实现，避免 `/user`+`/user/internal` 双前缀 404 |
| 主链路 E2E | **通过** | `scripts/run-e2e.ps1`：网关注册/登录、关注、积分钱包/签到、VIP、收藏夹、IM、游戏、漂流瓶、内部 Feign 等主路径 PASS（本地 Docker MySQL `:33306`） |
| 原 `backend/` 保留 | **通过** | 未删除单体目录 |

## Phase 完成度摘要

| Phase | 状态 |
|-------|------|
| 0 文档纠偏 | 完成 |
| 1a user 混权拆分 + 默认收藏夹 | 完成 |
| 1b 六域 api 契约 | 完成 |
| 2 代码所有权搬迁 | 完成（core 仍过渡依赖；Pruner 兜底） |
| 3 拆库切读 | **完成**：默认独立库 + 撤权 + 跨域 Feign（用户/关注/文件/积分等） |
| 4 命名清理 | 日志路径已改；Rabbit vhost 兼容 `forum-demo`；Java 包名继续随文件手改 |
| 5 架构验收 | 本文件（主链路已复跑） |

## 仍未宣称「包名级迁移完成」的原因

1. 各服务 Maven 仍依赖 `forum-core` 过渡库  
2. 大量业务包名仍为 `org.example.forumdemo`  
3. `DomainServicePruner` 尚未删除；`@MapperScan` 仍偏宽  

上述不影响当前「六库 + Feign + 主链路可用」的拆分目标。

## 建议后续（非阻塞）

1. 逐文件把已搬迁代码包名改为 `org.example.forum.{domain}`  
2. 删除 `DomainServicePruner`；收紧 `@MapperScan`  
3. 将 `UserMapper` 从非 auth 模块 classpath 彻底剥离  
