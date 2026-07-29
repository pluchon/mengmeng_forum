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
  U["用户"] --> F["Vue 用户端"]
  F --> N["Nginx 网站入口"]
  N --> J["Java 社区后端"]
  J --> A["Python AI 服务"]
  J --> D[("MySQL 社区数据")]
  J --> R[("Redis 热门与缓存")]
  J --> Q["RabbitMQ 异步任务"]
  A --> P[("PostgreSQL AI 流程记录")]
  A --> L["文本与图片模型"]
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
  U["用户请求"] --> API["Java 接口层"]
  API --> USER["账号与成长\n登录、会员、积分、签到"]
  API --> ARTICLE["内容社区\n帖子、板块、标签、评论、弹幕"]
  API --> SOCIAL["互动社交\n收藏、私信、群聊、漂流瓶"]
  API --> DISCOVER["发现内容\n搜索、热帖、推荐、兴趣"]
  API --> GAME["游戏中心\n大厅、房间、对局、排行"]
  API --> AI["AI 接入\n写作、审核、看板娘"]
  USER --> DB[("MySQL")]
  ARTICLE --> DB
  SOCIAL --> DB
  DISCOVER --> DB
  GAME --> DB
  AI --> DB
```

Java 模块按社区中的真实业务划分，而不是按页面拆分。同一条帖子无论从首页、搜索、收藏夹、个人主页还是看板娘对话进入，最终都回到同一份帖子和互动数据。

### 帖子发布与审核

```mermaid
flowchart LR
  U["作者填写标题和正文"] --> E["编辑器\n富文本或 Markdown"]
  E --> V["登录与基础校验"]
  V --> S["保存帖子草稿或待发布内容"]
  S --> Q["发送审核任务"]
  Q --> A["AI 审核"]
  A --> R["返回审核结果"]
  R --> P["通过后公开展示\n不通过则保留结果"]
  P --> H["首页、板块、搜索和推荐"]
```

帖子、问答、视频和弹幕都建立在内容社区模块上。发布时先保存必要内容，再进行检查；最终是否公开由 Java 后端决定，AI 只给出内容检查结果。

### 帖子互动与通知

```mermaid
flowchart LR
  P["一篇帖子"] --> L["点赞"]
  P --> C["评论"]
  C --> R["回复与楼中楼"]
  P --> F["收藏到文件夹"]
  P --> D["视频弹幕"]
  L --> N["通知中心"]
  R --> N
  F --> N
  D --> N
  N --> U["相关用户"]
```

互动数据和帖子数据分开保存，但展示时会组合在一起。这样既能显示每个人的点赞、收藏和回复状态，也能让通知中心准确告诉用户发生了什么。

### 搜索、热帖与推荐

```mermaid
flowchart LR
  A["新帖子与互动"] --> I["内容信息"]
  I --> H["热帖榜"]
  I --> S["关键词搜索"]
  I --> R["兴趣推荐"]
  H --> HOME["首页与板块"]
  S --> HOME
  R --> HOME
  HOME --> U["用户发现内容"]
```

发现模块不是另外复制一份帖子，而是根据已有内容、互动和兴趣帮助用户更快找到想看的内容。搜索优先找帖子本身；需要时也能找到与关键词高度相关的作者。

### 私信、群聊与实时互动

```mermaid
flowchart LR
  U1["用户 A"] --> M["私信或群聊"]
  U2["用户 B / 群成员"] --> M
  M --> S["消息服务"]
  S --> DB[("消息记录")]
  S --> W["实时连接"]
  W --> U1
  W --> U2
  S --> N["系统通知"]
```

私信、群聊和语音功能共用“消息先保存，再实时送达”的思路。用户重新进入页面后仍能看到历史消息；多人同时在线时也能及时收到新消息。

### 游戏中心

```mermaid
flowchart LR
  U["玩家进入游戏中心"] --> L["游戏大厅"]
  L --> M["创建或匹配房间"]
  M --> W["实时对局"]
  W --> R["结算结果"]
  R --> H["战绩与排行"]
  H --> L
```

五子棋、井字棋和俄罗斯方块复用大厅、房间、实时连接和结算这套基本流程；每种游戏只保留各自的规则和画面，不重复建立一整套社交和排行系统。

## AI 模块

AI 模块采用“统一入口、模块化处理”的方式。Java 后端不直接调用某一张图或某个模型，而是把请求交给 AI 服务入口；AI 服务再根据用途进入对应模块。这样现在可以共用一个服务，未来某项能力需要独立扩展时也容易拆开。

```mermaid
flowchart LR
  FE["用户端"] --> J["Java AI 接口"]
  J --> G["AI Gateway"]
  G --> M["内容检查"]
  G --> S["帖子摘要"]
  G --> C["创作辅助\n润色、封面要点"]
  G --> I["图片生成"]
  G --> R["站内检索"]
  G --> K["看板娘"]
  M --> J
  S --> J
  C --> J
  I --> J
  R --> J
  K --> J
```

### 内容检查与帖子摘要

```mermaid
flowchart LR
  P["待发布帖子"] --> J["Java 审核服务"]
  J --> Q["审核任务队列"]
  Q --> G["审核流程图"]
  G --> T["文本和图片检查"]
  T --> S["生成简短摘要"]
  S --> Q2["审核结果队列"]
  Q2 --> J
  J --> R["更新帖子状态"]
```

内容检查走异步流程，避免用户发帖时一直等待。摘要固定使用快速模型，作为展示和理解内容的辅助，不改变帖子原文。

### 看板娘助手

```mermaid
flowchart LR
  U["用户消息"] --> H["读取当前会话"]
  H --> J["判断用户想做什么"]
  J --> C["普通对话"]
  J --> R["查找相关帖子"]
  J --> I["生成图片"]
  J --> W["协助写作或说明站内功能"]
  R --> V["站内内容检索"]
  C --> O["返回回答"]
  V --> O
  I --> O
  W --> O
```

看板娘会参考正在进行的对话。多数问题优先快速回答；只有需要复杂推理、规划或多步骤处理时才使用更强模型。检索到的帖子结果会保存到当前会话中，用户关闭再打开仍可以继续查看。

### 站内检索与工具使用

```mermaid
flowchart LR
  Q["用户的问题"] --> R["理解关键词和上下文"]
  R --> V["从站内内容中找候选"]
  V --> F["Java 再次确认公开状态和权限"]
  F --> D["返回相关帖子或作者"]
  Q --> T["需要时使用天气、地图等工具"]
  T --> D
```

AI 可以帮助查找站内内容，也可以在需要时使用外部工具。但是否向用户展示内容、是否消耗额度、是否保存结果，仍然由社区后端统一决定。

### AI 使用的数据与记录

```mermaid
flowchart LR
  J["Java 社区后端"] --> M[("MySQL\n会话、额度、业务记录")]
  G["AI Gateway"] --> R[("Redis\n检索与临时缓存")]
  G --> P[("PostgreSQL\n流程检查点")]
  G --> L["文本和图片模型"]
  J <--> G
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

本轮 AI 模块的线上增量脚本是 [`incremental_ai_module_release.sql`](backend/src/main/resources/static/incremental_ai_module_release.sql)。旧会话清理默认关闭，只有明确把脚本中的开关改为 `1` 才会执行清理。

## 开发约定

- 内容、权限和数据由社区后端统一决定，页面只负责展示和提交操作。
- AI 只通过统一入口接入，不让页面直接调用具体模型。
- 新的数据库变更只追加新脚本，不修改已经执行过的历史脚本。
- 不把密码、密钥、验证码或本地环境配置提交到仓库。
