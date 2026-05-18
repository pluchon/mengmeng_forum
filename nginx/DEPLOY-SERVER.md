# 新服务器部署指南（www.nuonuoya.cn）

## 域名说明

| 地址 | 用途 |
|------|------|
| https://www.nuonuoya.cn | 用户端论坛 |
| https://nuonuoya.cn | 自动跳转到 www |
| https://admin.nuonuoya.cn | 管理后台 |

DNS 请添加 **A 记录**：`www`、`admin`、可选根域名 `nuonuoya.cn` → 服务器公网 IP。

## 服务器要求

- 已安装 **Docker** 与 **Docker Compose**（无需在宿主机安装 OpenJDK，Java 在容器内）
- 放行防火墙 **80、443**（数据库端口仅绑定 `127.0.0.1`，无需对公网开放）

## 一、本机打包（Windows 开发机）

```powershell
cd <项目根目录>/nginx
.\scripts\build-all.ps1
.\scripts\export-images.ps1
```

会生成 `nginx/package/`：

- `dist/` — 前端静态文件（含 `dist/user/live2d-assets/` 看板娘模型，约 17 MB）  
- `conf.d/`、`ssl/`、`docker-compose*.yaml`、`.env.example`  
- `images/*.tar` — Docker 镜像  

> **改过后端 Java 代码或 `application-prod.yml` 后必须重新 `build-all.ps1` 并 `export-images.ps1`**，否则服务器上的 `forum-backend` 镜像仍是旧版。

### 看板娘（Live2D）

模型源目录：`live2d/live2d-master`（与开发时 Vite 插件路径一致）。`build-all` 会自动复制到 `nginx/dist/user/live2d-assets/`，Nginx 通过 `/live2d-assets/` 提供静态访问。

- 若仓库中无 `live2d/live2d-master`，构建脚本会 **WARN** 并跳过；生产页可看板娘开关已开但模型会 404。
- 关闭看板娘：在 `forum-vue/front/.env.production` 设 `VITE_ENABLE_MASCOT=false` 后重新 `build-all.ps1`。
- 仅更新模型、不重编前端：`.\scripts\build-all.ps1 -SkipFront -SkipAdmin -SkipBackend -SkipDocker`（仍会同步 live2d）。

## 二、上传到服务器

将 `nginx/package` 目录整体上传到服务器，例如 `~/package`。

```bash
cd ~/package
cp .env.example .env
nano .env
```

**务必修改**（不可用示例占位符上线）：

- `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`REDIS_PASSWORD` 等
- **`PII_CRYPTO_SECRET`**、**`JWT_SECRET`**（各 ≥32 字符随机串；未设置会导致 `forum-backend-1` 反复崩溃、CPU 飙高）

生成随机串示例：

```bash
openssl rand -base64 32
```

## 三、导入镜像并启动

```bash
cd ~/package

docker load -i images/forum-backend.tar
docker load -i images/forum-ai-server.tar
docker load -i images/infra.tar

mkdir -p logs/backend

docker compose -f docker-compose.yaml -f docker-compose.prod.yml down
docker compose -f docker-compose.yaml -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` 会将 MySQL/Redis 等绑定到 **`127.0.0.1`**（仅本机 + SSH 可连，不暴露公网）。

### 端口已被占用（address already in use）

先查占用：`ss -tlnp | grep -E '33061|63790|54320'`，在 `.env` 中修改 `MYSQL_HOST_PORT` 等后重新 `up -d`。

## 四、初始化数据库（仅首次）

```bash
docker exec -i forum-mysql mysql -uroot -p你的ROOT密码 forum_db < create.sql
```

## 五、验证

```bash
docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
docker logs forum-backend-1 --tail 30
# 应看到 Started ForumDemoApplication

curl -s http://127.0.0.1/healthz
# 应返回 ok

curl -I https://www.nuonuoya.cn
curl -I https://admin.nuonuoya.cn

ss -tlnp | grep 33061
# 应看到 127.0.0.1:33061
```

## 六、Navicat 通过 SSH 连 MySQL

1. **SSH** 选项卡：主机 = 服务器公网 IP，用户 = `ubuntu` 等，认证 = 私钥文件  
2. **常规** 选项卡：主机 = `127.0.0.1`，端口 = `33061`（`.env` 中 `MYSQL_HOST_PORT`）  
3. 用户：`root`（密码 `MYSQL_ROOT_PASSWORD`）或 `forum_user`（`MYSQL_PASSWORD`），数据库 `forum_db`  

不要用公网 IP 直连 `3306`；生产栈未对 `0.0.0.0` 开放数据库端口。

## 常用命令

```bash
docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
docker compose logs -f forum-backend-1
docker compose logs -f nginx
docker compose exec nginx nginx -t
docker compose exec nginx nginx -s reload
```

## 更新前端后

在本机重新 `build-all.ps1`，只上传 `dist/` 覆盖服务器（含 `dist/user/live2d-assets` 若模型有变），然后：

```bash
docker compose exec nginx nginx -s reload
```

验证看板娘静态资源（将 `xiaomai` 换成实际上架的模型目录名）：

```bash
curl -I https://www.nuonuoya.cn/live2d-assets/xiaomai/xiaomai.model3.json
# 应返回 200
```
