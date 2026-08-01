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
