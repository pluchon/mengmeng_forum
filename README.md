<div align="center">

# 萌部落社区

<strong>内容交流 · 同好互动 · AI 陪伴</strong>

[![在线社区](https://img.shields.io/badge/在线社区-nuonuoya.cn-ff6b9b?style=for-the-badge)](https://www.nuonuoya.cn)
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3-42B883?style=for-the-badge&logo=vuedotjs&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)

[访问社区](https://www.nuonuoya.cn) · [项目结构](#项目结构) · [Java 业务](#java-业务模块) · [AI 模块](#ai-模块) · [本地启动](#本地启动)

</div>

萌部落是一个前后端分离的兴趣社区。它把发帖、评论、收藏、聊天、成长、游戏和 AI 陪伴放在一套社区体系中：用户只需围绕喜欢的内容交流，系统负责把内容保存、检查、分发和推荐出去。

## 项目总览

```mermaid
flowchart LR
  U["用户"] --> V["Vue 用户端"]
  V -->|页面、接口、WebSocket| N["Nginx 统一入口"]
  N -->|社区接口| J["Java 社区后端"]
  N -->|静态资源| V
  J -->|账号、帖子、互动、游戏| M[("MySQL")]
  J -->|缓存、验证码、排行榜| R[("Redis")]
  J -->|审核、通知、房间事件| Q["RabbitMQ"]
  J -->|内部鉴权请求| G["Python AI Gateway"]
  G -->|流程检查点| P[("PostgreSQL")]
  G -->|模型、检索、MCP 工具| X["外部 AI 能力"]
  Q --> W["异步消费者"]
  W --> J
```

整体思路很简单：用户端负责操作和展示；Java 后端负责社区规则、账号和数据；AI 服务负责理解、检索和生成。普通社区功能不依赖某个具体模型，因此 AI 能力以后单独调整时，不会影响发帖、聊天等基础体验。

## 项目结构

| 目录 | 负责什么 |
| --- | --- |
| `forum-vue` | 用户端页面、编辑器、社区互动与看板娘界面 |
| `backend` | 账号、帖子、互动、推荐、游戏、权限与数据保存 |
| `ai-server` | 发帖检查、摘要、写作、生图、站内检索与看板娘 |
| `nginx` | 网站入口、容器配置、打包和发布脚本 |
| `live2d` | 看板娘模型资源 |

## Java 业务模块

```mermaid
flowchart LR
  U["页面或客户端"] --> C["Controller<br/>接收请求与返回结果"]
  C --> S["Service<br/>权限、状态与业务规则"]
  S --> MP["Mapper<br/>持久化查询"]
  MP --> DB[("MySQL 社区数据")]
  S --> USER["账号与成长<br/>登录、会员、积分、签到"]
  S --> ARTICLE["内容社区<br/>帖子、板块、标签、评论、弹幕"]
  S --> SOCIAL["互动社交<br/>收藏、私信、群聊、漂流瓶"]
  S --> DISCOVER["内容发现<br/>搜索、热帖、推荐、兴趣"]
  S --> GAME["游戏中心<br/>大厅、房间、对局、排行"]
  S --> AI["AI 接入<br/>写作、审核、看板娘"]
  S --> R[("Redis 缓存")]
  S --> MQ["RabbitMQ 异步消息"]
```

Java 模块按社区中的真实业务划分，而不是按页面拆分。同一条帖子无论从首页、搜索、收藏夹、个人主页还是看板娘对话进入，最终都回到同一份帖子和互动数据。

### 帖子发布与审核

```mermaid
flowchart LR
  U["作者"] --> E["编辑器<br/>富文本或 Markdown"]
  E --> V["登录、参数与内容基础校验"]
  V --> D["草稿保存<br/>可继续编辑"]
  V --> P["提交发布"]
  P --> S["Java 写入待审核帖子"]
  S --> MQ["审核任务队列"]
  MQ --> A["AI 审核图<br/>文本、图片、视频"]
  A -->|通过与摘要| MR["审核结果队列"]
  A -->|拒绝或异常| MR
  MR --> R["Java 更新审核状态"]
  R --> OK["公开帖子"]
  R --> NO["保留原因，作者可修改"]
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
  U1["用户 A"] --> SEND["发送私信或群消息"]
  U2["用户 B 或群成员"] --> SEND
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
  JOIN --> WS["房间 WebSocket"]
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

AI 模块采用“统一入口、模块化处理”的方式。Java 后端不直接调用某一张图或某个模型，而是把请求交给 AI 服务入口；AI 服务再根据用途进入对应模块。这样现在可以共用一个服务，未来某项能力需要独立扩展时也容易拆开。

```mermaid
flowchart LR
  FE["用户端"] --> J["Java AI 接口"]
  J --> AUTH["当前用户、额度、内部密钥"]
  AUTH --> G["AI Gateway"]
  G --> ROUTE["模块注册与路由"]
  ROUTE --> MOD["内容检查"]
  ROUTE --> SUM["帖子摘要"]
  ROUTE --> CRE["创作辅助<br/>润色、封面要点"]
  ROUTE --> IMG["图片生成"]
  ROUTE --> SEARCH["站内检索与 RAG"]
  ROUTE --> MASCOT["看板娘 Agent"]
  MOD --> RESULT["标准事件与结果"]
  SUM --> RESULT
  CRE --> RESULT
  IMG --> RESULT
  SEARCH --> RESULT
  MASCOT --> RESULT
  RESULT --> J
  J --> FE
```

### 内容检查与帖子摘要

```mermaid
flowchart LR
  P["待审核帖子"] --> J["Java 审核任务"]
  J --> Q["RabbitMQ 审核队列"]
  Q --> START["LangGraph 开始"]
  START --> TEXT["validate_text<br/>检查正文"]
  TEXT -->|通过| IMAGE["validate_images<br/>检查图片"]
  TEXT -->|拒绝| RT["reject_text"]
  IMAGE -->|通过| VIDEO["validate_video<br/>检查视频"]
  IMAGE -->|拒绝| RI["reject_image"]
  VIDEO -->|通过| SUM["summarize<br/>Flash 摘要"]
  VIDEO -->|拒绝| RV["reject_video"]
  SUM --> OK["approved"]
  RT --> FIN["finalize"]
  RI --> FIN
  RV --> FIN
  OK --> FIN
  FIN --> RQ["审核结果队列"]
  RQ --> J2["Java 更新帖子状态"]
```

内容检查走异步流程，避免用户发帖时一直等待。摘要固定使用快速模型，作为展示和理解内容的辅助，不改变帖子原文。

### 看板娘助手

```mermaid
flowchart LR
  U["用户消息"] --> INIT["读取会话、记忆与上下文窗口"]
  INIT --> SKILL["route_skill<br/>识别请求类型"]
  SKILL --> SUP["supervisor<br/>决定图片或回答路径"]
  SUP -->|生图| IMG["image<br/>结合当前上下文生成"]
  SUP -->|对话| ASSESS["assess<br/>判断是否需要联网"]
  ASSESS -->|需要| WEB["tavily_search<br/>搜索文字与图片候选"]
  ASSESS -->|不需要| AGENT["agent<br/>快速回答或深度回答"]
  WEB --> AGENT
  IMG --> SAVE["保存消息、图片与状态"]
  AGENT --> SAVE
  SAVE --> EVENT["流式状态、文本、图集或帖子按钮"]
  EVENT --> U
```

看板娘会参考正在进行的对话。多数问题优先快速回答；只有需要复杂推理、规划或多步骤处理时才使用更强模型。检索到的帖子结果会保存到当前会话中，用户关闭再打开仍可以继续查看。

### 站内检索与工具使用

```mermaid
flowchart LR
  Q["用户问题与上下文"] --> PLAN["Agent 判断是否检索"]
  PLAN -->|站内内容| RAG["RAG 模块<br/>向量候选与关键词候选"]
  RAG --> JAVA["Java 检查公开状态、作者与权限"]
  JAVA --> POST["相关帖子或作者"]
  PLAN -->|互联网信息| MCP["搜索 MCP"]
  MCP --> TEXT["网页摘要"]
  MCP --> PIC["最多五张相关图片"]
  PLAN -->|外出话题| MAP["地图 MCP<br/>模糊地区天气信息"]
  TEXT --> ANSWER["Agent 组织自然回答"]
  PIC --> ANSWER
  MAP --> ANSWER
  POST --> ANSWER
```

AI 可以帮助查找站内内容，也可以在需要时使用外部工具。但是否向用户展示内容、是否消耗额度、是否保存结果，仍然由社区后端统一决定。

### AI 使用的数据与记录

```mermaid
flowchart LR
  J["Java 社区后端"] --> MYSQL[("MySQL<br/>会话、额度、创作工作区、推荐结果")]
  J --> REDIS[("Redis<br/>验证码、缓存、任务去重")]
  J --> MQ["RabbitMQ<br/>审核与异步结果"]
  G["AI Gateway"] --> PG[("PostgreSQL<br/>LangGraph checkpoint")]
  G --> VECTOR["向量检索与站内内容候选"]
  G --> MODEL["Flash、深度推理与生图模型"]
  G --> MCP["搜索与地图 MCP"]
  MYSQL --> J
  MQ --> G
  G --> J
```

会话、额度和业务结果留在社区数据中；AI 流程本身保留必要的过程记录。这样既能让用户回到上次的对话，也能避免模型调用越过社区已有的账号和权限规则。

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

线上 MySQL 增量统一使用 [`incremental_mysql_release.sql`](backend/src/main/resources/sql/incremental_mysql_release.sql)。旧会话清理默认关闭，只有明确把脚本中的开关改为 `1` 才会执行清理。

## 开发约定

- 内容、权限和数据由社区后端统一决定，页面只负责展示和提交操作。
- AI 只通过统一入口接入，不让页面直接调用具体模型。
- 新的数据库变更只追加新脚本，不修改已经执行过的历史脚本。
- 不把密码、密钥、验证码或本地环境配置提交到仓库。
