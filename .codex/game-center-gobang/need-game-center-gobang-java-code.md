# 萌部落论坛游戏中心与五子棋 Java 端设计

> 文档版本：V0.1
>
> 编写日期：2026-06-18
>
> 实现必须遵守当前论坛后端分层、JWT 鉴权、统一返回结构和现有 WebSocket 风格。

## 1. 实现原则

- 保持现有 `/ws/notify` 不变。
- 新增游戏 WebSocket 模块，不能复用通知在线表覆盖用户通知连接。
- Controller 只处理 HTTP 查询和管理类动作；实时动作通过 WebSocket handler。
- 服务端是五子棋权威状态源，前端提交坐标，后端判定合法性和胜负。
- 一期以单机内存房间为主，生产多实例通过 Nginx `/ws/` 粘滞降低跨实例房间问题；后续再演进 Redis 房间状态。
- 关键结算写 MySQL，临时匹配队列和棋盘状态可放内存。

## 2. 建议包结构

```text
org.example.forumdemo.common.config
  WebSocketConfigure.java              # 扩展注册游戏 WS handler

org.example.forumdemo.common.websocket.game
  GameHandshakeContext.java
  GameConnectionRegistry.java
  GameWsMessage.java
  GameWsMessageType.java
  GameWsResponse.java

org.example.forumdemo.common.websocket.game.handler
  GameCenterLobbyWebSocketHandler.java
  GobangGameWebSocketHandler.java
  GobangRoomWebSocketHandler.java

org.example.forumdemo.entity.db
  GameDefinition.java
  GameUserProfile.java
  GameMatchRecord.java

org.example.forumdemo.entity.dto.game
  GobangMoveRequest.java
  GameMatchRequest.java
  GameRoomActionRequest.java

org.example.forumdemo.entity.vo.game
  GameCenterOverviewVO.java
  GameUserProfileVO.java
  GameMatchRecordVO.java
  GobangRoomStateVO.java

org.example.forumdemo.service.interfaces.game
  GameCenterService.java
  GameUserProfileService.java
  GobangMatchService.java
  GobangRoomService.java

org.example.forumdemo.service.impl.game
  GameCenterServiceImpl.java
  GameUserProfileServiceImpl.java
  GobangMatchServiceImpl.java
  GobangRoomServiceImpl.java
  GobangRuleEngine.java
```

## 3. WebSocket 注册

在现有 `WebSocketConfigure` 中新增 handler：

```text
/ws/game-center/lobby
/ws/games/gobang
/ws/games/gobang/rooms/*
```

- 三个路径全部添加 `TokenHandshakeInterceptor`。
- `setAllowedOrigins("*")` 与现有项目保持一致。
- 前端连接均通过 query token。
- Nginx 当前 `location /ws/` 已可代理，无需新增 location；生产保持 `forum_backend_ws` 粘滞。

## 4. 连接注册表

新增 `GameConnectionRegistry`，维护三类连接：

```text
ConcurrentHashMap<Long, WebSocketSession> lobbySessions
ConcurrentHashMap<String, ConcurrentHashMap<Long, WebSocketSession>> gameSessions
ConcurrentHashMap<String, ConcurrentHashMap<Long, WebSocketSession>> roomSessions
ConcurrentHashMap<String, Long> sessionUserIndex
ConcurrentHashMap<String, GameConnectionKind> sessionKindIndex
```

核心方法：

- `enterLobby(userId, session)`。
- `exitLobby(userId, session)`。
- `enterGame(gameCode, userId, session)`。
- `exitGame(gameCode, userId, session)`。
- `enterRoom(roomId, userId, session)`。
- `exitRoom(roomId, userId, session)`。
- `sendToUserInGame(gameCode, userId, payload)`。
- `broadcastRoom(roomId, payload)`。
- `closePreviousSameChannel(userId, channelKey)`。

注意：

- 下线清理必须比较 session 引用，避免旧连接关闭时误删新连接。
- 心跳扫描按 sessionId 维度记录最后 ping 时间。

## 5. HTTP 接口

新增用户端接口前缀建议为 `/game`：

```text
GET /game/center/overview
GET /game/gobang/profile
GET /game/gobang/records
GET /game/gobang/rooms/{roomId}
POST /game/gobang/rooms/{roomId}/surrender
```

说明：

- `overview` 返回游戏卡片、在线状态、用户摘要。
- `profile` 返回五子棋积分与战绩。
- `records` 分页查询当前用户对局记录。
- `rooms/{roomId}` 用于刷新兜底或断线重连时拉取房间摘要。
- 认输也可走 WebSocket；HTTP 兜底接口用于页面异常时操作。

## 6. 数据库设计

### 6.1 game_definition

```text
id bigint pk
game_code varchar(64) unique       # gobang
game_name varchar(64)              # 五子棋
cover_url varchar(512)
status tinyint                     # 1 启用，0 停用
sort int
create_time datetime
update_time datetime
```

### 6.2 game_user_profile

```text
id bigint pk
user_id bigint
game_code varchar(64)
score int default 1000
total_count int default 0
win_count int default 0
lose_count int default 0
draw_count int default 0
current_status varchar(32)         # IDLE / MATCHING / PLAYING
current_room_id varchar(64)
create_time datetime
update_time datetime
unique(user_id, game_code)
```

### 6.3 game_match_record

```text
id bigint pk
game_code varchar(64)
room_id varchar(64)
black_user_id bigint
white_user_id bigint
winner_user_id bigint null
loser_user_id bigint null
end_reason varchar(32)             # FIVE / SURRENDER / DISCONNECT / TIMEOUT / ABNORMAL
score_delta int
started_at datetime
ended_at datetime
create_time datetime
```

一期不保存完整棋谱；后续复盘时新增 `game_gobang_move_record`。

## 7. 匹配服务设计

`GobangMatchService` 负责：

- 开始匹配。
- 取消匹配。
- 用户断线移出队列。
- 按积分分段匹配。
- 匹配成功创建内存房间。
- 通知两名玩家进入房间。

积分分段初始规则：

```text
0-1199
1200-1599
1600-1999
2000+
```

实现建议：

- 不直接照搬 demo 构造器启动死循环线程。
- 使用 `TaskExecutor` 或 `ScheduledExecutorService` 管理匹配 worker。
- 队列使用 `BlockingQueue` 或在 Service 中显式加锁。
- 每个用户入队前检查 `current_status`，防止重复入队。
- 取消匹配时同时清理内存队列与用户状态。

## 8. 房间服务设计

`GobangRoomService` 维护内存房间：

```text
roomId
blackUserId
whiteUserId
currentTurnUserId
int[15][15] board
startedAt
lastMoveAt
roomStatus
disconnectDeadlineByUser
```

核心动作：

- `createMatchedRoom(userA, userB)`。
- `joinRoom(roomId, userId, session)`。
- `handleMove(roomId, userId, row, col)`。
- `surrender(roomId, userId)`。
- `handleDisconnect(roomId, userId)`。
- `finishRoom(roomId, winnerId, endReason)`。

落子校验：

- 房间存在且状态为 `PLAYING`。
- 用户属于该房间。
- 当前轮到该用户。
- 坐标在 0-14。
- 目标位置为空。
- 通过后写棋盘、切换回合、广播。

胜负判定：

- 使用四条轴线扫描：横、竖、主对角线、副对角线。
- 连续同色棋子数大于等于 5 判胜。
- 一期不判断禁手。

## 9. WebSocket 消息协议

统一消息外壳：

```json
{
  "type": "start_match",
  "requestId": "uuid",
  "data": {}
}
```

统一响应：

```json
{
  "type": "match_started",
  "ok": true,
  "message": "",
  "data": {}
}
```

五子棋游戏连接消息：

```text
start_match
stop_match
match_started
match_stopped
match_success
match_failed
```

房间连接消息：

```text
room_ready
move
move_accepted
move_rejected
turn_changed
game_finished
surrender
peer_disconnected
peer_reconnected
error
ping
pong
```

## 10. 结算与事务

房间结束时：

1. 加锁保证只结算一次。
2. 写入 `game_match_record`。
3. 更新双方 `game_user_profile`。
4. 清理双方 `current_status/current_room_id`。
5. 广播 `game_finished`。
6. 移除内存房间。

结算必须幂等：

- 同一 `roomId` 只能写一条终局记录。
- 多个断线/认输/胜利事件同时触发时，只允许第一个成功。

## 11. 从 gobang-demo 迁移注意点

- `RoomManger` 命名可修正为 `RoomManager`。
- `Matcher` 中 highQueue / veryHighQueue 分支当前 `notify()` 写到了 `normalQueue`，迁移时必须修复。
- `Room` 不建议通过 `SpringBeanUtil` 主动拿 Bean；改由 Service 创建房间对象并注入依赖。
- `GameAPI` 中 user 对象比较使用引用比较不适合迁移；统一按 `userId` 比较。
- demo 的 `int userId` 改为论坛 `Long userId`。
- demo 静态 HTML 只作为交互参考，不直接并入 Vue 项目。

## 12. 核心测试

- JWT 握手缺失、过期、伪造 token 均拒绝。
- `/ws/notify` 与游戏连接互不影响。
- 同一用户同通道重复连接时旧连接关闭，新连接有效。
- 开始匹配、取消匹配、断线移出队列。
- 两个积分段相近用户可匹配成功。
- 房间双方 ready 后推送身份与先手。
- 非当前回合、越界、重复位置落子被拒绝。
- 横竖斜四方向五连均可判胜。
- 认输、断线、胜利只结算一次。
- MySQL 战绩与用户游戏资料更新正确。
