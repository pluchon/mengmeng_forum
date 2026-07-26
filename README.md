# 萌部落社区 / Moe Community

![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2+-6DB33F?style=flat&logo=springboot&logoColor=white)
![Vue.js](https://img.shields.io/badge/Vue.js-3.5+-4FC08D?style=flat&logo=vue.js&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.11+-3776AB?style=flat&logo=python&logoColor=white)

[![License MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Ask DeepWiki](https://img.shields.io/badge/Ask-DeepWiki-blue)](https://deepwiki.com)

---

> 本项目出于个人兴趣爱好搭建；线上地址仅供学习交流。

## 主要功能演示

![image-20260722165915197](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722165915752.png)

![image-20260722165945205](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722165945609.png)

![image-20260722170011789](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722170012141.png)

![image-20260722170033274](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722170033456.png)

![image-20260722170046716](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722170047039.png)

![image-20260722170102428](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722170102767.png)

![image-20260722170114759](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722170114978.png)

![image-20260722170126920](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722170127123.png)

![image-20260722170135408](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260722170135657.png)

***

线上地址：

- 用户端：`https://www.nuonuoya.cn`

> 前后端分离技术社区：发帖（图文 / 视频）、评论楼中楼、视频弹幕、私信、群聊（带语音）、匿名漂流瓶、收藏夹、抽奖、搜索、帖子标签；发布前 AI 审核；多实例部署时私信支持跨实例实时推送；看板娘支持 RAG 推荐帖子、MCP 联网与出行工具；游戏中心已接入 WebSocket 五子棋、井字棋与俄罗斯方块（单人 + PK）与统一排位系统。

---

## 当前社区能力

```mermaid
flowchart TD
  U[用户]

  subgraph Community[社区互动]
    direction LR
    C[内容<br/>发帖、评论、问答、弹幕、推荐]
    S[社交<br/>私信、群聊、漂流瓶、收藏]
    C --> S
  end

  subgraph Experience[成长与体验]
    direction LR
    G[成长<br/>签到、积分、抽奖、会员]
    A[智能<br/>审核、写作、搜索、看板娘]
    P[游戏<br/>五子棋、井字棋、俄罗斯方块]
    G --> A --> P
  end

  U --> C
  S --> G
```
| 模块 | 已提供能力 | 关键体验 |
| --- | --- | --- |
| 内容 | 图文/视频发帖、标签、评论、楼中楼、问答采纳、视频弹幕 | 内容审核后发布，支持互动与二次创作 |
| 发现 | 热帖榜、搜索、个性化推荐 | 热度按日统计，推荐支持“不感兴趣”反馈 |
| 社交 | 关注、私信、群聊、群语音、收藏夹、表情包 | 支持图片、表情、回复引用与成员管理 |
| 匿名互动 | 漂流瓶投放、捞取、回应、举报 | 配额控制与匿名身份隔离 |
| 成长 | 签到、积分流水、积分抽奖、成长挑战、会员 | 成长记录、奖池保底与会员额度统一展示 |
| AI | 发帖审核、写作辅助、生图、语义搜索、看板娘 | Java 保持业务权威，Python 负责模型与 RAG 服务 |
| 游戏 | 五子棋、井字棋、俄罗斯方块 | WebSocket 实时对局、战绩结算与统一排位 |

---


## 项目概览

| 目录 | 说明 |
|------|------|
| `backend` | Java 后端（Spring Boot）：业务 API、鉴权、MQ、WebSocket、审核状态、五子棋 / 井字棋 / 俄罗斯方块 |
| `ai-server` | Python：AI 审核 / 写作 / 看板娘 / 语义搜索 / RAG |
| `forum-vue` | 用户端（Vue 3 + Vite 6） |
| `nginx` | Nginx 配置、Docker Compose、打包脚本、`ffmpeg` 视频服务 |

---

## 整体架构

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'12px'}}}%%
flowchart TD
  U[用户端 Web] --> N[Nginx]
  N --> J[Java 后端]

  subgraph Core[核心服务]
    direction LR
    J --- F[FFmpeg]
    J --- G[Game WS]
  end

  subgraph Infrastructure[数据与异步]
    direction LR
    M[(MySQL)]
    R[(Redis)]
    Q[(RabbitMQ)]
  end

  subgraph AiService[AI 服务]
    direction LR
    P[Python AI] --> PG[(PostgreSQL)]
    P --> OSS[(OSS)]
  end

  J --> M
  J --> R
  J --> Q
  J --> P
```

- 用户端 → **Nginx**（静态 `dist/` + 反代 API）
- Java → MySQL / Redis / RabbitMQ / FFmpeg / ai-server
- 五子棋 / 井字棋 / 俄罗斯方块 PK 实时链路 → Java WebSocket（大厅在线、游戏在线、房间对局三类连接）
- 局部责任链 → Java 后端 Guard Chain，只拦截前置准入规则，不接管事务核心流程
- ai-server → PostgreSQL（LangGraph checkpoint）、DashScope、OSS 签名读私有媒体

## 现有 AI 模块结构

当前 AI 模块是 **Java 业务权威 + Python AI 服务中心** 的混合架构：Java 负责鉴权、配额、业务状态、消息落库与最终发布；Python 负责模型调用、LangGraph 审核、看板娘对话、RAG 检索和 MCP 工具调用。

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'curve':'basis', 'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart TD
  FE[前端] --> BE[Java后端]
  BE --> AUTH[鉴权/配额]
  AUTH --> MASCOT_J[MascotService]
  AUTH --> AIHUB_J[AiHubService]
  AUTH --> AUDIT_J[AuditService]
  
  MASCOT_J --> MASCOT_API[/mascot/chat/]
  AIHUB_J --> AI_API[/ai/polish/]
  AIHUB_J --> RAG_API[/rag/search/]
  
  MASCOT_API --> MASCOT_G[看板娘Graph]
  AI_API --> HUB_SVC[AI写作/生图]
  RAG_API --> RAG[RAG检索]
  
  AUDIT_J --> MQ_TASK[(MQ审核队列)]
  MQ_TASK --> WORKER[audit_worker]
  WORKER --> AUDIT_G[审核Graph]
  AUDIT_G --> MQ_RESULT[(MQ结果队列)]
  MQ_RESULT --> AUDIT_J
  
  BE --> MYSQL[(MySQL)]
  RAG --> REDIS[(Redis)]
  AUDIT_G --> PG[(PostgreSQL)]
  MASCOT_G --> MODEL[Qwen]
  HUB_SVC --> OSS[(OSS)]
```

现状要点：

- `article_audit.py` 是真实 LangGraph 固定审核流程，并绑定 PostgreSQL checkpoint。
- `mascot_graph.py` 先以 Supervisor 读取最近会话并决定聊天或委派生图；聊天分支已接入站内 RAG、Tavily、地图、天气等 MCP 工具，生图仍由 Java 负责计费与执行。
- RAG 当前主要使用 Redis 保存文章 / 用户向量索引，展示前仍回到 Java / MySQL 过滤公开状态。
- 看板娘会话消息持久化在 MySQL，Python 只接收 Java 传入的历史上下文。
- 异步审核采用 MQ 双向回执：Java 投递审核任务，Python 回投审核结果，最终发布状态由 Java 决定。

---

## 功能一览

### 用户端

**五子棋能力**

- 快速匹配：真人优先，长时间无人时自动创建 AI 房间
- AI 对手：Java 调 Python AI Hub；低水平玩家使用 `qwen3.6-flash`，高水平玩家使用 `qwen3.7-max`；Qwen 不可用或返回非法坐标时才走本地规则兜底
- 实时对局：服务端维护权威棋盘，校验回合、坐标、棋色和观战身份；落子结果通过房间 WebSocket 主动推送
- 计时规则：支持 10 分钟局时、60 秒步时，任一玩家超时直接结算
- 观战与聊天：观众只读棋局，不能落子 / 认输；房间内支持文本和已购表情包，终局后禁止继续发送消息或表情
- 棋谱与回放：每手落子写入 MySQL，前端支持历史对局回放和自动播放
- 积分结算：胜负同步论坛积分流水，战绩统计、胜率、天梯榜与论坛用户体系共享
- 多实例准备：在线状态、匹配队列、房间快照与房间事件可走 Redis；对局结束事件可投递 RabbitMQ 做补偿与异步处理

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11, 'noteFontSize': 10}}}%%
sequenceDiagram
  participant FE as 前端
  participant Game as 游戏WS
  participant Room as 房间WS
  participant J as Java
  participant P as AI
  participant DB as DB

  FE->>Game: 开始匹配
  alt 匹配真人
    J->>Room: 创建真人房间
  else AI房间
    J->>P: Qwen落子
  end
  Room->>J: 玩家落子
  J->>DB: 写战绩
  J-->>Room: 推送结果
```

### 1b) 游戏中心 / 井字棋（WebSocket 轻量对战）

井字棋作为游戏中心第二款对战游戏，**复用** `game_definition` / `game_user_profile` / `game_match_record` / `game_room_move` 等通用表，以 `game_code = jinzi` 区分数据。实时链路同样拆成三层 WebSocket：大厅连接展示游戏中心在线；游戏连接负责井字棋匹配页在线人数与匹配队列；房间连接负责 3×3 落子、计时、聊天和终局同步。HTTP 负责个人资料、历史战绩、天梯榜与棋谱回放查询。

**井字棋能力**

- 快速匹配：按论坛积分分桶（青铜 / 白银 / 黄金 / 大师），同桶内真人优先配对
- 入场门槛：开始匹配前须至少有 **3** 论坛积分；真人胜局 ±3 分，AI 胜局 ±1 分，**平局不结算积分**
- AI 对手：队列等待约 **15 秒**无人匹配时自动创建 AI 房间；积分低于 1600 走 `qwen3.6-flash`，达到 1600 及以上走 `qwen3.7-max`；Qwen 不可用或返回非法坐标时走本地 Minimax 兜底
- 实时对局：服务端维护权威 3×3 棋盘，校验回合、坐标、棋色；三连成线推送 `winningLine`，平局走 `END_DRAW`
- 计时规则：支持 **2 分钟**局时、**20 秒**步时；断线保留 **30 秒**重连窗口，超时判负
- 房间聊天：对局双方支持文本和已购表情包；**不开放观战席**，终局后禁止继续落子 / 认输 / 聊天
- 棋谱与回放：每手落子写入 `game_room_move`，匹配页支持历史对局回放
- 积分结算：胜负同步论坛积分流水；战绩、胜率、天梯榜与论坛用户体系共享
- 多实例准备：在线状态、匹配队列、房间快照与结算事件复用 Redis / RabbitMQ 扩展点

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11}}}%%
sequenceDiagram
  participant FE as 前端
  participant Game as 游戏WS
  participant Room as 房间WS
  participant J as Java
  participant P as AI
  participant DB as DB

  FE->>Game: 开始匹配
  alt 真人
    J->>Room: 创建房间
  else AI(15s无人)
    J->>P: Qwen落子
  end
  Room->>J: 玩家落子
  J->>DB: 写战绩
  J-->>Room: 推送结果
```

### 1c) 游戏中心 / 俄罗斯方块（单人 + PK）

俄罗斯方块分成两套玩法。单人模式采用 `canvas` 主棋盘，前端本地运行游戏循环，后端只负责资料、结算、排行榜与回放；双人 PK 模式则回到后端权威房间状态，双方输入通过 WebSocket 上送，服务端推进棋盘、处理垃圾行并广播最新状态。这样既保留了单人模式的流畅度，也保证了 PK 模式的公平性。

**俄罗斯方块能力**

- 单人模式：10x20 经典玩法，支持移动、旋转、软降 / 硬降、消行、成绩结算、历史记录、排行榜与回放
- PK 模式：双人匹配、双棋盘实时同步、基础垃圾行攻击、终局胜负结算
- 观战：第三名及以上用户可以进入 PK 房间实时观看双方棋盘，但不能发送输入控制
- 数据分层：单人模式不硬套棋类房间语义；PK 模式保留游戏级 / 房间级连接与观战列表
- 积分联动：单人模式按成绩档位发放论坛积分，PK 模式按胜负发放论坛积分

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11}}}%%
sequenceDiagram
  participant FE as 前端
  participant Game as 游戏WS
  participant J as Java
  participant DB as DB

  alt 单人
    FE->>FE: 本地循环
    FE->>J: 提交结算
    J->>DB: 写记录
  else PK
    FE->>Game: 匹配
    Game->>J: 创建房间
    FE->>Game: 输入操作
    J->>DB: 写结算
    J-->>Game: 广播棋盘
  end
```

### 1d) 视频帖弹幕

视频帖在播放器层叠加弹幕引擎：前端按视频时间轴渲染滚动 / 顶部 / 底部弹幕；用户可在播放条旁设置颜色、字号、模式与显示区域，发送时写入 `article_video_danmaku` 表。弹幕层高度受控，底部不低于播放器控制栏。

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11}}}%%
sequenceDiagram
  participant U as 用户
  participant FE as 播放器
  participant J as Java
  participant DB as DB

  U->>FE: 发送弹幕
  FE->>J: POST弹幕
  J->>DB: 持久化
  J-->>FE: 返回条目
  FE->>FE: 渲染
```

### 2) 发帖审核（异步 + 幂等）

1. 用户提交 → Java 状态改为「审核中」，生成 `taskId`，投递 `q-audit-article`
2. Python worker 消费：`validate_text` → `validate_images` → **`validate_video`**（有视频时）→ `summarize`
3. 结果回 MQ → Java 条件更新状态并通知用户

**视频审核要点**

- DashScope 需能访问视频 URL；**私有 OSS** 由 ai-server 用 `ALIYUN_*` / `OSS_*` 生成**签名 URL**
- 视频 >100MB 或 DashScope 拉取失败时：FFmpeg **抽帧** → 走图片审核兜底
- ai-server 容器须配置与 Java 相同的 OSS 环境变量（见 `docker-compose.yaml`）

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11}}}%%
sequenceDiagram
  participant FE as 前端
  participant J as Java
  participant MQ as MQ
  participant P as AI
  participant DS as DashScope

  FE->>J: 提交审核
  J->>MQ: 任务
  MQ->>P: 消费
  P->>DS: 视频审核
  P->>MQ: 结果
  MQ->>J: 更新状态
```

### 3) 视频上传

用户选择视频后，前端可**后台上传**并展示进度；后端按体积分流，大文件经 FFmpeg 再写入 OSS。Nginx `/file/` 代理超时 **3600s**，避免长视频压缩卡住。

- **≤200MB**：Java 直传 OSS
- **>200MB**：Java → FFmpeg（H.264+AAC 则 **remux** 不重编码，否则 **ultrafast** 重编码）→ 回传字节流 → OSS
- 绑定帖子：保存草稿 / 提交时调用 `setArticleVideo`（视频帖不调相册接口）

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TD
  U[选择视频] --> FE[上传]
  FE --> J[Java]
  J --> S{≤200MB?}
  S -->|是| OSS1[直传OSS]
  S -->|否| FF[FFmpeg]
  FF --> OSS2[上传OSS]
  OSS1 --> URL[返回URL]
  OSS2 --> URL
```

### 4) 行为验证码 + 一次性票据

短信 / 邮件有成本，注册与找回密码不能裸奔。滑块验证通过后签发 **Redis 短 TTL 票据**；后续发码 / 注册须携带票据，校验成功即 **删除**（一次性）。

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11}}}%%
sequenceDiagram
  participant FE as 前端
  participant BE as Java
  participant R as Redis

  FE->>BE: 滑块验证
  BE->>R: SET ticket
  BE-->>FE: 返回ticket
  FE->>BE: 注册(带ticket)
  BE->>R: GET+DEL
  alt 有效
    BE-->>FE: 继续
  else 无效
    BE-->>FE: 拒绝
  end
```

### 5) 积分抽奖防超卖

抽奖最怕 **积分扣成负数** 和 **限量奖品发超**。关键扣减在事务内用 **带条件的 UPDATE** 保证原子性；并发抽奖对用户行 `SELECT FOR UPDATE` 串行化。

- 扣积分：`WHERE points >= cost`，影响行数为 0 则失败
- 扣库存：`WHERE stock > 0`，失败则换奖品重抽（有上限）
- **硬保底**：连续 N 次未中头奖 → 下次走保底池
- **软保底**：十连最后一抽兜底，保证至少一个稀有

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 20, 'rankSpacing': 30}}}%%
flowchart TD
  S[开始] --> L[FOR UPDATE]
  L --> P[扣积分]
  P -->|失败| F1[余额不足]
  P -->|成功| G{硬保底?}
  G -->|是| POOL[保底池]
  G -->|否| W[权重抽]
  POOL --> K[扣库存]
  W --> K
  K -->|失败| R[重试]
  K -->|成功| REC[发奖]
  R --> W
  REC --> E[提交]
```

### 6) 私信跨实例推送

多实例时，接收方的 WebSocket 连接落在哪台机器不确定。写库后向 Redis **PubSub 广播**推送事件；**只有持有目标连接的那台实例**真正下发，其余实例忽略。

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25}}}%%
flowchart TD
  A[用户A] --> J1[实例1]
  B[用户B的WS] --> J2[实例2]
  J1 --> DB[(MySQL)]
  J1 --> Pub[PubSub]
  Pub --> J2
  J2 -->|有连接| WS[推送]
  Pub --> J1
  J1 -->|无连接| Skip[忽略]
```

### 7) 热帖榜（Redis ZSet 蓝绿切换）

热帖榜用 Redis **ZSet**：member 为帖子 ID，score 为热度。点赞 / 浏览 / 回复 / 收藏等行为通过 `ArticleHotRankingService.incrementScore` 更新；删帖、驳回、下线时在 **事务提交后** `ZREM` 并清理搜索/RAG。定时任务在**非活跃槽**重建完整榜单，再原子切换 `hot:articles:active` 指针，重算期间读侧始终命中旧槽，避免空榜。

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 20, 'rankSpacing': 30}}}%%
flowchart TD
  E[用户行为] --> S[HotRankingService]
  S --> ZA[ZSet槽A/B]
  D[删帖] --> AC[afterCommit]
  AC --> R[ZREM]
  T[定时03:00] --> INAC[写非活跃槽]
  INAC --> SW[切换指针]
  SW --> ZA
  ZA --> API[ZREVRANGE]
  EMPTY{槽空?} -->|是| DB[MySQL兜底]
  EMPTY -->|否| API
```

### 8) 智能搜索（快搜 + 语义增强）

搜索分层：**先数据库** `LIKE` 快搜（低成本、稳定）；结果过少或相关性不足时，再调 **ai-server** 做语义排序 / RAG 召回，把更相关的帖子排到前面。

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TD
  Q[输入关键词] --> DB[MySQL LIKE]
  DB --> C{结果够?}
  C -->|是| R1[返回]
  C -->|否| Cand[拉取候选]
  Cand --> AI[语义排序]
  AI --> R2[重排返回]
```

### 9) 局部责任链（Guard Chain）

后端只在**前置准入校验**上使用局部责任链，避免业务入口继续堆叠大量重复 `if`。责任链只回答“能不能继续”，失败时返回统一错误；真正的落库、扣积分、库存扣减、MQ 投递、WebSocket 广播和状态流转仍由原 Service 主流程负责。

已接入的 Guard Chain：

- 五子棋房间动作：`MOVE / CHAT / SURRENDER`，校验房间存在、进行中、玩家身份、回合、坐标、空位和聊天内容
- 五子棋匹配入口：校验用户存在、积分足够、未在对局中、未重复入队
- 井字棋房间动作：在 `JinziRoomService` 内校验房间存在、进行中、玩家身份、回合、坐标、空位和聊天内容（不开放观战）
- 井字棋匹配入口：校验用户存在、积分 ≥ 3、未在对局中、未重复入队
- 私信发送：校验文本 / 图片 / GIF / 回复消息的内容、发送者状态、接收者、不能给自己发、媒体 URL 来源
- 发帖提交审核：校验作者、禁言、帖子可见、状态允许、审核重试次数
- 抽奖准入：校验用户 ID、抽数、活动可用、用户可用

明确不放进责任链的核心流程：

- 五子棋终局结算：对局记录、胜负统计、积分流水、玩家状态、结算事件和广播
- 井字棋终局结算：对局记录、胜负统计、积分流水（平局 scoreDelta=0）、玩家状态、结算事件和广播
- 发帖审核结果应用：`PENDING_AUDIT + taskId` 的 DB CAS、Redis dedup、通过 / 拒绝 / 异常状态落库
- 抽奖执行：积分扣减、库存扣减、中奖记录、软保底 / 硬保底计数
- 文件上传：当前私有方法校验已足够集中，拆链收益不明显

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TD
  A[业务请求] --> C[构造Context]
  C --> G[Guard Chain]
  G -->|失败| F[返回错误]
  G -->|通过| S[Service主流程]
  S --> DB[(MySQL/Redis)]
  S --> MQ[RabbitMQ]
  S --> WS[WebSocket]
```

### 10) 并发写一致性与幂等（v1.7）

核心原则：**MySQL 是唯一事实来源**；Redis、热帖榜、搜索/RAG、MQ 推送均为派生数据，且尽量在 **事务提交后** 更新。

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TD
  REQ[客户端] --> TX[事务写DB]
  TX -->|成功| HOOK[afterCommit]
  TX -->|回滚| NOP[不更新派生数据]
  HOOK --> R[Redis]
  HOOK --> MQ[MQ/Outbox]
  HOOK --> IDX[搜索/RAG]
```

**典型幂等模式**

| 场景 | 手段 |
|------|------|
| 点赞 / 关注 | 唯一索引 + `DuplicateKeyException` |
| 积分 / VIP / 抽奖 | `idempotency_key` 或 `requestId` + `FOR UPDATE` |
| AI 计费 | `forum_ai_call_record` 预记录 + `clientRequestId` |
| MQ 消费 | `MqEventDedupHelper` Redis `SET NX` |
| 游戏匹配建房 | `GameMatchRoomHelper` 用户对 Redis 键 |
| 验证码 / 票据 | `RedisAtomicValueConsumer` Lua 原子删 |

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11}}}%%
sequenceDiagram
  participant FE as 前端
  participant J as Java
  participant DB as DB
  participant R as Redis
  participant Q as MQ

  FE->>J: 写操作
  J->>DB: 事务INSERT/UPDATE
  alt 成功
    J->>J: afterCommit
    J-->>DB: COMMIT
    J->>R: 更新缓存
    J->>Q: MQ通知
  else 回滚
    J-->>DB: ROLLBACK
  end
```

**SQL 迁移文件**（`backend/src/main/resources/sql/`，打包时复制到 `package/sql/`）

| 文件 | 使用场景 |
|------|----------|
| `create.sql` | 全新 MySQL 删库重建（**线上增量禁用**） |
| `incremental_concurrency.sql` | 已有 `forum_db` 增量（幂等键、AI 预记录、Outbox 等） |
| `postgres_ai_session.sql` | Postgres 会话表全量（可重复执行） |
| `incremental_postgres_ai_session.sql` | 已有 Postgres 库补字段/触发器 |

---

## 本地开发

### 启动顺序

```powershell
# 1. 中间件
cd nginx
docker compose -f docker-compose.dev.yaml up -d --build

# 2. 用户端
cd ..\forum-vue\front
npm install
npm run dev

# 3. 后端
cd ..\..\backend
mvn spring-boot:run

# 4. Python AI
cd ..\ai-server
python main.py
```

| 模块 | 默认地址 / 端口 |
|------|----------------|
| 用户端 | `http://localhost:5173` |
| 后端 | `http://localhost:10086` |
| AI 服务 | `http://localhost:5000` |
| MySQL | `localhost:33306` |
| Redis | `localhost:63790` |
| RabbitMQ AMQP | `localhost:56690` |
| RabbitMQ 管理台 | `localhost:25672` |
| PostgreSQL | `localhost:54320` |
| FFmpeg | `localhost:8099` |

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'12px'}, 'flowchart': {'nodeSpacing': 30, 'rankSpacing': 40}}}%%
flowchart TD
  Dev[开发者] --> FE[Vue:5173]
  FE --> BE[Java:10086]

  subgraph Middleware[本地中间件]
    direction LR
    M[(MySQL)]
    R[(Redis)]
    Q[(RabbitMQ)]
  end

  subgraph LocalServices[本地服务]
    direction LR
    AI[AI:5000] --> PG[(PostgreSQL)]
    FF[FFmpeg]
  end

  BE --> M
  BE --> R
  BE --> Q
  BE --> AI
  BE --> FF
```

**IDEA 打开后端**：File → Open → 选 `backend` 文件夹或 `pom.xml`，Maven Reload，开启 Lombok Annotation Processors。

**五子棋本地调试**

- 游戏入口：用户端登录后访问 `/games`，再进入 `/games/gobang`
- WebSocket 入口：`/ws/game-center/lobby`、`/ws/games/gobang`、`/ws/games/gobang/rooms/{roomId}`
- 对局结果依赖 MySQL；在线、匹配与多实例房间事件依赖 Redis；异步结算事件依赖 RabbitMQ
- AI 对手依赖 `ai-server` 与 `DASHSCOPE_API_KEY`；Python 服务不可用时 Java 会使用本地规则兜底，但界面会展示兜底标识

**井字棋本地调试**

- 游戏入口：用户端登录后访问 `/games`，再进入 `/games/jinzi`
- WebSocket 入口：`/ws/game-center/lobby`、`/ws/games/jinzi`、`/ws/games/jinzi/rooms/{roomId}`
- 匹配门槛：论坛积分至少 3 分；真人胜局 ±3 分，AI 胜局 ±1 分，平局不结算
- 计时：2 分钟局时、20 秒步时；断线 30 秒内可重连，否则判负
- AI 对手约 15 秒无人匹配后触发；同样依赖 `ai-server` 与 `DASHSCOPE_API_KEY`，不可用时走本地 Minimax 兜底

### 本地密钥

```powershell
copy scripts\dev-secrets.ps1.example scripts\dev-secrets.ps1
. .\scripts\load-dev-env.ps1
```

真实 `.env`、`scripts/dev-secrets.ps1`、`ai-server/config.local.yaml` 不提交。

**数据库脚本**

| 场景 | 命令 / 文件 |
|------|-------------|
| 本地空库初始化 | `docker compose.dev` 启动后执行 `create.sql` |
| 已有库升级（对齐 v1.7） | `incremental_concurrency.sql`、`incremental_postgres_ai_session.sql` |
| 切勿在线上 | `reset-db.sh` / `create.sql`（会 DROP 库） |

### 快速验收

```powershell
cd backend
mvn clean test
# 并发相关（需本地 MySQL + Redis）
mvn test -Dtest=Phase01RedisAtomicConsumeTest,Phase02ConcurrencyAcceptanceTest

cd ..\forum-vue\front
npm run build
```

---

## 生产部署

生产部署遵循「**本机构建完整包，服务器只加载完整包**」的原则，避免前端 `index.html` 与 `assets` 版本不一致。

### 发布模式对比

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TB
  subgraph first[首次部署]
    F1[make-package] --> F2[上传]
    F2 --> F3[配置.env]
    F3 --> F4[start.sh]
  end

  subgraph incr[增量发布]
    I1[make-package] --> I2[上传覆盖]
    I2 --> I3[增量SQL]
    I3 --> I4[up.sh]
    I4 --> I5[healthz]
  end
```

| 步骤 | 首次部署 | 增量发布（已有数据） |
|------|----------|----------------------|
| 打包 | `.\scripts\make-package.ps1` | 同左 |
| 上传 | 整包 `nginx/package/` → `~/package/` | 同左 |
| 数据库 | `bash start.sh` 或 `reset-db.sh` + `create.sql` | **`incremental_*.sql`  only** |
| 启停 | `bash start.sh` | **`bash up.sh`** |
| 数据卷 | 新建 | **保留**（禁止 `down -v`） |

### 增量发布流程（线上常规）

```mermaid
%%{init: {'theme':'base', 'sequence': {'mirrorActors': false, 'actorFontSize': 11}}}%%
sequenceDiagram
  participant Dev as 本机
  participant Pkg as package
  participant Srv as 服务器
  participant DB as DB
  participant Docker as 容器

  Dev->>Pkg: make-package
  Dev->>Srv: 上传整包
  Srv->>DB: 增量SQL
  Srv->>Docker: up.sh
  Srv->>Srv: healthz
```

**本机**

```powershell
cd nginx
.\scripts\make-package.ps1
```

**服务器**（在 `~/package` 执行）

```bash
# 1. 增量 SQL（v1.7 并发整改；可重复执行）
docker exec -i forum-mysql mysql -uroot -p'<MYSQL_ROOT_PASSWORD>' forum_db \
  < sql/incremental_concurrency.sql

docker exec -i forum-postgres psql -U langgraph -d langgraph_db \
  < sql/incremental_postgres_ai_session.sql

# 2. 重建容器（保留数据卷）
bash up.sh

# 3. 验证
curl -s http://127.0.0.1/healthz
./verify-frontend-dist.sh .
docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
```

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TD
  Local[本地打包] --> Pack[package]
  Pack --> Upload[上传]
  Upload --> SQL[增量SQL]
  SQL --> Up[up.sh]
  Up --> Load[docker load]
  Load --> Recreate[force-recreate]
  Recreate --> Check[healthz]
```

### 日常更新（无 schema 变更时）

若本次发布**仅改代码、不改表**，可跳过 SQL 步骤，直接：

```bash
cd ~/package && bash up.sh
```

### 首次部署

```bash
cd ~/package
cp .env.example .env
nano .env
bash start.sh
# 需要重建表结构时（会清空数据）：
# bash reset-db.sh
```

### 增量发布禁止事项

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TD
  OK[up.sh] --> Safe[保留数据卷]
  BAD1[reset-db.sh] --> X1[删库]
  BAD2[down -v] --> X2[删数据卷]
  BAD3[仅传dist] --> X3[版本错位]
  BAD4[create.sql] --> X4[删库重建]
```

| 禁止操作 | 后果 |
|----------|------|
| `docker compose down -v` | 删除 MySQL / Redis 等持久化数据 |
| `bash reset-db.sh` / 线上 `create.sql` | 全库 DROP 重建 |
| 服务器 `docker compose up --build` | 不 load 离线镜像，易 403 / 镜像不一致 |
| 只替换单个 `assets/*.js` | `index.html` 引用版本错位 |

`up.sh` 保留数据卷；需要排查时执行 `bash collect-logs.sh`。更多说明见打包内 `DEPLOY.txt`。

---

## 配置说明

以 `nginx/.env` 和服务器 `package/.env` 为准，真实值只来自环境变量或本地忽略文件。

| 类别 | 变量 |
|------|------|
| 安全 | `JWT_SECRET`、`PII_CRYPTO_SECRET`、`FORUM_MASCOT_INTERNAL_KEY`、`FORUM_AI_INTERNAL_KEY` |
| 数据 | `MYSQL_*`、`REDIS_PASSWORD`、`RABBITMQ_*`、`POSTGRES_*` |
| AI | `DASHSCOPE_API_KEY`、`HUANAPI_*`、`TAVILY_API_KEY`、`BAIDU_MAP_API_KEY` |
| OSS | `ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET`、`OSS_BUCKET_NAME`、`OSS_URL_PREFIX`、`OSS_ROOT_PREFIX` |
| OSS → ai-server | 同上 OSS 变量须注入 **forum-ai-server**（视频审核签名读私有桶） |
| 邮件 | `MAIL_USERNAME`、`MAIL_PASSWORD` |

五子棋 / 井字棋 AI 使用 Qwen：低水平对手走 `qwen3.6-flash`，高水平对手走 `qwen3.7-max`；模型名称配置在 `ai-server/config.yaml` / `config.docker.yaml` 的 `dashscope.model_text_flash`、`dashscope.model_text_deep`。看板娘 MCP 内置 `tavily_search`、`get_current_datetime`、`map_*`，需要对应 API Key。

---

## 常见问题

### 1) 五子棋对局结束后还能发消息导致异常

结束后房间会清理内存状态，但前端还会停留 60 秒展示胜负和胜线。后端必须把这段窗口里的聊天、表情、落子、认输都拦截成友好提示：`当前对战已经结束，不能发送消息或表情包`。如果又出现堆栈，优先看 `GobangGuardChain` 中的房间存在、进行中和玩家动作准入校验。

### 2) 五子棋 WebSocket 已连接但棋盘不刷新

检查三层连接是否连对：大厅 `/ws/games/lobby`，游戏 `/ws/games/gobang`，房间 `/ws/games/gobang/rooms/{roomId}`。落子后不应该依赖 HTTP 刷新，前端应直接应用 `move_accepted` / `game_finished` 的 payload。

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'fontSize':'11px'}, 'flowchart': {'nodeSpacing': 25, 'rankSpacing': 35}}}%%
flowchart TD
  A[棋盘没刷新] --> B{WS有消息?}
  B -->|没有| C[检查token/Nginx]
  B -->|有| D{类型对?}
  D -->|move_accepted| E[检查applyMove]
  D -->|game_finished| F[检查胜线渲染]
  D -->|room_error| G[修权限/状态]
```

### 3) 井字棋 WebSocket 已连接但棋盘不刷新

检查游戏连接与房间连接是否连对：大厅 `/ws/game-center/lobby`，游戏 `/ws/games/jinzi`，房间 `/ws/games/jinzi/rooms/{roomId}`。落子后应直接应用 `move_accepted` / `game_finished` 的 payload，不要依赖 HTTP 轮询刷新。

### 4) AI 对手一直显示本地策略兜底

说明 Java 没拿到 Python / Qwen 的合法坐标。依次检查：`ai-server` 是否在 `5000` 端口；`FORUM_AI_INTERNAL_KEY` 是否一致；`DASHSCOPE_API_KEY` 是否有效；Python 返回坐标是否为空位。兜底不是错误，但如果 Python 已启动仍长期兜底，优先查 Python 日志里的 Qwen 调用失败原因。

### 5) 前端 403 / 白屏

通常是生产包没整体更新，导致 `index.html` 指向的 `assets` 不存在。不要只传单个 JS 文件；重新上传完整 `nginx/package`，服务器执行 `bash up.sh`，再跑 `./verify-frontend-dist.sh .`。

### 6) 审核一直显示异常

视频审核最常见是私有 OSS 导致 DashScope 无法拉取媒体，确认 ai-server 注入 OSS 变量并生成签名 URL。RabbitMQ 队列短暂 `NOT_FOUND` 多数是启动竞态，Java / Python 都起来后会恢复；持续存在时检查交换机和队列声明。

### 7) Redis / RabbitMQ 在游戏里分别负责什么

Redis 更适合短生命周期实时状态：大厅在线、游戏在线、匹配队列、房间快照、跨实例房间事件广播。RabbitMQ 更适合“必须最终处理”的事件：对局结束后异步结算、补偿任务、统计刷新和通知扩展。不要把实时棋盘权威状态只放进 MQ，棋盘最终裁决仍由 Java 房间服务完成。

### 8) 本地 RabbitMQ 端口对不上

开发 Compose 默认把 RabbitMQ AMQP 映射到 `56690`，而后端配置如果没有加载本地环境变量，可能仍按 `56720` 连接。启动后端前确认 `SPRING_RABBITMQ_PORT=56690`，或在本机显式启动与 `application.yml` 一致的 RabbitMQ 端口。

### 9) 未登录点击功能直接跳转登录页

v1.6 起首页搜索、创作中心、私信、看板娘等交互应弹出「需要登录」确认框。若仍直接跳转，检查 `loginPrompt.js` 与路由 `meta.requiresAuth` 是否被绕过。

### 10) 行为验证码失败后弹窗不关闭

验证失败（业务码 `1168`）须自动关闭弹窗，由用户下次手动触发。检查 `BehaviorCaptchaDialog.failAndClose` 与 `checkCaptcha` 的 `silentBizCodes` 配置。

### 11) 增量发布后接口 500 / 缺表

v1.7 起若后端报 `forum_ai_call_record`、`forum_outbox_message` 等表不存在，说明**未执行增量 SQL**。在 `~/package` 执行 `sql/incremental_concurrency.sql` 后 `docker compose restart backend-1`。切勿用 `reset-db.sh` 修表。

### 12) AI 重试重复扣费

看板娘 / 写作 / 生图请求应携带 **`clientRequestId`**（同一轮重试复用同一 UUID）。未传时预记录表不生效，极端重试仍可能重复计费。详见 `.codex/todo/concurrency-frontend-pending.md`。

---

## 仓库结构

```text
luntan/
  backend/                 # Java 后端：API、WebSocket、积分、弹幕、关注、五子棋 / 井字棋 / 俄罗斯方块
    src/main/resources/sql/
      create.sql           # 全量建库（仅新环境）
      incremental_concurrency.sql      # MySQL 增量（线上常规）
      incremental_postgres_ai_session.sql
  ai-server/               # Python AI（审核 / 看板娘 / RAG）
  forum-vue/               # 用户端前端
  nginx/                   # Compose、Nginx、FFmpeg、打包脚本
    scripts/
      make-package.ps1     # 一键打包
      build-all.ps1        # 构建前后端与镜像
      export-images.ps1    # 组装 package/（含 sql/）
      verify-package.ps1   # 打包自检
      server-up.sh         # → package/up.sh
    package/               # 上传服务器的完整部署包（gitignore）
    ffmpeg/                # 视频压缩 / 审核抽帧
  .codex/                  # 本地设计 / 待办文档（gitignore）
    concurrency-update/    # 并发整改阶段说明与 IMPLEMENTATION.md
    todo/                  # 前后端 backlog
  scripts/
    dev-secrets.ps1.example
    load-dev-env.ps1       # 本地加载密钥到当前 PowerShell 会话
  luntan.code-workspace    # 多根工作区（可选）
```
