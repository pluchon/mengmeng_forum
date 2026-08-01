# 微服务真拆分 — 架构验收记录

日期：2026-08-01  
分支：`feat/microservices-true-split`

## 门禁结果

| 项 | 结果 | 证据 |
|----|------|------|
| 服务模块无互相业务实现依赖 | **通过** | 各服务依赖 `forum-platform` + `*-api`，不依赖他域 Boot 模块 |
| 不再依赖整包 `forum-core` | **通过** | 模块已更名为 `forum-platform`（JWT/拦截器/Feign/远程适配/共享 VO）；域 Mapper 已迁出 |
| `DomainServicePruner` | **已删除** | `DomainServicePruner` / `Config` / `DomainServicePackages` 已移除 |
| `@MapperScan` 本域生效 | **通过** | classpath 上仅本域 Mapper：auth 含 `UserMapper*`，content 含 `ArticleMapper*`，economy/im/game/ai fat jar 均不含二者 |
| `mvn … package` | **通过** | 含 gateway 全 reactor BUILD SUCCESS |
| 独立库默认切换 | **通过** | 各服务默认 `forum_*_db` + 独立账号；跨库 GRANT 已撤销 |
| `*-api` 无 `@FeignClient` | **通过** | Feign 在 `forum-platform/.../cloud/feign` |
| 内部契约路径 | **通过** | Internal API 独立 Controller，避免双前缀 404 |
| 主链路 E2E | **通过（切库后）** | `scripts/run-e2e.ps1` pass=62 fail=0；本轮 Phase2 后再复跑确认 |
| 原 `backend/` 保留 | **通过** | 未删除单体目录 |

## Phase 完成度

| Phase | 状态 |
|-------|------|
| 0 文档纠偏 | 完成 |
| 1a/1b 混权拆分 + api 契约 | 完成 |
| 2 代码所有权 / 去 Pruner / 收紧 Mapper | **完成**（`forum-platform` 替代 `forum-core`） |
| 3 拆库切读 | 完成 |
| 4 包名 `org.example.forum.*` | **部分**：启动类与 Feign 已在 `org.example.forum.*`；存量业务包仍为 `org.example.forumdemo`（规范禁止脚本批量改名，随手改逐文件演进） |
| 5 架构验收 | 本文件 |

## 本轮所有权迁移摘要

- auth：`UserMapper` / `UserFollow*` / `UserLoginLog*` / `UserAuthSnapshotServiceImpl`
- content：`ArticleMapper` / `ArticleHotRankingServiceImpl`；新增 `ArticleInternalApi`
- platform：共享 `User`/`Article`/`UserFavoriteFolder` 实体（会话与 VO）、Feign、远程适配；**无** User/Article Mapper
