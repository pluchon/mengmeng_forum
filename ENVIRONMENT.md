# 环境变量说明

业务密钥与 AI Key 的配置方式已统一至 **[WINDOWS-ENV.md](./WINDOWS-ENV.md)**（Windows / Docker / IDE）。

- 本地 Java / AI：用户环境变量或 `scripts/dev-secrets.ps1`（勿提交）
- Docker 部署：`nginx/.env`（勿提交，参考 `nginx/.env.example`）
- 生产服务器：`~/package/.env`

启动后可在 Java 控制台查看 `ForumDemoApplication` 的环境变量检查输出（`FOUND` / `MISSING`）。
