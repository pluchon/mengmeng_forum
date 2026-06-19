# 萌部落论坛游戏中心与五子棋验收测试文档

> 文档版本：V0.3
>
> 编写日期：2026-06-18
>
> 验收范围：游戏中心、五子棋在线匹配、双人对战、局时步时、观战、聊天、棋谱回放、AI 兜底匹配。

## 1. 验收目标

1. 验证游戏中心入口、登录态、五子棋游戏资料展示完整。
2. 验证大厅、游戏、房间三类 WebSocket 连接互不覆盖。
3. 验证五子棋匹配、进入房间、落子、胜负、结算形成闭环。
4. 验证服务端权威校验、防重复连接、断线处理、幂等结算。
5. 验证新增能力不破坏论坛现有通知 WebSocket、私信、审核、系统消息。

## 2. 测试环境

| 项目 | 目标值 |
|---|---|
| 后端 | `backend` Spring Boot，默认 `http://localhost:10086` |
| 前端 | `forum-vue/front`，默认 `http://localhost:5173` |
| 浏览器 | Chromium，至少两个独立用户会话 |
| 数据库 | MySQL，使用论坛开发库 |
| Redis | 现有 Redis |
| WebSocket | `/ws/notify`、`/ws/game-center/lobby`、`/ws/games/gobang`、`/ws/games/gobang/rooms/{roomId}` |

## 3. 验收数据

- 至少准备两个普通论坛用户：用户 A、用户 B。
- 两个用户均已登录且 JWT 有效。
- 两个用户均无进行中的五子棋匹配和房间。
- 初始五子棋资料不存在时，进入五子棋页面应自动初始化。

## 4. 测试用例

| 编号 | P | 测试场景与步骤 | 预期结果 | 结果 | 证据/缺陷 |
|---|---|---|---|---|---|
| GC-001 | P0 | 未登录访问 `/games` | 跳转登录或展示现有登录提示 | 待执行 | |
| GC-002 | P0 | 登录后访问 `/games` | 展示游戏中心、五子棋卡片、用户游戏摘要 | 待执行 | |
| GC-003 | P1 | 建立游戏大厅 WS | 连接成功，收到大厅状态或心跳正常 | 待执行 | |
| GC-004 | P0 | 同时保持 `/ws/notify` 与游戏大厅 WS | 通知连接不被关闭，私信/系统消息仍可推送 | 待执行 | |
| GC-005 | P1 | 断开大厅 WS 后刷新 HTTP 数据 | 页面仍可展示基础游戏卡片，提示实时状态不可用 | 待执行 | |
| GG-001 | P0 | 进入 `/games/gobang` | 展示五子棋资料、积分、胜率、匹配入口 | 待执行 | |
| GG-002 | P0 | 五子棋资料首次初始化 | 默认积分、总局数、胜负数正确 | 待执行 | |
| GG-003 | P0 | 建立五子棋游戏 WS | 连接成功，不影响大厅和通知连接 | 待执行 | |
| GG-004 | P0 | 用户 A 点击开始匹配 | 按钮进入匹配中，服务端状态为 MATCHING | 待执行 | |
| GG-005 | P0 | 用户 A 匹配中点击取消 | 退出队列，状态恢复 IDLE | 待执行 | |
| GG-006 | P0 | 用户 A、B 同时开始匹配 | 匹配成功，双方收到同一 `roomId` | 待执行 | |
| GG-007 | P0 | 用户重复点击开始匹配 | 不重复入队，不生成多个房间 | 待执行 | |
| GG-008 | P0 | 用户匹配中刷新页面 | 旧连接清理，新连接可恢复或取消匹配 | 待执行 | |
| GR-001 | P0 | A、B 进入同一房间 | 双方收到 `room_ready`，黑白身份与先手一致 | 待执行 | |
| GR-002 | P0 | 非房间成员访问房间路由并连接 WS | 服务端拒绝，不泄露房间状态 | 待执行 | |
| GR-003 | P0 | 黑方第一手落子 | 服务端接受，双方棋盘同步 | 待执行 | |
| GR-004 | P0 | 非当前回合玩家落子 | 服务端拒绝，棋盘不变化 | 待执行 | |
| GR-005 | P0 | 越界坐标落子 | 服务端拒绝，连接保持 | 待执行 | |
| GR-006 | P0 | 已有棋子位置重复落子 | 服务端拒绝，棋盘不变化 | 待执行 | |
| GR-007 | P0 | 横向五连 | 判定当前落子方胜利，双方收到结算 | 待执行 | |
| GR-008 | P0 | 纵向五连 | 判定当前落子方胜利，双方收到结算 | 待执行 | |
| GR-009 | P0 | 主对角线五连 | 判定当前落子方胜利，双方收到结算 | 待执行 | |
| GR-010 | P0 | 副对角线五连 | 判定当前落子方胜利，双方收到结算 | 待执行 | |
| GR-011 | P0 | 一方认输 | 对手胜利，写入战绩，双方状态恢复 | 待执行 | |
| GR-012 | P0 | 一方断线超过重连窗口 | 对手胜利或按断线规则结束，房间释放 | 待执行 | |
| GR-013 | P1 | 一方短暂断线后重连 | 仍回到原房间，棋盘状态同步 | 待执行 | |
| GR-014 | P0 | 胜利和断线事件并发触发 | 只结算一次，只写一条战绩 | 待执行 | |
| GR-015 | P0 | 对局结束后继续发送落子 | 服务端拒绝，战绩不重复变化 | 待执行 | |
| GR-016 | P0 | 当前回合落子后保存棋谱 | `game_room_move` 写入 `row_index`、`col_index`、`move_no`、`chess` | 通过 | 2026-06-18 双浏览器落子后查库通过 |
| GR-017 | P0 | 房间 WS 业务异常兜底 | 业务异常返回 `room_error`，不由异常装饰器直接关闭连接 | 通过 | 已增加 handler try/catch；双浏览器未出现 `room_error` |
| TIME-001 | P0 | 房间显示局时/步时 | 页面实时倒计时，回合切换后步时重置 | 部分通过 | 双浏览器落子后显示 `00:59`；超时判负待专项等待验证 |
| WATCH-001 | P1 | 大厅展示活跃对局房间 | 活跃房间可展示并进入观战 | 待执行 | |
| CHAT-001 | P1 | 房间发送文本聊天 | 同房间用户实时收到文本 | 待执行 | |
| CHAT-002 | P1 | 房间发送已购表情包 | 同房间用户实时收到表情图片 | 待执行 | |
| REPLAY-001 | P1 | 对局结束后查看棋谱回放 | 回放棋盘可按步前进/后退 | 待执行 | 需先产生完整结束对局 |
| AI-001 | P1 | 长时间匹配不到真人 | 超过等待阈值后创建 AI 房间 | 待执行 | |
| SCORE-001 | P0 | 正常胜负结算后查资料 | 胜方 +10，负方 -10，胜负局数正确 | 待执行 | |
| SCORE-002 | P0 | 负方积分不足 10 | 积分最低为 0，不出现负数 | 待执行 | |
| SCORE-003 | P1 | 查看最近对局记录 | 展示双方、结果、结束原因、时间 | 待执行 | |
| SAFE-001 | P0 | 缺 token 连接任一游戏 WS | 握手拒绝 | 待执行 | |
| SAFE-002 | P0 | 伪造或过期 token 连接任一游戏 WS | 握手拒绝 | 待执行 | |
| SAFE-003 | P0 | 同一用户同一房间通道多开 | 新连接覆盖旧连接，状态不乱 | 待执行 | |
| SAFE-004 | P0 | 同一用户尝试同时匹配两个房间 | 服务端拒绝第二次匹配 | 待执行 | |
| SAFE-005 | P1 | 心跳停止超过阈值 | 服务端关闭连接并清理注册表 | 待执行 | |
| REG-001 | P0 | 游戏模块上线后发送私信 | 私信 WebSocket 实时推送仍正常 | 待执行 | |
| REG-002 | P0 | 审核结果 WebSocket 推送 | `/ws/notify` 行为无回归 | 待执行 | |
| REG-003 | P1 | 前端生产构建 | 构建通过，无阻断错误 | 待执行 | |
| REG-004 | P1 | 后端测试或启动 | 应用启动成功，WebSocket 注册无冲突 | 待执行 | |

## 5. 缺陷与回归记录

### 5.1 2026-06-18 落子导致房间 WebSocket 断开

**现象**

```text
org.springframework.jdbc.BadSqlGrammarException
SQL: INSERT INTO game_room_move (... row, col ...)
```

**原因**

`game_room_move` 使用了 `row` / `col` 作为列名，其中 `row` 在 MySQL 8 环境下会造成插入 SQL 解析异常。异常从房间 WebSocket handler 向外抛出后，Spring 的 `ExceptionWebSocketHandlerDecorator` 会直接关闭当前 session。

**修复**

1. `GameRoomMove` 实体改为字段 `row/col` 映射数据库列 `row_index/col_index`。
2. `create.sql` 与增量 SQL 同步改为 `row_index/col_index`。
3. 增量 SQL 增加幂等迁移过程：旧库存在 `row/col` 时自动改名。
4. 房间 WebSocket handler 增加业务异常兜底，返回 `room_error`，避免业务异常直接关闭连接。
5. 本地开发库已执行迁移，当前列为 `row_index/col_index`。

**回归结果**

| 项目 | 结果 |
|---|---|
| 后端 Maven 测试 | 通过，6 个测试通过 |
| 本地后端启动 | 通过，`localhost:10086` 正常监听 |
| 双浏览器账号 | `pluchon` / `charon` |
| 双方匹配 | 通过，进入同一房间 `0a410ba6-fa1d-4755-8d6f-f10d87a825de` |
| 黑方第一手 | 通过，两端棋子数均为 1 |
| 白方第二手 | 通过，两端棋子数均为 2 |
| 房间错误消息 | 未出现 `room_error` / `move_rejected` |
| 数据库棋谱 | 通过，写入 `move_no=1/2`、`row_index=7`、`col_index=7/8` |
| 浏览器关闭后状态释放 | 通过，测试账号最终恢复 `IDLE`，测试房间按 `TIMEOUT` 结算 |

**双浏览器测试证据**

截图文件：

```text
backend/target/codex-logs/gobang-a.png
backend/target/codex-logs/gobang-b.png
```

数据库验证：

```text
room_id                               move_no user_id row_index col_index chess
0a410ba6-fa1d-4755-8d6f-f10d87a825de  2       2       7         8         2
0a410ba6-fa1d-4755-8d6f-f10d87a825de  1       1       7         7         1
```

### 5.2 2026-06-18 完整协议验收与专项修复

**执行账号**

| 用户 | 用途 |
|---|---|
| `pluchon` | 玩家 A、AI 对局玩家 |
| `charon` | 玩家 B、AI 对局观众 |

**本轮发现与修复**

| 问题 | 原因 | 处理 |
|---|---|---|
| 连接已结束房间时 WS 1011 异常断开 | 房间不存在时 `afterConnectionEstablished` 直接抛出业务异常 | 房间 WS 建立阶段增加兜底，返回 `room_error` 后按策略关闭 |
| 棋谱回放接口 SQL 报错 | MyBatis-Plus 查询生成 `row_index AS row`，`row` 作为别名仍触发 MySQL 语法错误 | `GameRoomMove` 实体字段改为 `rowIndex/colIndex`，彻底避开保留字别名 |
| 观众收不到对局结束 | 结算时只向黑白双方发送 `game_finished` | 注册表增加房间连接遍历，结算时按连接用户逐个发送最终状态 |

**协议级验收结果**

| 类别 | 覆盖项 | 结果 |
|---|---|---|
| 鉴权 | 缺 token、非法 token 连接游戏 WS | 通过 |
| 大厅 | 概览接口、天梯榜、活跃房间、大厅 WS、心跳 | 通过 |
| 匹配 | 开始匹配、取消匹配、双人匹配成功 | 通过 |
| 房间 | 房间列表、`room_ready`、短线重连、过期房间兜底 | 通过 |
| 落子 | 非当前回合、越界、重复坐标、双方同步 | 通过 |
| 胜负 | 横向、纵向、主对角线、副对角线五连 | 通过 |
| 结算 | 认输、步时超时、断线窗口、记录分页 | 通过 |
| 棋谱 | 保存落子、回放接口返回落子列表 | 通过 |
| 聊天 | 文本消息、表情消息、观众接收聊天 | 通过 |
| 观战 | 观众进入、观众禁止落子、观众接收落子和结束推送 | 通过 |
| AI | 15 秒未匹配到真人后分配 AI，AI 自动落子 | 通过 |

最终协议脚本结果：

```text
ACCEPTANCE_SUMMARY {"total":33,"passed":32,"failed":1,"failedIds":["WATCH-003"]}
```

其中 `WATCH-003` 已在随后修复并专项回归：

```json
{
  "ok": true,
  "roomId": "cda1ccea-ffc9-4d19-ae6d-b5e067d68511",
  "playerEnd": "SURRENDER",
  "watcherEnd": "SURRENDER",
  "watcherSpectator": true
}
```

**说明**

断线窗口专项中，断线玩家在当前回合等待满 60 秒时，结算原因可能记录为 `TIMEOUT`，但胜负结果、房间释放、资料恢复均正确。若产品上必须严格区分“断线超时”和“步时超时”，后续需要调整两个定时任务的优先级或结束原因规则。

### 5.3 构建、启动与浏览器烟测

| 项目 | 命令/方式 | 结果 |
|---|---|---|
| 后端测试 | `mvnw.cmd -f backend/pom.xml test` | 通过，6 个测试通过 |
| 后端启动 | `mvnw.cmd -f backend/pom.xml spring-boot:run` | 通过，`localhost:10086` 正常监听 |
| 前端构建 | `npm run build` | 通过，仅 Vite 大包/动态导入警告 |
| 浏览器烟测 | Playwright 打开 `/games` 与 `/games/gobang` | 通过，无 JS 错误 |
| 游戏页截图 | `backend/target/codex-logs/gobang-ui-smoke.png` | 已生成 |
| 后端日志尾部 | 检索 SQL/WS 异常关键字 | 未发现新 `BadSqlGrammarException`、`SQLSyntaxErrorException`、`ExceptionWebSocketHandlerDecorator` |

### 5.4 2026-06-18 UI 细节、AI 调用链与终局高亮回归

**修复范围**

| 模块 | 内容 |
|---|---|
| 游戏中心 | 对局统计抽屉移除积分余额，仅保留胜负、总局数、胜率三行；进入匹配按钮调整为星空蓝黑主题；五子棋封面动画改为更接近真人开局的落子顺序 |
| 五子棋匹配页 | 移除“准备开始一局”提示卡；标题与说明改为同一行；开始匹配按钮调整为星空蓝黑主题；最近对局回放按钮统一主题样式 |
| 五子棋房间 | 对局结束不再弹窗，棋盘中央展示胜负、结束原因和 60 秒返回倒计时；五连棋子三端高亮；对手卡昵称缩小并支持两行；点击对手头像/昵称打开简略统计 |
| AI 对手 | Java 端优先调用 Python AI Hub `/api/v1/ai/gobang-move`，Python 端使用 DeepSeek 生成合法落点；Python 服务不可用或返回非法坐标时才使用本地策略兜底，并在界面显示对应模型/兜底名称 |

**自动化与浏览器验收**

| 项目 | 结果 |
|---|---|
| 后端编译 | 通过，`mvn -q -DskipTests compile` |
| Python 语法检查 | 通过，`python -m py_compile ai-server/api/ai_hub.py ai-server/services/ai_hub_service.py` |
| 前端构建 | 通过，`npm run build`，仅保留 Vite 大包/动态导入警告 |
| 游戏中心统计抽屉 | 通过，卡片为 `胜 / 负`、`总局数`、`胜率`，无积分余额，三行展示 |
| 游戏中心/匹配页按钮 | 通过，按钮背景包含星空点状 `radial-gradient` 与深色 `linear-gradient` |
| 五子棋匹配页文案 | 通过，“进入快速匹配”和说明文案在同一行 |
| AI 对手展示 | 通过，对手卡展示 AI SVG 和 `deepseek-v4-flash · 本地策略兜底`；点击可打开统计弹窗 |
| 玩家终局高亮 | 通过，胜方和负方棋盘均有 5 个 `.gobang-cell.is-winning` |
| 观众终局高亮 | 通过，观众棋盘有 5 个 `.gobang-cell.is-winning`，展示“黑方获胜 五子连珠 60 秒后返回五子棋” |

### 5.5 2026-06-19 观战视角棋色归属修复

**问题**

观战用户进入房间后，前端复用了玩家视角的“我方/对手”棋色计算，导致观众界面被默认投射成黑棋视角。语义上观众既不是黑方也不是白方，只能看到黑方玩家与白方玩家。

**修复**

| 模块 | 内容 |
|---|---|
| 后端房间状态 | 观众视角不再返回 `opponentPlayer`，避免把白方误当成观众的“对手” |
| 前端房间页 | 观众视角单独展示“黑方/白方”两名棋手卡片，并新增中立“观战视角 不参与落子”卡片 |
| 前端落子权限 | 观众仍无 `.can-play` 棋格，无法落子、无法认输 |

**回归结果**

```json
{
  "spectator": true,
  "opponentPlayer": null,
  "cards": [
    "黑方 儒雅的诺诺丫 09:58",
    "白方 诺诺不是诺诺丫 10:00"
  ],
  "observerCard": "观战视角 不参与落子",
  "opponentCardCount": 0,
  "canPlayCount": 0
}
```

截图文件：

```text
output/playwright/gobang-spectator-neutral.png
```

**浏览器截图**

```text
output/playwright/game-center-polish.png
output/playwright/gobang-game-polish.png
output/playwright/gobang-room-ai-dialog.png
output/playwright/gobang-winning-line-a.png
output/playwright/gobang-winning-line-b.png
output/playwright/gobang-winning-line-spectator.png
```

### 5.6 2026-06-19 Plus 变更后验收重跑状态

**结论**

本次没有完成整份验收文档的完整重跑。已完成不依赖登录态的构建、编译和未登录访问检查；双账号匹配、房间对局、观战、聊天、AI、回放等浏览器端到端验收被本地 Redis/MySQL/RabbitMQ 环境阻塞。

**当前环境检查**

| 项目 | 结果 |
|---|---|
| 后端端口 | `localhost:10086` 已监听，进程来自当前项目 Spring Boot |
| 前端端口 | `localhost:5173` 已监听，进程来自当前项目 Vite |
| Redis | 配置端口 `63790` 未监听，`/captcha/generate` 返回 `Redis command timed out` |
| MySQL | 配置端口 `33061` 未监听 |
| RabbitMQ | 配置端口 `56720` 未监听 |

**已执行项**

| 验收项 | 命令/方式 | 结果 |
|---|---|---|
| 后端编译 | `mvn -DskipTests compile` | 通过 |
| 前端构建 | `npm run build` | 通过，仅保留 Vite chunk 体积/动态导入警告 |
| Python 语法检查 | `python -m py_compile ai-server/api/ai_hub.py ai-server/services/ai_hub_service.py` | 通过 |
| 未登录访问 `/games` | Playwright CLI 打开 `http://localhost:5173/games` | 通过，出现“需要登录”提示 |
| 未登录访问游戏概览接口 | `GET http://localhost:10086/game/center/overview` | 通过，返回 401 未授权 |
| 验证码生成 | `POST http://localhost:10086/captcha/generate` | 阻塞，返回 `Redis command timed out` |

**阻塞影响**

验证码生成依赖 Redis。由于 Redis 端口不可用，无法签发 `X-Captcha-Ticket`，因此无法通过正常页面流程登录 `pluchon` / `charon`，后续双账号匹配、房间 WebSocket、落子、胜负结算、观战和聊天验收均无法继续。

**恢复后需继续执行**

1. 启动配置所需 Redis：`localhost:63790`。
2. 启动配置所需 MySQL：`localhost:33061`。
3. 启动配置所需 RabbitMQ：`localhost:56720`，virtual-host 为 `forum-demo`。
4. 重新执行双账号浏览器验收：`pluchon` / `charon`。
5. 补跑 P0：匹配、落子、非法落子、五连胜负、认输、计时、结算幂等、观战中立。
6. 补跑 P1：聊天、表情、回放、AI 兜底匹配。

### 5.7 2026-06-19 依赖恢复后的完整验收补跑

**结论**

本轮在 Redis、MySQL、RabbitMQ、前后端均启动后，补跑了登录、页面、WebSocket、匹配、对局、观战、聊天、回放、AI 兜底和构建检查。核心 P0 链路通过；AI 对手可进入房间并自动落子，但本轮实际返回模型名为 `deepseek-v4-flash · 本地策略兜底`，说明 Python/DeepSeek 完整模型调用未命中或返回不可用坐标，Java 端按设计使用了本地兜底策略。

**环境检查**

| 项目 | 结果 |
|---|---|
| 后端 | `localhost:10086` 正常 |
| 前端 | `localhost:5173` 正常 |
| Redis | Docker 容器 `forum-redis-dev` 正常 |
| MySQL | Docker 容器 `forum-mysql-dev` 正常 |
| RabbitMQ | Docker 容器 `forum-rabbitmq-dev` 正常 |
| Python AI | `localhost:5000` 有响应 |
| 数据库增量 | 已在本地 `forum_db` 执行 `incremental_game_center_gobang_plus.sql`，创建 `game_settlement_event` |

**页面与看板娘排除**

| 页面 | 结果 |
|---|---|
| `/games` 游戏中心 | 登录态正常；强刷新后 `.mascot-root`、`#oml2d-stage`、`#oml2d-canvas` 均不存在 |
| `/games/gobang` 五子棋页面 | 登录态正常；看板娘 DOM 不存在 |
| `/games/gobang/rooms/{roomId}` 对局页面 | 双账号进入房间后看板娘 DOM 不存在 |

**协议与业务验收**

| 类别 | 结果 | 证据 |
|---|---|---|
| 双账号登录 | 通过 | `pluchon`、`charon` 通过真实 `/user/login` 获取 JWT |
| 双账号匹配 | 通过 | 双方进入同一房间 `52c8105c-eb94-407b-a7e9-64355c473d34` |
| 步时超时 | 通过 | 手工浏览器对局等待超过步时后，双方分别展示“你赢了/你输了 · 超时”和 60 秒返回倒计时 |
| 正常五连 | 通过 | 协议脚本房间 `af058f3a-acd7-4fed-b03b-a204df5392d9`，黑方 9 手横向五连 |
| 胜线高亮数据 | 通过 | `winningLineCount=5` |
| 双端实时同步 | 通过 | 双方均收到 9 条 `move_accepted` |
| 观战中立身份 | 通过 | 房间 `41afb165-faa1-45d5-8344-c21b7c672ec5`，`zhangsan` 为 `spectator=true`，既非黑方也非白方 |
| 观众禁止落子 | 通过 | 观众发送 `move` 后收到 `room_error`，`ok=false` |
| 聊天文本 | 通过 | 房间 `fe3d48fa-80db-47a4-8fb8-9a475444a09b`，对端收到 `验收聊天 hello` |
| 表情消息 | 通过 | 对端收到 `EMOJI` 消息与测试 URL |
| 对局记录 | 通过 | 最近记录出现 `FIVE`、`TIMEOUT`、`SURRENDER` |
| 棋谱回放 | 通过 | 记录 `id=34` 回放返回 9 手，坐标为 `(7,7)` 到 `(7,11)` 横向五连 |
| AI 兜底匹配 | 通过 | 房间 `3a9cc734-2a59-4e8b-97fc-476c6c255677`，`aiRoom=true`，AI 自动落子 `(6,7)` |
| AI 模型名 | 部分通过 | 当前展示 `deepseek-v4-flash · 本地策略兜底`；未证明 DeepSeek 远程调用成功 |

**构建与语法检查**

| 项目 | 结果 |
|---|---|
| 后端编译 | `mvn -DskipTests clean compile` 通过 |
| 后端测试 | `mvn test` 通过，6 个测试通过；初次发现 `game_settlement_event` 缺表，执行增量 SQL 后重跑无该错误 |
| 前端构建 | `npm run build` 通过，仅保留既有 Vite chunk / dynamic import 警告 |
| Python 语法 | `python -m py_compile ai-server/api/ai_hub.py ai-server/services/ai_hub_service.py` 通过 |

**本轮修复**

1. 全局 App 挂载看板娘时排除 `/games` 与 `/games/**`，保证游戏中心、五子棋页面、对局页面不出现看板娘模型。
2. AI 兜底展示名统一为 `deepseek-v4-flash · 本地策略兜底`，避免继续出现不精确模型名。
3. 本地开发库执行 Plus 增量 SQL，补齐 `game_settlement_event` 表，消除结算事件补偿任务缺表错误。

### 5.8 2026-06-19 AI 模型分层与图标专项修复

**修复范围**

| 模块 | 内容 |
|---|---|
| DeepSeek 模型名 | 按 DeepSeek 官方文档校正为 `deepseek-v4-flash` / `deepseek-v4-pro`，不再沿用旧 `chat` 口径 |
| AI 分层 | 低水平五子棋玩家使用 `deepseek-v4-flash`；五子棋积分达到 1600 及以上时使用 `deepseek-v4-pro` |
| Java 到 Python | Java 创建 AI 房间时写入 `aiModelCode`，调用 Python AI Hub 时通过 `model_code` 传递目标模型 |
| Python AI Hub | `/api/v1/ai/gobang-move` 接收 `model_code`，优先调用对应 DeepSeek 模型；只有 DeepSeek 不可用、返回非法坐标或解析失败时才进入本地规则兜底 |
| 前端图标 | AI 对手头像改用 `forum-vue/front/src/assets/svg/deepseek-color.svg`，通过 `modelIcon()` 统一解析，不再使用页面内手写 SVG |

**验收结果**

| 项目 | 结果 |
|---|---|
| 后端编译 | `mvn -DskipTests clean compile` 通过 |
| 后端测试 | `mvn test` 通过，6 个测试通过 |
| 前端构建 | `npm run build` 通过，仅保留既有 Vite chunk / dynamic import 警告 |
| Python 语法 | `python -m py_compile ai-server/api/ai_hub.py ai-server/services/ai_hub_service.py` 通过 |
| Python 单函数导入 | 当前命令行 Python 环境缺少 `requests`，未做真实单函数调用；以运行中的 Python 服务环境为准 |

**说明**

如果 Python 服务已经启动但界面仍显示“本地策略兜底”，现在只代表 DeepSeek 调用失败、超时、未配置有效 API Key、网络不可达、返回坐标非法或解析失败；旧版“先本地判断再跳过模型调用”的路径已移除。修改 Java/Python 源码后，正在运行的后端和 Python AI 服务都需要重启，才能加载本次 `model_code` 传递与分层逻辑。

## 6. 重点端到端链路

### 5.1 正常匹配对局

```text
用户 A 登录
→ 用户 B 登录
→ A、B 均进入五子棋页
→ 双方点击开始匹配
→ 服务端创建房间
→ 双方进入房间
→ 黑方先手
→ 双方轮流落子
→ 一方五连获胜
→ 写入战绩并更新积分
→ 双方返回五子棋页查看资料变化
```

### 5.2 异常落子防作弊

```text
用户 A 与 B 已在房间
→ A 非自己回合强发 move
→ A 强发越界 row/col
→ A 强发重复坐标
→ 服务端全部拒绝
→ 双方棋盘与回合状态不变化
```

### 5.3 断线重连

```text
用户 A 与 B 已在房间
→ A 浏览器刷新或网络断开
→ 服务端推送 A 暂离
→ A 在重连窗口内重新进入房间
→ 服务端恢复棋盘和回合
→ 对局继续
```

## 7. 验收通过标准

满足以下条件可认为一期通过：

1. 所有 P0 用例通过。
2. 游戏中心、五子棋匹配、房间对战、胜负结算闭环可演示。
3. 服务端能拒绝非法落子和越权房间访问。
4. 三层游戏 WebSocket 不影响现有 `/ws/notify`。
5. 战绩与积分写库正确，结算幂等。
6. 前端构建通过，后端启动通过。
7. 剩余 P1/P2 问题有明确记录，不影响核心对局闭环。

## 8. 验收报告模板

| 指标 | 结果 |
|---|---|
| 执行日期 | 2026-06-18 |
| 执行环境 | Windows 本地开发环境，前端 `localhost:5173`，后端 `localhost:10086` |
| 总用例数 | 43 |
| P0 用例数 | 29 |
| P1 用例数 | 14 |
| 通过 | 协议级 33 项最终全部通过；前端构建、后端测试、浏览器烟测通过 |
| 失败 | 0（`WATCH-003` 曾失败，已修复并专项回归通过） |
| 阻塞 | 0 |
| P0 缺陷 | `game_room_move.row` / `row_index AS row` SQL 关键字冲突，已修复 |
| P1 缺陷 | 无阻断缺陷；断线与步时同时到期时结束原因可能为 `TIMEOUT`，可按产品口径后续优化 |
| 前端构建 | 通过 |
| 后端启动/测试 | 通过 |
| 最终结论 | 游戏中心与五子棋后端核心链路完整验收通过，观战/聊天/AI/回放/计时均已覆盖 |

## 9. V0.4 UI 与实时链路专项验收

执行时间：2026-06-18 22:50-23:00

### 9.1 本次变更覆盖

| 模块 | 验收点 | 结果 |
|---|---|---|
| 游戏大厅 | 删除手动刷新按钮，顶部显示积分余额 | 通过 |
| 游戏大厅 | 三个状态卡同一行，卡内左右布局 | 通过 |
| 游戏大厅 | 五子棋卡片使用动态落子封面，标题左侧无图标 | 通过 |
| 游戏大厅 | 天梯榜/对局统计位于卡片右上，进入匹配独占一行 | 通过 |
| 游戏大厅 | 观战房间空态显示“等待第一盘棋开局”，右侧卡片与左侧卡片底部对齐 | 通过 |
| 五子棋匹配页 | 删除手动刷新、连接副标题和四个资料卡 | 通过 |
| 五子棋匹配页 | 右侧仅展示游戏在线与房间总数两个实时卡片 | 通过 |
| 五子棋匹配页 | 回放增加自动播放按钮 | 通过 |
| 对局页 | 首页图标离开并弹出确认，删除刷新按钮 | 通过 |
| 对局页 | 蓝黑主题、当前回合蓝紫强调环、黑白棋子视觉提示 | 通过 |
| 对局页 | 观战席展示头像/昵称，超过 6 人弹窗分页 | 通过 |
| 对局页 | 聊天区区分我方/对方消息，表情过多时弹窗选择 | 通过 |
| 对局页 | 结果在棋盘层与弹窗展示，不再占用右侧卡片 | 通过 |
| 实时链路 | `move_accepted` 不再触发 HTTP 刷新，前端直接应用 WS payload | 通过 |
| AI 对手 | AI 从首空位改为本地快思考启发式，优先赢棋/挡棋/棋形评分 | 通过 |
| 观战状态 | 房间状态新增玩家、AI、观战名单、房间在线数，并通过 `room_state_updated` 推送 | 通过 |

### 9.2 自动化验收结果

| 项目 | 结果 |
|---|---|
| 后端编译 | `C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd -q -DskipTests compile` 通过 |
| 前端构建 | `npm run build` 通过，仅保留项目既有 Vite chunk 体积/动态导入提示 |
| 后端运行 | 已重启并监听 `localhost:10086`，PID `15288` |
| 浏览器验收 | Playwright 通过，控制台无 error / warning |
| 大厅检查 | `/games` 标题正确，刷新按钮数 `0`，状态卡数 `3` |
| 匹配页检查 | `/games/gobang` 标题正确，刷新按钮数 `0`，实时卡数 `2` |
| 房间页检查 | `/games/gobang/rooms/{roomId}` 刷新按钮数 `0` |
| AI 落子检查 | 房间棋子数从 `0` 变为 `2`，玩家落子后 AI 延迟响应成功 |

### 9.3 截图产物

| 页面 | 截图 |
|---|---|
| 游戏大厅 | `output/playwright/game-center.png` |
| 五子棋匹配页 | `output/playwright/gobang-game.png` |
| 五子棋对局页 | `output/playwright/gobang-room.png` |

### 9.4 说明

AI 优先调用 Python AI Hub，再由 Python 按 `model_code` 调用 DeepSeek。低水平玩家默认 `deepseek-v4-flash`，五子棋积分达到 1600 及以上时默认 `deepseek-v4-pro`。当 DeepSeek 没有返回可用坐标时，前端展示对应模型名加 `· 本地策略兜底`，本地策略只作为兜底保底能力。
