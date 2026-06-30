# 坦克大战在线 PK PRD

## 1. 功能定位

坦克大战是游戏中心的轻量实时 1v1 对战游戏。

P0 阶段目标是做出稳定、可结算、可接入排位的在线 PK，不做多人混战和复杂物理系统。

## 2. P0 目标

P0 必须支持：

- 1v1 在线匹配。
- 固定地图对战。
- 玩家移动、转向、开炮。
- 子弹命中扣血。
- 墙体阻挡移动和子弹。
- 倒计时开局。
- 对局时间限制。
- 胜负、平局、认输、断线逃跑结算。
- 房间内文字聊天。
- 可复用 PK 语音聊天能力。
- 可接入游戏排位系统。

## 3. 玩法规则

### 3.1 基础参数

| 项目 | P0 建议值 |
| --- | ---: |
| 对局人数 | 2 |
| 地图尺寸 | 20 x 12 网格 |
| 对局时长 | 180 秒 |
| 开局倒计时 | 3 秒 |
| 初始血量 | 3 |
| 命中伤害 | 1 |
| 开炮冷却 | 600ms |
| 单玩家同时子弹数 | 1 到 2 发 |
| 断线等待 | 10 秒 |

### 3.2 地图元素

P0 只保留两类地图元素：

- 空地：坦克和子弹可通过。
- 墙体：坦克不可通过，子弹命中后消失。

P0 不做草丛、水域、基地、道具、可破坏墙体。

### 3.3 胜负规则

- 任意玩家血量归零，对方获胜。
- 主动认输，认输方失败。
- 断线超过 10 秒未重连，断线方按逃跑失败处理。
- 对局时间结束后，剩余血量高者获胜。
- 剩余血量相同，命中次数高者获胜。
- 剩余血量和命中次数都相同，判定为平局。

## 4. 前端设计

前端负责画面渲染、操作采集、输入上报和本地表现，不负责最终判定。

### 4.1 页面结构

建议新增：

- TankPkRoom.vue
- TankPkRoom.js
- TankPkRoom.scss

页面包含：

- Canvas 游戏画布。
- 玩家血量。
- 对局倒计时。
- 连接状态。
- 结算弹窗。
- 房间文字聊天。
- 麦克风开关。
- 接收语音开关。

### 4.2 游戏脚本结构

建议新增：

- src/scripts/games/tank/constants.js
- src/scripts/games/tank/canvas.js
- src/scripts/games/tank/renderer.js
- src/scripts/games/tank/input.js
- src/scripts/games/tank/interpolator.js

职责划分：

- constants：地图尺寸、颜色、资源、方向枚举。
- canvas：画布初始化和尺寸适配。
- renderer：地图、坦克、子弹、命中特效绘制。
- input：键盘和按钮输入监听。
- interpolator：根据服务端 state_tick 做插值渲染。

### 4.3 前端上报原则

前端只上报输入意图，不上报坐标和血量。

允许上报：

- 移动方向。
- 停止移动。
- 开炮。
- 认输。
- 聊天消息。
- 语音信令。

禁止以上报结果作为结算依据：

- 坦克坐标。
- 子弹坐标。
- 命中结果。
- 血量变化。
- 胜负结果。

## 5. 后端设计

后端负责房间状态、游戏循环、碰撞、命中、胜负和结算。

### 5.1 WebSocket 路径

复用现有游戏中心 WebSocket 模式：

- 游戏级匹配：`/ws/games/tank`
- 房间级对战：`/ws/games/tank/rooms/{roomId}`

### 5.2 后端模块方向

建议新增：

- TankGameWebSocketHandler
- TankRoomWebSocketHandler
- TankMatchService
- TankRoomService
- TankRoom
- TankPlayerState
- TankBulletState
- TankMap
- TankRuleEngine

职责划分：

- TankGameWebSocketHandler：处理开始匹配、取消匹配。
- TankRoomWebSocketHandler：处理加入房间、输入、聊天、认输、重连。
- TankMatchService：管理匹配队列，创建 1v1 房间。
- TankRoomService：维护房间主流程、状态推进、结算。
- TankRuleEngine：处理移动、碰撞、命中、胜负判断。

### 5.3 状态存储

P0 房间实时状态保存在 JVM 内存中。

MySQL 只保存：

- 对局结果。
- 胜负记录。
- 排位分变化。
- 必要的对局统计。

Redis 只用于：

- 匹配队列。
- 在线状态。
- 低频房间快照。
- 多实例下的低频房间事件通知。

P0 不允许每个 tick 写 MySQL 或 Redis。

## 6. WebSocket 消息

### 6.1 匹配消息

客户端发送：

```json
{ "type": "start_match", "requestId": "start_match-1", "data": null }
```

```json
{ "type": "stop_match", "requestId": "stop_match-1", "data": null }
```

服务端返回：

```json
{
  "type": "match_success",
  "data": {
    "roomId": "tank_10001",
    "side": "A"
  }
}
```

### 6.2 房间初始化

服务端返回：

```json
{
  "type": "room_init",
  "data": {
    "roomId": "tank_10001",
    "mapId": "classic_01",
    "countdownSeconds": 3,
    "players": []
  }
}
```

### 6.3 输入消息

客户端发送：

```json
{
  "type": "input_move",
  "data": {
    "direction": "UP",
    "seq": 101
  }
}
```

```json
{
  "type": "input_stop",
  "data": {
    "seq": 102
  }
}
```

```json
{
  "type": "fire",
  "data": {
    "seq": 103
  }
}
```

### 6.4 状态广播

服务端按固定 tick 广播轻量状态：

```json
{
  "type": "state_tick",
  "data": {
    "tick": 238,
    "players": [
      { "userId": 1, "x": 3, "y": 8, "dir": "UP", "hp": 2 },
      { "userId": 2, "x": 16, "y": 8, "dir": "LEFT", "hp": 1 }
    ],
    "bullets": [
      { "id": 11, "x": 7, "y": 8, "dir": "RIGHT" }
    ],
    "events": ["hit"]
  }
}
```

### 6.5 结算消息

```json
{
  "type": "game_over",
  "data": {
    "result": "WIN",
    "winnerUserId": 1,
    "reason": "HP_ZERO",
    "rankScoreDelta": 12
  }
}
```

## 7. 服务端 Tick 设计

P0 使用低频固定 tick。

- 服务端 tick：10 tick/s。
- 每 100ms 推进一次房间状态。
- 前端渲染：requestAnimationFrame 60fps。
- 前端通过插值平滑服务端状态。

每个 tick 做：

- 读取两名玩家当前输入状态。
- 推进坦克位置。
- 校验地图碰撞。
- 校验开炮冷却。
- 推进子弹位置。
- 判断子弹碰撞墙体或玩家。
- 更新血量和命中次数。
- 判断是否结束。
- 广播 state_tick。

## 8. 服务器压力控制

P0 必须优先控制服务器压力。

### 8.1 限制玩法规模

- 只做 1v1。
- 不做观战。
- 不做多人混战。
- 不做高频道具。
- 不做复杂物理。

### 8.2 限制网络频率

- 客户端只在输入变化时发消息。
- 客户端不能按帧发送位置。
- 服务端状态广播 10 tick/s。
- 聊天、认输、语音信令和游戏状态分开处理。

### 8.3 限制状态大小

- state_tick 只包含坦克、子弹和必要事件。
- 不在 state_tick 中发送完整用户资料。
- 地图只在 room_init 下发一次。
- 静态资源由前端本地维护。

### 8.4 限制持久化压力

- 不每 tick 写 MySQL。
- 不每 tick 写 Redis。
- Redis 快照低频保存，只用于断线恢复。
- MySQL 仅在结算时写入结果。

### 8.5 限制并发规模

P0 可设置系统级开关：

- 最大同时房间数。
- 最大匹配队列人数。
- 单用户只能处于一个匹配或房间中。
- 房间结束后立即释放内存。

建议初始值：

- 最大同时房间数：50 到 100。
- 最大匹配队列人数：200。

## 9. 断线与重连

- 玩家断线后，房间保留 10 秒。
- 10 秒内重连，恢复当前房间状态。
- 10 秒未重连，断线方判逃跑失败。
- 对手在等待期间停留在房间内，不能继续伤害断线玩家。
- 对局结束后，断线玩家重连只能看到结算结果。

## 10. 排位接入

坦克大战属于可接入排位的 PK 游戏。

结算时输出：

- gameCode = TANK
- winnerUserId
- loserUserId
- result
- finishReason
- durationSeconds
- hitCount
- escapeUserId

排位规则复用游戏排位系统：

- 正常胜利加分。
- 正常失败扣分。
- 平局不加不扣或极少变动。
- 主动认输按失败处理。
- 断线超时按逃跑失败处理。

异常短局、重复匹配刷分等风控由排位系统统一处理。

## 11. PK 语音接入

坦克大战房间可复用 PK 语音聊天 PRD。

- 麦克风开关控制是否发送自己的声音。
- 接收语音开关只控制本地是否播放对方声音。
- 语音使用 WebRTC P2P。
- Java WebSocket 只转发语音信令。
- 语音流不经过后端转发，不写入数据库。

## 12. P1 增强

- 新增更多固定地图。
- 加入简单道具。
- 加入可破坏墙体。
- 加入观战。
- 加入战绩详情。
- 加入坦克外观皮肤，但不提供属性加成。

## 13. P2 增强

- 自定义房间。
- 好友邀请。
- 训练模式。
- AI 机器人。
- 回放。
- 更完整的地图编辑器。

## 14. 明确不做

P0 不做：

- 多人混战。
- 大地图。
- 复杂物理引擎。
- 战争迷雾。
- 草丛隐藏。
- 基地守护模式。
- AI 补位。
- 自定义地图。
- 观战。
- 回放。
- 皮肤属性加成。
- 服务端转发语音流。

## 15. 验收标准

- 两名玩家可以通过匹配进入同一房间。
- 房间能正常倒计时开局。
- 玩家可以移动、停止、转向、开炮。
- 墙体能阻挡坦克和子弹。
- 子弹命中玩家后能扣血。
- 血量归零能正确结算胜负。
- 时间结束能按血量和命中次数结算。
- 主动认输能正确结算。
- 断线 10 秒未重连能按逃跑结算。
- 前端不能通过上报坐标、血量或胜负影响结算。
- 对局结束后只写入一次结算记录。
- 服务端不会每 tick 写 MySQL 或 Redis。
