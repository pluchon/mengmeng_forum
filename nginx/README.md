# 萌萌论坛 — Nginx / Docker 部署

本目录提供 **Nginx 反向代理 + 双实例 Spring Boot + AI 服务 + 中间件** 的一键编排，与 `forum-vue/front/vite.config.js` 的 API 前缀保持一致。

## 目录结构

```
nginx/
├── conf.d/
│   ├── 00-core.conf              # upstream、gzip、限流
│   ├── 10-local-http.conf        # 本地 HTTP（默认启用，无需证书）
│   ├── examples/
│   │   └── 20-prod-https.conf    # 生产 HTTPS 模板（复制后启用）
│   └── snippets/
│       ├── forum-api.inc         # /ws + REST API 反代
│       └── forum-spa.inc         # 前端静态资源
├── dist/
│   ├── user/                     # 用户端 npm run build 产物
│   └── admin/                    # 管理端 build 产物
├── ssl/                          # fullchain.pem + privkey.pem（HTTPS）
├── logs/
├── docker-compose.yaml
└── .env.example
```

## 一键打包（推荐）

在项目根目录执行：

```powershell
# Windows
cd nginx
.\scripts\build-all.ps1
```

```bash
# Linux / macOS
cd nginx && bash scripts/build-all.sh
```

脚本会依次：构建用户端与管理端前端并同步到 `dist/` → 构建 `forum-backend:latest` → `docker compose build ai-server`。

仅打包前端、不构建镜像：`.\scripts\build-all.ps1 -SkipDocker`

## 快速开始（本地 HTTP）

### 1. 构建（或用手动步骤）

见上方「一键打包」。

### 2. 启动栈

```bash
cd nginx
cp .env.example .env
docker compose up -d
```

访问：

| 入口 | 地址 |
|------|------|
| 用户端 | http://localhost |
| 管理端 | http://admin.localhost（需在 hosts 增加 `127.0.0.1 admin.localhost`） |
| 健康检查 | http://localhost/healthz |
| RabbitMQ 控制台 | http://localhost:15672 |

WebSocket 站内信：`ws://localhost/ws/notify?token=...`（与 Vite 开发代理路径一致）。

## 生产 HTTPS（nuonuoya.cn）

已配置 `conf.d/20-prod-https.conf`：

- 用户端：https://www.nuonuoya.cn  
- 管理端：https://admin.nuonuoya.cn  
- 证书：`ssl/www.nuonuoya.cn.pem` + `ssl/www.nuonuoya.cn.key`

详见 [DEPLOY-SERVER.md](./DEPLOY-SERVER.md)。

## 架构说明

- **API 负载均衡**：`backend-1` / `backend-2`，`least_conn`
- **WebSocket**：`ip_hash` 粘滞到同一后端（避免多实例内存 Session 丢失）
- **静态资源**：`index.html` 不缓存；带 hash 的 js/css 缓存 30 天
- **限流**：API 约 30 req/s/IP（可 burst 60）

## 新增后端 API 前缀时

同步修改三处：

1. `forum-vue/front/vite.config.js` 的 proxy 正则
2. `conf.d/00-core.conf` 的 `$is_forum_api` map
3. `conf.d/snippets/forum-api.inc` 的 `location ~` 正则

## 常用命令

```bash
docker compose ps
docker compose logs -f nginx
docker compose exec nginx nginx -t          # 检查配置
docker compose exec nginx nginx -s reload   # 热重载
```

## 注意事项

- Compose **服务名**为 `backend-1` / `backend-2`（upstream 已对齐，勿写 container_name）
- 无 SSL 证书时不要启用 `conf.d/20-prod-https.conf`，否则 Nginx 启动会失败
- 默认账号密码仅适合本地；生产务必修改 `.env` 中全部密钥
