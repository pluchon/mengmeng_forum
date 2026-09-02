# mengmeng_forum 协作约定

AI 增强的社区论坛。四块：`java-cloud-standalone`（Spring Boot 3.5 六域微服务）、
`ai-server`（Python + LangGraph）、`forum-vue/front`（Vue 3 + Vite）、`deploy` / `scripts`（部署脚本）。
完整架构见根目录 README.md。

## 边界

- **Java 是业务真相**：权限、状态机、落库、跨域编排都在这里。
- **Python 只做生成与检查**：出检查结果 / 摘要 / 候选 ID + 分数，不写社区业务库。
  可见性过滤、分页、排序一律留在 Java。
- **前端不直接调模型**，也不信自己带的用户 ID。

## Java 端

- 调用链固定 Controller → Service → Mapper；写操作带事务；业务状态只允许 Service 推进。
- 跨域只依赖对方的 `xxx-api`，消费方本地声明 Feign。
  **不共享 Entity / Mapper / Service 实现。**
- 六域各自落库（auth / content / im / game / economy / ai），不要跨库直连。

## 数据库

- 建库文件在各域 `server/src/main/resources/db/create.sql`。
- **有数据的库禁止跑 `init-db` / `reset-db`**，改表只用审核过的前向迁移。

## OSS 文件

- **上传只落 `_pending/`，业务绑定时才转正**（`OssPendingPromoter.promoteIfPending`）。
  没人绑定的由 OSS 生命周期规则按天数收走，孤儿文件因此不会产生。
- 新增上传路径或绑定路径，**两件事都要做**：上传写待定区，绑定处调转正。
- **正式目录校验不得削弱**——它只认正式目录，是漏改绑定点时的唯一警报，
  能让问题当场报错而不是几天后文件被删才发现。
- 不要用 `url.contains(业务目录)` 判来源：待定 URL 同样能通过。

## 发布

- 生产密钥在服务器 `/opt/forum-config/prod.env`，发布包不带真实 `.env`。
- 不要把真实域名、证书路径、邮箱提交进仓库。
