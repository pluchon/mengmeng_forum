## 萌部落社区 v1.2

> 本项目出于个人兴趣爱好搭建；线上地址仅供学习交流。

## 部分界面演示

![image-20260605175623912](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175624402.png)

![image-20260605175652260](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175652453.png)

![image-20260605175708728](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175708955.png)

![image-20260605175730012](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175730614.png)

![image-20260605175744816](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175745055.png)

![image-20260605175802603](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175803233.png)

![image-20260605175822507](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175822653.png)

> 管理界面还没完全做好，目前比较糙

![image-20260605175951669](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605175951857.png)

![image-20260605180011517](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260605180011700.png)

***

线上地址：

- 用户端：`https://www.nuonuoya.cn`

> 前后端分离技术社区：发帖（图文 / 视频）、评论、私信、抽奖、搜索、帖子标签；发布前 AI 审核；多实例部署时私信支持跨实例实时推送；看板娘支持 RAG 推荐帖子、MCP 联网与出行工具。

---

## 目录

- [v1.2 更新摘要](#v12-更新摘要)
- [项目概览](#项目概览)
- [整体架构](#整体架构)
- [功能一览](#功能一览)
- [核心流程](#核心流程)
- [本地开发](#本地开发)
- [生产部署](#生产部署)
- [配置说明](#配置说明)
- [常见问题](#常见问题)
- [仓库结构](#仓库结构)

---

## v1.2 更新摘要

- **视频帖**：发帖支持视频模式；大文件（>200MB）走 FFmpeg 服务压缩/重封装后上传 OSS
- **帖子标签**：按版块绑定标签，搜索与详情展示标签
- **审核增强**：文本 → 图片 → **视频** 三阶段；私有 OSS 视频自动签名 URL；超大视频抽帧兜底审核
- **看板娘**：登录用户会话落库；RAG 推荐站内帖子；Tavily 搜索 + 百度地图 MCP
- **部署脚本**：`package/up.sh` 日常更新（权限修复、`docker load`、强制重建）；`start.sh` 首次部署 / 初始化库

---

## 项目概览

| 目录 | 说明 |
|------|------|
| `forum-demo` | Java 后端（Spring Boot）：业务 API、鉴权、MQ、WebSocket、审核状态 |
| `ai-server` | Python：AI 审核 / 写作 / 看板娘 / 语义搜索 / RAG |
| `forum-vue` | 用户端（Vue 3 + Vite 6） |
| `forum-vue-admin` | 管理端（Vue 3 + Arco） |
| `nginx` | Nginx 配置、Docker Compose、打包脚本、`ffmpeg` 视频服务 |

---

## 整体架构

```mermaid
flowchart TB
  U[用户端 Web] --> N[Nginx]
  A[管理端 Web] --> N
  N --> J[Java 后端 forum-demo]
  N --> F[FFmpeg 视频服务]

  J --> M[(MySQL)]
  J --> R[(Redis)]
  J --> Q[(RabbitMQ)]
  J --> F

  J --> P[Python AI ai-server]
  P --> PG[(PostgreSQL)]
  P --> OSS[(阿里云 OSS)]
```

- 用户端 / 管理端 → **Nginx**（静态 `dist/` + 反代 API）
- Java → MySQL / Redis / RabbitMQ / FFmpeg / ai-server
- ai-server → PostgreSQL（LangGraph checkpoint）、DashScope、OSS 签名读私有媒体

---

## 功能一览

### 用户端

- 发帖 / 评论 / 楼中楼（富文本 / Markdown）
- **图文帖 / 视频帖**、封面、相册（最多 15 张）
- **帖子标签**（版块内申请 / 绑定）
- 发帖 **AI 异步审核**（通过才发布）
- 图片压缩 + AI 审核 + OSS；视频 FFmpeg 处理后上传 OSS
- 私信 WebSocket、积分 / 签到 / 商城 / 抽奖、热帖榜
- 智能搜索（DB 快搜 + AI 语义增强）
- 看板娘：多模型、会话历史、站内帖子 RAG、联网与地图工具

### 管理端

- 帖子 / 评论 / 公告 / 抽奖等内容管理
- 系统字典、菜单、部门、角色（RBAC 预置表）

---

## 核心流程

### 1) 发帖审核（异步 + 幂等）

1. 用户提交 → Java 状态改为「审核中」，生成 `taskId`，投递 `q-audit-article`
2. Python worker 消费：`validate_text` → `validate_images` → **`validate_video`**（有视频时）→ `summarize`
3. 结果回 MQ → Java 条件更新状态并通知用户

**视频审核要点（v1.2）**

- DashScope 需能访问视频 URL；**私有 OSS** 由 ai-server 用 `ALIYUN_*` / `OSS_*` 生成**签名 URL**
- 视频 >100MB 或 DashScope 拉取失败时：FFmpeg **抽帧** → 走图片审核兜底
- ai-server 容器须配置与 Java 相同的 OSS 环境变量（见 `docker-compose.yaml`）

```mermaid
sequenceDiagram
  participant FE as 前端
  participant J as Java
  participant MQ as RabbitMQ
  participant P as ai-server
  participant DS as DashScope

  FE->>J: 提交审核
  J->>MQ: 审核任务(含 videoUrl)
  MQ->>P: worker 消费
  P->>P: 文本/图片审核
  P->>P: OSS 签名 videoUrl
  P->>DS: 视频审核(或抽帧兜底)
  P->>MQ: 审核结果
  MQ->>J: 更新帖子状态
```

### 2) 视频上传

用户选择视频后，前端可**后台上传**并展示进度；后端按体积分流，大文件经 FFmpeg 再写入 OSS。Nginx `/file/` 代理超时 **3600s**，避免长视频压缩卡住。

- **≤200MB**：Java 直传 OSS
- **>200MB**：Java → FFmpeg（H.264+AAC 则 **remux** 不重编码，否则 **ultrafast** 重编码）→ 回传字节流 → OSS
- 绑定帖子：保存草稿 / 提交时调用 `setArticleVideo`（视频帖不调相册接口）

```mermaid
flowchart TD
  U[用户选择视频] --> FE[前端后台上传 + 进度条]
  FE --> NG[Nginx /file/ 反代]
  NG --> J[Java FileService]
  J --> S{体积 ≤ 200MB?}
  S -->|是| OSS1[直传 OSS]
  S -->|否| FF[FFmpeg 服务]
  FF --> M{H.264 + AAC?}
  M -->|是| R[remux -c copy]
  M -->|否| E[ultrafast 重编码]
  R --> OSS2[上传 OSS]
  E --> OSS2
  OSS1 --> URL[返回 videoUrl]
  OSS2 --> URL
  URL --> BIND[setArticleVideo 绑定帖子]
```

### 3) 行为验证码 + 一次性票据

短信 / 邮件有成本，注册与找回密码不能裸奔。滑块验证通过后签发 **Redis 短 TTL 票据**；后续发码 / 注册须携带票据，校验成功即 **删除**（一次性）。

```mermaid
sequenceDiagram
  participant FE as 前端
  participant BE as Java 后端
  participant Redis as Redis

  FE->>BE: 1) 提交滑块验证结果
  BE->>Redis: SET ticket(UUID, purpose, TTL≈2min)
  BE-->>FE: 返回 ticket

  FE->>BE: 2) 注册 / 发码（带 ticket）
  BE->>Redis: GET + DEL ticket（用一次即失效）
  alt 票据有效且未用过
    BE-->>FE: 继续业务（发码 / 注册）
  else 无效 / 已用 / 过期
    BE-->>FE: 拒绝
  end
```

### 4) 积分抽奖防超卖

抽奖最怕 **积分扣成负数** 和 **限量奖品发超**。关键扣减在事务内用 **带条件的 UPDATE** 保证原子性；并发抽奖对用户行 `SELECT FOR UPDATE` 串行化。

- 扣积分：`WHERE points >= cost`，影响行数为 0 则失败
- 扣库存：`WHERE stock > 0`，失败则换奖品重抽（有上限）
- **硬保底**：连续 N 次未中头奖 → 下次走保底池
- **软保底**：十连最后一抽兜底，保证至少一个稀有

```mermaid
flowchart TD
  S[开始抽奖] --> L[事务: SELECT user FOR UPDATE]
  L --> P[扣积分 UPDATE points>=cost]
  P -->|0 行| F1[余额不足]
  P -->|成功| D{单抽 / 十连}
  D --> G{触发硬保底?}
  G -->|是| POOL[保底奖池]
  G -->|否| W[按权重抽奖品]
  POOL --> K
  W --> K[扣库存 UPDATE stock>0]
  K -->|失败| R[换奖品重试]
  K -->|成功| REC[写中奖记录 / 发奖]
  R --> W
  REC --> E[提交事务]
  D --> SB[十连: 末抽软保底检查]
  SB --> E
```

### 5) 私信跨实例推送

多实例时，接收方的 WebSocket 连接落在哪台机器不确定。写库后向 Redis **PubSub 广播**推送事件；**只有持有目标连接的那台实例**真正下发，其余实例忽略。

```mermaid
flowchart LR
  A[用户 A 发私信] --> J1[Java 实例 1]
  B[用户 B 的 WS] --> J2[Java 实例 2]

  J1 --> DB[(MySQL 持久化消息)]
  J1 --> Pub[Redis PubSub 广播]
  Pub --> J1
  Pub --> J2
  J2 -->|本机有 B 的连接| WS[WebSocket 推送给 B]
  J1 -->|无 B 连接| Skip[忽略]
```

### 6) 热帖榜（Redis ZSet）

热帖榜用 Redis **ZSet**：member 为帖子 ID，score 为热度。点赞 / 浏览 / 回复 / 收藏等行为触发 `ZINCRBY`；删帖、驳回、下线时 `ZREM`。定时任务可从 DB 全量重算做兜底。

```mermaid
flowchart TD
  E[用户行为: 浏览 / 点赞 / 回复 / 收藏] --> Z[Redis ZSet hot_rank]
  Z --> I[ZINCRBY 加分]
  D[删帖 / 审核驳回 / 下线] --> R[ZREM 移除]
  T[定时兜底任务] --> Recalc[从 MySQL 重算热度]
  Recalc --> Z
  Z --> API[首页 / 热榜接口 ZREVRANGE]
```

### 7) 智能搜索（快搜 + 语义增强）

搜索分层：**先数据库** `LIKE` 快搜（低成本、稳定）；结果过少或相关性不足时，再调 **ai-server** 做语义排序 / RAG 召回，把更相关的帖子排到前面。

```mermaid
flowchart TD
  Q[用户输入关键词] --> DB[MySQL 标题 LIKE 快搜]
  DB --> C{结果数量 / 质量够用?}
  C -->|是| R1[直接返回列表]
  C -->|否| Cand[拉取候选: 标题 + 摘要 + 标签]
  Cand --> AI[ai-server 语义排序 / RAG]
  AI --> R2[返回重排后的结果]
```

---

## 本地开发

### 中间件（Docker）

```powershell
cd nginx
docker compose -f docker-compose.dev.yaml up -d --build
```

| 服务 | 宿主机端口 |
|------|------------|
| MySQL | 33061 |
| Redis | 63790 |
| RabbitMQ AMQP | **56690**（Windows Hyper-V 保留 56720，勿改回） |
| RabbitMQ 管理台 | 25672 |
| PostgreSQL | 54320 |
| FFmpeg | 8099 |

### 应用（宿主机）

| 模块 | 目录 | 默认地址 |
|------|------|----------|
| 用户端 | `forum-vue/front` | `npm run dev` → 5173 |
| 管理端 | `forum-vue-admin/admin` | 各自 dev 端口 |
| 后端 | `forum-demo` | `http://localhost:10086` |
| AI | `ai-server` | `http://localhost:5000` |

**IDEA 打开后端**：File → Open → 选 **`forum-demo`** 文件夹（或 `pom.xml`），Maven Reload，开启 Lombok Annotation Processors。

**本地密钥（勿提交仓库）**

```powershell
copy scripts\dev-secrets.ps1.example scripts\dev-secrets.ps1
# 编辑 dev-secrets.ps1 填入 Key
. .\scripts\load-dev-env.ps1
```

**数据库**：结构变更后整库重跑 `forum-demo/src/main/resources/sql/create.sql`（会清空数据）。PostgreSQL 会话表：`postgres_ai_session.sql`；开发清空 checkpoint：`postgres_reset_dev.sql`。

**RAG 向量**：`model_embedding_rag` 默认 `qwen3-vl-embedding`；`qwen3-vl-rerank` 仅用于重排，不能当 embedding。

---

## 生产部署

栈：**本机 `make-package.ps1` → 上传 `nginx/package/` → 服务器 `up.sh` / `start.sh`**。  
**勿**只上传部分 `assets/*.js`；**勿**单独 `docker compose up --build`（不会 `docker load` 离线镜像，易 403 白屏）。

### 本机打包

```powershell
cd nginx
# 确认 nginx\.env 为生产配置（会复制进 package\.env，该文件已 gitignore）
.\scripts\make-package.ps1
```

产物：`nginx\package\`（`dist/`、`images/*.tar`、`conf.d/`、`ssl/` 模板、`.env.example`、`start.sh`、`up.sh`、`collect-logs.sh`）。

仅更新前端（已有镜像）：

```powershell
.\scripts\build-all.ps1 -SkipDocker -SkipBackend
.\scripts\export-images.ps1
```

### 上传

整目录传到服务器 `~/package/`。替换前可备份 `.env` 与 `ssl/`。

### 服务器启动

```bash
cd ~/package
sed -i 's/\r$//' .env start.sh up.sh verify-frontend-dist.sh reset-db.sh collect-logs.sh
chmod +x start.sh up.sh verify-frontend-dist.sh reset-db.sh collect-logs.sh

# 日常更新（推荐）
bash up.sh

# 首次部署 / 需要初始化库
cp .env.example .env && nano .env
# HTTPS：证书放入 ssl/（*.pem *.key 已 gitignore）
bash start.sh
```

`up.sh`：`chmod dist` → 校验前端 → `docker load` 三个 tar → `compose up --force-recreate`。  
`start.sh`：含首次 `create.sql` 初始化（可用 `SKIP_DB_INIT=1` 跳过）。

**排错**：`bash collect-logs.sh` 生成 `logs-collect-*.txt`。

### 数据与 Navicat

| 操作 | 数据卷 |
|------|--------|
| `up.sh` / `up --force-recreate` | 保留 |
| `docker compose down` | 保留 |
| `docker compose down -v` | **删除** |

SSH 隧道后连接（须加载 `docker-compose.yaml` + `docker-compose.prod.yml`）：

| 服务 | 127.0.0.1 端口 |
|------|----------------|
| MySQL | 33061 |
| Redis | 63790 |
| PostgreSQL | 54320 |
| RabbitMQ 管理台 | 15672 |

### 验证

```bash
docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
curl -s http://127.0.0.1/healthz
./verify-frontend-dist.sh .
```

---

## 配置说明

以 `nginx/.env`（及服务器 `package/.env`）为准，**切勿提交真实 `.env`**。

| 类别 | 变量 |
|------|------|
| 安全 | `JWT_SECRET`、`PII_CRYPTO_SECRET`、`FORUM_MASCOT_INTERNAL_KEY`、`FORUM_AI_INTERNAL_KEY` |
| 数据 | `MYSQL_*`、`REDIS_PASSWORD`、`RABBITMQ_*`、`POSTGRES_*` |
| AI | `DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`（须来自 [platform.deepseek.com](https://platform.deepseek.com)，**勿与 DashScope 混用**）、`HUANAPI_*`、`TAVILY_API_KEY`、`BAIDU_MAP_API_KEY` |
| OSS | `ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET`、`OSS_BUCKET_NAME`、`OSS_URL_PREFIX`、`OSS_ROOT_PREFIX` |
| OSS → ai-server | 同上 OSS 变量须注入 **forum-ai-server**（视频审核签名读私有桶） |
| 邮件 | `MAIL_USERNAME`、`MAIL_PASSWORD` |

看板娘 MCP（ai-server 内置）：`tavily_search`、`get_current_datetime`、`map_*`（需对应 API Key）。

---

## 常见问题

### 1) 502 / WebSocket 失败

后端启动约 20～90s，刷新即可；compose 已配置 backend healthy 后再起 Nginx。

### 2) 前端 403 / 白屏

- 未执行 `bash up.sh`：缺 `chmod dist` 或 `docker load`
- `index.html` 与 `assets/*.js` 版本不一致：整包重传 `dist/`，跑 `./verify-frontend-dist.sh .`

### 3) 审核一直「审核异常」

- 文本超时：已加长 `text_audit_timeout`；长文会截断
- **视频帖**：日志若 `Failed to download multimodal content` → OSS 私有桶；确认 ai-server 有 OSS 环境变量并重新部署
- 队列 `NOT_FOUND q-audit-article`：启动竞态，Java 起来后自动恢复

### 4) 视频帖保存报「不支持相册图」

v1.2 已修复：视频模式只调 `setArticleVideo`，勿对视频帖调 `replaceArticleImages`。需部署最新前后端。

### 5) Navicat 连不上

生产端口绑 `127.0.0.1`，须 SSH 隧道 + `docker-compose.prod.yml`。

### 6) 管理端「需要管理员权限」

库中 `user.is_admin = 1` 并绑定 `role_admin`。

---

## 仓库结构

```text
luntan/
  forum-demo/              # Java 后端
  ai-server/               # Python AI（审核 / 看板娘 / RAG）
  forum-vue/               # 用户端前端
  forum-vue-admin/         # 管理端前端
  nginx/                   # Compose、Nginx、FFmpeg、打包脚本
    scripts/
      make-package.ps1     # 一键打包
      build-all.ps1        # 构建前后端与镜像
      export-images.ps1    # 组装 package/
      verify-package.ps1   # 打包自检
      server-up.sh         # → package/up.sh
    ffmpeg/                # 视频压缩 / 审核抽帧
  scripts/
    dev-secrets.ps1.example
    load-dev-env.ps1       # 本地加载密钥到当前 PowerShell 会话
  luntan.code-workspace    # 多根工作区（可选）
```

**Git 忽略要点**：`.env`、`nginx/package/`、`nginx/dist/`、`target/`、`node_modules/`、`ssl/*.pem`、`scripts/dev-secrets.ps1`、本地 `ai-server/config.local.yaml` 等，见根目录 `.gitignore`。
