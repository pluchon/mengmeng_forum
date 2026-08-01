<div align="center">
# 萌部落社区

<strong>内容交流 · 同好互动 · AI 陪伴</strong>

[![在线社区](https://img.shields.io/badge/在线社区-nuonuoya.cn-ff6b9b?style=for-the-badge)](https://www.nuonuoya.cn)
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3-42B883?style=for-the-badge&logo=vuedotjs&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)

[访问社区](https://www.nuonuoya.cn) · [项目结构](#项目结构) · [Java 业务](#java-业务模块) · [AI 模块](#ai-模块) · [本地启动](#本地启动)

</div>

萌部落是一个前后端分离的兴趣社区，把发帖、评论、收藏、聊天、成长、游戏和 AI 陪伴放在一套体系里。

## 核心功能展示

![image-20260731104016909](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104017090.png)

![image-20260731104225366](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104225465.png)

![image-20260731104041440](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104041560.png)

![image-20260731104113641](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104113737.png)

![image-20260731104131244](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104131343.png)

![image-20260731104150950](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104151074.png)

![image-20260731104209926](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104210018.png)

![image-20260731104252092](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104252186.png)

![image-20260731104304264](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104304367.png)

![image-20260731104322625](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104322719.png)

![image-20260731104338208](https://zlhimage.oss-cn-guangzhou.aliyuncs.com/20260731104338307.png)

## 项目总览

```mermaid
flowchart LR
  U["用户"] --> V["用户端"]
  V -->|页面、接口、WebSocket| N["Nginx 统一入口"]
  N -->|社区接口| J["Java 社区后端"]
  N -->|静态资源| V
  J -->|账号、帖子、互动、游戏| M[("MySQL")]
  J -->|缓存、验证码、排行榜| R[("Redis")]
  J -->|审核、通知、房间事件| Q["RabbitMQ"]
  J -->|内部鉴权请求| G["AI 服务"]
  G -->|LangGraph 状态| P[("PostgreSQL")]
  G -->|模型、检索、MCP 工具| X["外部 AI 能力"]
  Q --> W["异步消费者"]
  W --> J
```

整体思路很简单：用户端负责操作和展示；Java 后端负责社区规则、账号和数据；AI 服务负责理解、检索和生成。普通社区功能不依赖某个具体模型，因此 AI 能力以后单独调整时，不会影响发帖、聊天等基础体验。

## 项目结构

| 目录 | 负责什么 |
| --- | --- |
| `forum-vue` | 用户端页面、编辑器、社区互动与看板娘界面 |
| `backend` | 单体 Java 后端（账号、帖子、互动、推荐、游戏、权限与数据保存） |
| `java-cloud-standalone` | 单机微服务线（Gateway + 分域服务，共享同一 MySQL；详见该目录 README） |
| `ai-server` | 发帖检查、摘要、写作、生图、站内检索与看板娘 |
| `nginx` | 网站入口、容器配置、打包和发布脚本 |
| `live2d` | 看板娘模型资源 |

## Java 业务模块

```mermaid
flowchart LR
  U["页面或客户端"] --> C["Controller<br/>接收请求与返回结果"]
  C --> S["Service<br/>权限、状态与业务规则"]
  S --> MP["Mapper<br/>数据库持久化"]
  MP --> DB[("社区数据库")]
  S --> USER["账号与成长<br/>登录、会员、积分、签到"]
  S --> ARTICLE["内容社区<br/>帖子、板块、标签、评论、弹幕"]
  S --> SOCIAL["互动社交<br/>收藏、私信、群聊、漂流瓶"]
  S --> DISCOVER["内容发现<br/>搜索、热帖、推荐、兴趣"]
  S --> GAME["游戏中心<br/>大厅、房间、对局、排行"]
  S --> AI["AI 接入<br/>写作、审核、看板娘"]
  S --> R[("缓存层")]
  S --> MQ["异步消息队列"]
```

Java 模块按社区中的真实业务划分，而不是按页面拆分。同一条帖子无论从首页、搜索、收藏夹、个人主页还是看板娘对话进入，最终都回到同一份帖子和互动数据。

### 帖子发布与审核

```mermaid
flowchart LR
  U["作者"] --> E["编辑器<br/>富文本或 Markdown"]
  E --> V["登录、参数与内容基础校验"]
  V --> D["保存为草稿"]
  V --> P["提交审核"]
  P --> S["Java 写入待审核帖子"]
  S --> MQ["审核任务队列"]
  MQ --> A["AI 审核图<br/>文本、图片、视频"]
  A -->|通过与摘要| MR["审核结果队列"]
  A -->|拒绝或异常| MR
  MR --> R["Java 更新审核状态"]
  R --> OK["公开帖子"]
  R --> NO["驳回，作者可修改"]
  OK --> H["首页、板块、搜索、推荐"]
```

帖子、问答、视频和弹幕都建立在内容社区模块上。发布时先保存必要内容，再进行检查；最终是否公开由 Java 后端决定，AI 只给出内容检查结果。

### 帖子互动与通知

```mermaid
flowchart LR
  P["公开帖子"] --> L["点赞或取消点赞"]
  P --> C["发布评论"]
  C --> SR["回复与楼中楼"]
  P --> F["收藏到文件夹"]
  P --> DM["视频弹幕"]
  L --> DB[("互动记录")]
  C --> DB
  SR --> DB
  F --> DB
  DM --> DB
  L --> N["通知中心"]
  C --> N
  SR --> N
  F --> N
  DM --> N
  N --> WS["实时提醒或未读数"]
  WS --> U["相关用户"]
```

互动数据和帖子数据分开保存，但展示时会组合在一起。这样既能显示每个人的点赞、收藏和回复状态，也能让通知中心准确告诉用户发生了什么。

### 搜索、热帖与推荐

```mermaid
flowchart LR
  A["新帖子、浏览与互动"] --> I["内容与行为数据"]
  I --> H["定时计算热度"]
  H --> RC[("Redis 热帖缓存")]
  RC --> HOME["首页与板块"]
  Q["用户关键词"] --> S["帖子标题、正文与标签检索"]
  S --> MATCH["帖子结果<br/>高相似度作者结果"]
  MATCH --> HOME
  I --> F["生成帖子特征<br/>更新兴趣画像"]
  F --> REC["推荐候选与排序"]
  REC --> HOME
  HOME --> U["用户发现内容"]
```

发现模块不是另外复制一份帖子，而是根据已有内容、互动和兴趣帮助用户更快找到想看的内容。搜索优先找帖子本身；需要时也能找到与关键词高度相关的作者。

### 私信、群聊与实时互动

```mermaid
flowchart LR
  U1["发起方"] --> SEND["发送私信或群消息"]
  U2["接收方或群成员"] --> SEND
  SEND --> AUTH["身份与成员关系校验"]
  AUTH --> DB[("会话和消息记录")]
  DB --> WS["WebSocket 实时推送"]
  WS --> U1
  WS --> U2
  DB --> READ["未读数、已读状态、历史分页"]
  READ --> N["消息中心与通知"]
  VC["语音邀请"] --> AUTH
```

私信、群聊和语音功能共用“消息先保存，再实时送达”的思路。用户重新进入页面后仍能看到历史消息；多人同时在线时也能及时收到新消息。

### 游戏中心

```mermaid
flowchart LR
  U["玩家"] --> L["游戏大厅"]
  L --> M["创建、加入或匹配房间"]
  M --> JOIN["校验人数、状态与游戏类型"]
  JOIN --> WS["房间实时连接"]
  WS --> MOVE["落子、操作、聊天、重连"]
  MOVE --> RULE["对应游戏规则"]
  RULE --> STATE["广播最新房间状态"]
  RULE --> END["胜负、认输或结算"]
  END --> REC["保存战绩与回放"]
  REC --> RANK["积分、排行与个人资料"]
  RANK --> L
```

五子棋、井字棋和俄罗斯方块复用大厅、房间、实时连接和结算这套基本流程；每种游戏只保留各自的规则和画面，不重复建立一整套社交和排行系统。

## AI 模块

AI 模块采用“统一入口、模块化处理”的方式。Java 后端不直接调用某一张图或某个模型，而是把请求交给 AI 服务入口；AI 服务按 `taskType + intent + version` 路由到已注册模块。当前的八个 Python 模块目录承载内容审核、摘要、创作、生图、游戏、搜索、RAG、推荐和看板娘九类 Gateway 能力；Java 始终负责权限、额度、持久化与最终业务状态。

```mermaid
flowchart LR
  FE["用户端"] --> J["Java AI 接口"]
  J --> AUTH["校验用户、额度与内部密钥"]
  AUTH --> G["AI Gateway"]
  G --> ROUTE["模块路由<br/>taskType / intent / version"]
  ROUTE --> MOD["内容审核"]
  ROUTE --> SUM["帖子摘要"]
  ROUTE --> CRE["创作辅助<br/>润色、封面提示"]
  ROUTE --> IMG["图片生成"]
  ROUTE --> GAME["五子棋 AI"]
  ROUTE --> SEARCH["站内搜索"]
  ROUTE --> RAG["RAG 索引"]
  ROUTE --> REC["推荐特征与画像"]
  ROUTE --> MASCOT["看板娘 Agent"]
  MOD --> RESULT["标准事件与结果"]
  SUM --> RESULT
  CRE --> RESULT
  IMG --> RESULT
  GAME --> RESULT
  SEARCH --> RESULT
  RAG --> RESULT
  REC --> RESULT
  MASCOT --> RESULT
  RESULT --> J
  J --> FE
```

### 内容审核模块

```mermaid
flowchart LR
  P["待审核帖子"] --> J["Java 审核任务"]
  J --> Q["审核任务队列"]
  Q --> START["LangGraph 流程开始"]
  START --> TEXT["validate_text<br/>检查正文"]
  TEXT -->|通过| IMAGE["validate_images<br/>检查图片"]
  TEXT -->|拒绝| RT["reject_text"]
  IMAGE -->|通过| VIDEO["validate_video<br/>检查视频"]
  IMAGE -->|拒绝| RI["reject_image"]
  VIDEO -->|通过| SUM["summarize<br/>Flash 摘要"]
  VIDEO -->|拒绝| RV["reject_video"]
  SUM --> OK["审核通过"]
  RT --> FIN["finalize<br/>汇总结果"]
  RI --> FIN
  RV --> FIN
  OK --> FIN
  FIN --> RQ["审核结果队列"]
  RQ --> J2["Java 更新帖子状态"]
```

内容审核走 RabbitMQ 异步链路，避免用户发帖时一直等待。审核模块也提供单独的文本、图片检查入口：文本优先命中语义缓存；图片先校验 Base64、体积与格式，再由视觉模型描述、文本模型判定。无论 AI 给出什么结论，Java 才能变更帖子审核状态。

### 帖子摘要模块

```mermaid
flowchart LR
  A["详情页请求摘要"] --> J["Java 校验登录、额度与可见性"]
  J --> G["POST_SUMMARY / GENERATE / v1"]
  G --> CLEAN["清理 HTML，校验正文"]
  CLEAN --> SPLIT{"正文超过单段上限？"}
  SPLIT -->|否| ONE["Flash 模型直接摘要"]
  SPLIT -->|是| CHUNK["分块逐段摘要"]
  CHUNK --> MERGE["合并各段结果"]
  ONE --> RESULT["返回 summary / highlights / chunkCount"]
  MERGE --> RESULT
  RESULT --> J
  J --> UI["详情页展示，不修改原文"]
```

摘要模块使用 `AiRuntime` 调用快速文本模型；它仅输出内容理解结果，不修改帖子原文或审核状态。

### 创作与图片生成模块

```mermaid
flowchart LR
  U["作者"] --> E["编辑器"]
  E --> J["Java 校验登录、额度与草稿归属"]
  J --> P["POST_CREATION / POLISH"]
  J --> C["POST_CREATION / COVER_HINTS"]
  P --> POLISH["按体裁和编辑器模式<br/>润色正文"]
  C --> HINT["从正文提取封面提示词"]
  POLISH --> BACK["返回候选文本"]
  HINT --> BACK
  BACK --> E
  E --> IMG["IMAGE_GENERATION / GENERATE"]
  IMG --> GEN["校验提示词与质量参数<br/>调用生图能力"]
  GEN --> URL["图片地址与用量统计"]
  URL --> J
  J --> SAVE["用户确认后保存草稿或发布"]
```

创作模块只生成候选内容、提示词或图片 URL。草稿保存、发布、积分扣除与权限校验都留在 Java，AI 不会直接写入帖子状态。

### 游戏 AI 模块

```mermaid
flowchart LR
  ROOM["五子棋房间"] --> J["Java 校验回合、玩家与棋盘"]
  J --> G["GAME / GOBANG_MOVE / v1"]
  G --> VALID["校验落子位置合法性"]
  VALID --> RULE["规则层生成候选步"]
  RULE --> DEC{"启用大模型规划？"}
  DEC -->|是| PLAN["大模型评估候选步"]
  DEC -->|否或调用失败| FALLBACK["规则兜底选步"]
  PLAN --> MOVE["返回落子位置与用量"]
  FALLBACK --> MOVE
  MOVE --> J
  J --> APPLY["二次校验后落子并广播"]
```

游戏模块只建议一步棋；房间状态、胜负结算与 WebSocket 广播由 Java 游戏服务控制，模型不可绕过游戏规则。

### 推荐画像模块

```mermaid
flowchart TB
  subgraph Signal["行为与内容信号"]
    Publish["帖子审核通过"]
    Action["点赞、收藏、回复、兴趣设置"]
    Negative["标记不感兴趣 / 恢复"]
  end

  Publish -. "事务提交后异步" .-> FeatureTask["帖子特征提取任务"]
  Action -. "事务提交后异步" .-> ProfileTask["用户画像更新任务"]
  Negative --> Feedback["反馈记录表"]
  Negative -. "事务提交后异步" .-> ProfileTask

  FeatureTask --> AF["RECOMMENDATION / ARTICLE_FEATURE"]
  ProfileTask --> UP["RECOMMENDATION / USER_PROFILE"]
  AF --> AI["Gateway → LangChain → 模型"]
  UP --> AI
  AI --> Feature["forum_article_ai_feature<br/>topics / summary / fingerprint"]
  AI --> Snapshot["forum_user_ai_profile_snapshot<br/>topics / avoidTopics / summary"]

  Feed["请求推荐流"] --> Rule["Java 推荐规则<br/>召回、可见性、关注、热度"]
  Feedback --> Rule
  Feature --> Rule
  Snapshot --> Rule
  Rule --> Result["硬过滤不感兴趣<br/>兴趣加分、回避降分、后端分页"]
```

推荐 AI 只异步提炼公开帖主题与用户的聚合兴趣/回避主题，模型失败时使用规则兜底。推荐候选、帖子可见性、不感兴趣硬过滤和最终排序始终由 Java 完成；该模块不经过 RabbitMQ，也不需要 LangGraph。

### 看板娘助手模块

```mermaid
flowchart LR
  U["用户消息"] --> INIT["读取会话、记忆与上下文"]
  INIT -->|历史过长| COMP["MASCOT / CONTEXT_COMPRESS<br/>将历史压缩为摘要"]
  COMP --> INIT
  INIT --> SKILL["route_skill<br/>识别请求类型"]
  SKILL --> SUP["supervisor<br/>选择生图还是对话路径"]
  SUP -->|生图| IMG["image<br/>结合上下文生成图片"]
  SUP -->|对话| ASSESS["assess<br/>判断是否需要联网搜索"]
  ASSESS -->|需要| WEB["tavily_search<br/>搜索文字与图片"]
  ASSESS -->|不需要| AGENT["agent<br/>快速或深度回答"]
  WEB --> AGENT
  IMG --> SAVE["保存消息、图片与状态"]
  AGENT --> SAVE
  SAVE --> EVENT["流式推送文本、图集或帖子按钮"]
  EVENT --> U
```

看板娘会参考正在进行的对话。多数问题优先快速回答；只有需要复杂推理、规划或多步骤处理时才使用更强模型。长会话先压缩为摘要，检索到的帖子结果会保存到当前会话中，用户关闭再打开仍可以继续查看。

### 站内搜索模块

```mermaid
flowchart LR
  Q["搜索词"] --> J["Java 提取公开候选与用户上下文"]
  J --> G["SEARCH / QUERY / v1"]
  G --> CLEAN["规范化搜索词与范围"]
  CLEAN --> VECTOR["向量检索帖子与用户"]
  VECTOR --> HIT{"有向量命中？"}
  HIT -->|是| SCORE["候选 ID 与相关分数"]
  HIT -->|否| KEYWORD["关键词混合排序兜底"]
  KEYWORD --> SCORE
  SCORE --> J2["Java 过滤状态、作者与权限"]
  J2 --> PAGE["后端分页返回"]
```

搜索模块只返回候选 ID 与分数，响应中明确标注仍需 Java 权限过滤。看板娘需要外部网页或地图信息时，才在自身 Agent 图中按需调用对应 MCP 工具。

### RAG 索引模块

```mermaid
flowchart LR
  PUB["帖子公开 / 作者资料更新"] --> J["Java 确认业务状态已提交"]
  J --> IDX["RAG / INDEX_ARTICLE 或 INDEX_USER"]
  IDX --> CLEAN["规范化可检索字段"]
  CLEAN --> EMBED["文本向量化"]
  EMBED --> STORE["写入索引存储"]
  REMOVE["帖子下架或删除"] --> J2["Java 触发删除"]
  J2 --> DEL["RAG / REMOVE_ARTICLE"]
  DEL --> STORE
  STORE --> SEARCH["供搜索模块召回"]
```

RAG 模块将文章与用户资料的索引写入、删除统一收敛到 Gateway；它不拥有帖子公开状态，索引命中后仍由 Java 过滤。

### AI 使用的数据与记录

```mermaid
flowchart LR
  J["Java 社区后端"] --> MYSQL[("MySQL<br/>会话、额度、创作工作区、推荐结果")]
  J --> REDIS[("Redis<br/>验证码、缓存、任务去重")]
  J --> MQ["RabbitMQ<br/>审核任务与异步回调"]
  G["AI Gateway"] --> PG[("PostgreSQL<br/>LangGraph 流程状态")]
  G --> VECTOR["向量检索与站内内容候选"]
  G --> MODEL["Flash、深度推理与生图模型"]
  G --> MCP["搜索与地图工具"]
  MYSQL --> J
  MQ --> G
  G --> J
```

会话、额度和业务结果留在社区数据中；AI 流程本身保留必要的过程记录。这样既能让用户回到上次的对话，也能避免模型调用越过社区已有的账号和权限规则。

## 技术栈

### Java 后端

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Spring Boot | 3.5.x | Web、AMQP、Mail、Validation、WebSocket |
| MyBatis-Plus | 3.5.15 | Lambda API 查询，自带分页插件 |
| JJWT | 0.11.5 | JWT 签发与解析 |
| springdoc-openapi | 2.8.0 | 开发期 Swagger UI |
| spring-security-crypto | 随 Spring Boot | BCrypt 密码加密，未引入完整 Security 框架 |
| tianai-captcha | 1.5.5 | 滑块拼图验证码 |
| ip2region | 2.7.0 | 离线 IP 归属地解析 |
| Apache POI / PDFBox | 5.2.5 / 3.0.5 | Word、PDF 题库文档解析 |
| 阿里云 OSS SDK | 3.17.4 | 图片、视频文件存储 |
| 阿里云短信 SDK | 1.2.2 | 手机号验证码 |
| thumbnailator | 0.4.20 | 上传图片压缩与缩略图 |
| Hutool | 5.8.x | 通用工具集 |

### Python AI 服务

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Flask | 3.x | HTTP Gateway 入口 |
| LangChain Core | 0.3.x | 模型调用与链路封装 |
| LangGraph | 0.2.x | 审核、看板娘等有状态 Agent 图 |
| langgraph-checkpoint-postgres | 2.x | LangGraph 线程状态持久化到 PostgreSQL |
| dashscope | ≥1.20 | 通义系列模型，含文本、视觉和生图 |
| Pillow | ≥10 | 图片格式校验与处理 |
| pika | 1.3.2 | RabbitMQ 消费者，负责审核异步链路 |
| psycopg3 | ≥3.1 | PostgreSQL 驱动 |
| redis-py | ≥5.0 | 语义缓存与状态去重 |
| oss2 | ≥2.18 | 阿里云 OSS 写入 |

### 前端

| 依赖 | 用途 |
| --- | --- |
| Vue 3 + Vite | SPA 框架与构建工具 |
| Pinia + pinia-plugin-persistedstate | 状态管理，支持本地持久化 |
| Vue Router | 客户端路由 |
| Element Plus | UI 组件库，配置中文语言包 |

### 基础设施

| 服务 | 镜像版本 | 用途 |
| --- | --- | --- |
| Nginx | 1.30.1 | 反向代理、HTTPS 终止、静态资源分发 |
| MySQL | 9.7.0 | 主数据库 |
| Redis | 8.0 | 缓存与排行榜，allkeys-lru 策略，限 256 MB |
| RabbitMQ | 4.3-management | 审核任务的异步消息队列 |
| PostgreSQL | 17 | LangGraph checkpoint 持久化存储 |

## 本地启动

准备 Java 17、Python 3.11、Node.js、Docker Desktop，以及 MySQL、Redis、RabbitMQ、PostgreSQL。

```powershell
# 用户端
cd forum-vue\front
npm install
npm run dev

# 社区后端
cd ..\..\backend
mvn spring-boot:run

# AI 服务
cd ..\ai-server
python main.py
```

本地构建和 Docker 构建会通过本机 `7897` 代理访问外部依赖。密钥和环境差异只放在环境变量或被忽略的本地配置中，不放入仓库。

### 环境变量说明

参考 `nginx/.env.example`，主要配置项：

| 变量 | 说明 |
| --- | --- |
| `JWT_SECRET` | JWT 签名密钥，生产必须替换 |
| `PII_CRYPTO_SECRET` | 手机号等敏感字段对称加密密钥 |
| `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | 数据库密码 |
| `REDIS_PASSWORD` | Redis 认证密码 |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | MQ 账号与虚拟主机 |
| `DASHSCOPE_API_KEY` | 通义大模型 API Key |
| `TAVILY_API_KEY` | 看板娘联网搜索 Key |
| `HUANAPI_IMAGE_KEY` | 图片生成服务 Key |
| `ALIYUN_ACCESS_KEY_ID/SECRET` | 阿里云 OSS / 短信公共凭据 |
| `FORUM_MASCOT_INTERNAL_KEY` | Java ↔ Python 内部鉴权密钥，两侧须一致 |
| `FORUM_VOICE_TURN_*` | 语音通话用的 TURN 服务器地址和凭据 |

## 打包发布

```mermaid

flowchart LR
  CODE["当前源码"] --> BUILD["make-package.ps1"]
  BUILD --> FRONT["Vite 构建用户端"]
  BUILD --> BACK["构建 Java、AI、FFmpeg 镜像"]
  FRONT --> DIST["nginx dist"]
  BACK --> IMAGES["离线镜像 tar"]
  DIST --> PKG["nginx package"]
  IMAGES --> PKG
  SQL["sql 中的初始化与增量脚本"] --> PKG
  PKG --> CHECK["verify-package.ps1"]
  CHECK --> UPLOAD["仅上传 package 目录"]
  UPLOAD --> SERVER["up.sh 启动线上容器"]
```

在 `nginx` 目录执行：

```powershell
.\scripts\make-package.ps1
```

默认只显示简洁的构建结果。需要查看镜像构建详情时，再使用：

```powershell
.\scripts\make-package.ps1 -ShowBuildDetails
```

完整发布包会生成在 `nginx\package\`。上传前可校验：

```powershell
.\scripts\verify-package.ps1
```

发布包以 [`create.sql`](backend/src/main/resources/sql/create.sql) 作为新环境初始化基线；已上线环境按已执行的发布变更维护，不再要求携带历史增量 SQL。旧会话清理默认关闭，只有明确把脚本中的开关改为 `1` 才会执行清理。

## 核心设计思路

权限边界放在 Java 这侧。前端的登录拦截只是减少无效请求，身份校验和授权判断统一在 Service 层完成，AI 服务也只接受附带内部密钥的请求。

帖子有一套严格的状态流转：草稿 → 待审核 → 已发布，每一步只能由 Java Service 推进。审核期间字段锁定，前端拿不到直接改状态的入口。每次提交审核都会生成一个任务 ID 写入 `article.audit_task_id`，和 AI 侧 LangGraph 的 `thread_id` 对应，审核过程可以完整追溯。

AI 服务只管生成，不管写入。审核结论、摘要、推荐特征都由模型给出，但落库的动作始终在 Java 完成。这样换模型或 AI 服务临时挂掉，不会影响发帖、聊天这些核心功能。

消息的逻辑是先存后推。WebSocket 断线重连后，未读内容从数据库补全，不依赖连接状态。私信、群聊、语音邀请和系统通知走同一套流程，差别只在消息类型字段。

游戏的实时状态放在内存里，结束后再写库。对局中的每一步通过 WebSocket 广播给房间成员，游戏结束才把战绩和回放写进数据库，不做高频持久化。

站内搜索优先走向量召回，没命中再退回关键词排序。搜索模块只返回候选 ID 和相关分数，权限过滤和分页由 Java 完成，搜索结果不会泄露无权查看的内容。

## 数据表概览

84 张表按业务分组，每张表都有 `create_time`、`update_time`、`delete_state` 三个基础字段，主键统一用 `BIGINT AUTO_INCREMENT`。

| 分组 | 主要表 |
| --- | --- |
| 用户与账号 | `user`、`user_login_log`、`user_follow`、`user_interest_preference` |
| 帖子内容 | `article`、`article_image`、`article_reply`、`article_sub_reply`、`article_video_danmaku`、`forum_article_tag` |
| 互动行为 | `article_like`、`article_reply_like`、`article_favorite`、`user_favorite_folder` |
| 私信与群聊 | `message`、`group_chat`、`group_chat_member`、`group_chat_message` |
| 漂流瓶 | `drift_bottle`、`drift_bottle_comment`、`drift_bottle_pick_log` |
| 游戏 | `game_definition`、`game_gobang_match_record`、`game_jinzi_match_record`、`game_tetris_pk_match_record`、`game_user_profile` |
| 成长与积分 | `user_growth_profile`、`growth_experience_log`、`growth_challenge`、`points_log` |
| 抽奖 | `lottery_activity`、`lottery_prize`、`lottery_draw_record` |
| AI 相关 | `forum_ai_task_session`、`forum_ai_usage_log`、`forum_ai_creation_workspace`、`forum_user_ai_profile_snapshot`、`forum_article_ai_feature`、`forum_companion_session` |
| 通知 | `forum_notice`、`system_message`、`forum_outbox_message` |

## 容器服务说明

生产栈由 `nginx/docker-compose.yaml` 定义。所有服务在 `forum-net` 网络里用服务名互通，只有 Nginx 的 80 和 443 端口对外开放。

```mermaid
flowchart LR
  subgraph ext["外部访问"]
    HTTPS["443 HTTPS"]
    HTTP["80 HTTP"]
  end
  subgraph containers["forum-net 内部"]
    NGX["nginx"]
    BE1["backend-1"]
    BE2["backend-2\n(dual profile)"]
    AI["ai-server"]
    FF["ffmpeg"]
    MY[("mysql")]
    RD[("redis")]
    RMQ["rabbitmq"]
    PG[("postgres")]
  end
  HTTPS --> NGX
  HTTP --> NGX
  NGX --> BE1
  NGX --> BE2
  BE1 --> MY & RD & RMQ & AI & FF
  BE2 --> MY & RD & RMQ & AI
  AI --> PG & RD & RMQ
  FF --> AI
```

`backend-2` 只在 `--profile dual` 时启动，用于不中断服务的滚动发布。`ffmpeg` 容器处理视频转码任务，通过内部密钥与 Java 和 AI 服务通信。

## 开发约定

- 内容、权限和数据由社区后端统一决定，页面只负责展示和提交操作。
- AI 只通过统一入口接入，页面不直接调用具体模型。
- 新的数据库变更只追加新脚本，不修改已经执行过的历史脚本。
- 密码、密钥、验证码或本地环境配置不提交仓库。
