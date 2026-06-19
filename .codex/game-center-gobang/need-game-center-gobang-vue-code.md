# 萌部落论坛游戏中心与五子棋 Vue3 前端设计

> 文档版本：V0.1
>
> 编写日期：2026-06-18
>
> 前端实现需遵守当前 `forum-vue/front` 的 Vue3 + Vite 结构：`.vue` 负责模板，复杂脚本放 `src/scripts`，样式按现有组件习惯拆分。

## 1. 前端定位

- 游戏中心是用户端功能，第一期不进入 `forum-vue-admin`。
- 页面应保持论坛现有登录态、Header、整体布局的一致性，同时游戏区域可以拥有更强的沉浸感。
- 五子棋 UI 可参考 `gobang-demo` 的 `game_hall.html` 与 `game_room.html`，但需改写为 Vue 组件。
- WebSocket 连接独立于现有 `useWebSocket.js` 通知连接，避免互相 close。

## 2. 建议目录

```text
forum-vue/front/src/views/GameCenter.vue
forum-vue/front/src/views/GobangGame.vue
forum-vue/front/src/views/GobangRoom.vue

forum-vue/front/src/scripts/views/GameCenter.js
forum-vue/front/src/scripts/views/GobangGame.js
forum-vue/front/src/scripts/views/GobangRoom.js

forum-vue/front/src/components/game/GameCard.vue
forum-vue/front/src/components/game/GameProfilePanel.vue
forum-vue/front/src/components/game/GobangBoard.vue
forum-vue/front/src/components/game/GobangMatchPanel.vue
forum-vue/front/src/components/game/GobangResultDialog.vue

forum-vue/front/src/composables/useGameSocket.js
forum-vue/front/src/composables/useGobangRoomSocket.js

forum-vue/front/src/api/game.js
```

## 3. 路由设计

在用户端主布局下新增：

```text
/games
/games/gobang
/games/gobang/rooms/:roomId
```

- `/games`：游戏中心大厅。
- `/games/gobang`：五子棋游戏大厅与匹配页。
- `/games/gobang/rooms/:roomId`：五子棋房间页。
- 未登录访问时跳转登录，并带 `redirect`。

## 4. API 模块

`src/api/game.js`：

```text
getGameCenterOverview()
getGobangProfile()
getGobangRecords(params)
getGobangRoom(roomId)
surrenderGobangRoom(roomId)
```

- 所有 HTTP 请求沿用现有 request 封装。
- WebSocket 不走 axios，由 composable 自行构造连接 URL。

## 5. WebSocket Composable

### 5.1 useGameCenterSocket

职责：

- 连接 `/ws/game-center/lobby?token=xxx`。
- 接收游戏中心在线人数、游戏状态、当前用户游戏状态。
- 页面离开时关闭。

状态：

```text
connected
onlineCount
gameStatuses
currentPlayState
lastError
```

### 5.2 useGobangGameSocket

职责：

- 连接 `/ws/games/gobang?token=xxx`。
- 发送开始匹配、取消匹配。
- 接收匹配成功并跳转房间。

状态：

```text
connected
matching
matchSeconds
matchError
matchedRoomId
```

消息：

```text
start_match
stop_match
match_started
match_stopped
match_success
match_failed
```

### 5.3 useGobangRoomSocket

职责：

- 连接 `/ws/games/gobang/rooms/{roomId}?token=xxx`。
- 接收房间状态、双方身份、落子、回合、结束事件。
- 发送落子、认输、心跳。

状态：

```text
connected
roomReady
board
myUserId
opponentUserId
blackUserId
whiteUserId
currentTurnUserId
winnerUserId
endReason
disconnectNotice
```

## 6. 游戏中心页面

页面内容：

- 游戏中心标题与用户游戏摘要。
- 五子棋卡片：
  - 游戏名。
  - 简介。
  - 在线人数。
  - 当前状态：可玩 / 维护中。
  - 进入按钮。
- 后续游戏预留位可显示“敬请期待”。

交互：

- 点击五子棋进入 `/games/gobang`。
- 大厅 WebSocket 断开时，保留 HTTP 数据并显示离线提示。

## 7. 五子棋游戏页

页面内容：

- 玩家资料卡：
  - 头像、昵称。
  - 五子棋积分。
  - 胜率、总局数、胜负数。
- 匹配面板：
  - 未匹配：开始匹配按钮。
  - 匹配中：计时器、取消匹配按钮。
  - 匹配成功：进入房间过渡状态。
- 最近对局记录。

交互：

- 开始匹配时禁用重复点击。
- 取消匹配后恢复按钮。
- 匹配成功收到 `roomId` 后跳转 `/games/gobang/rooms/:roomId`。
- 页面离开时如果还在匹配中，发送 `stop_match`。

## 8. 五子棋房间页

页面内容：

- 15x15 棋盘。
- 双方玩家信息。
- 当前回合提示。
- 我方棋色提示。
- 认输按钮。
- 对局结果弹窗。

棋盘交互：

- 只有 `roomReady` 后允许点击。
- 只有轮到自己时允许点击。
- 已落子位置不可点击。
- 点击后先进入本地 pending 状态，等待服务端 `move_accepted` 后正式渲染。
- 服务端拒绝时撤销 pending 并提示原因。

断线与重连：

- 房间连接断开后显示重连中。
- 自动尝试重连，重连仍使用当前 `roomId`。
- 服务端返回房间已结束时展示结算结果。

## 9. UI 风格建议

- 游戏中心页面保持论坛整体温和社区感。
- 五子棋游戏页可以使用更强的游戏视觉，但避免大面积单一紫蓝渐变。
- 可参考本地已安装 skills：
  - `impeccable`：整体设计审查。
  - `taste-skill`：避免模板化游戏界面。
  - `awesome-design-md/design-md/playstation/DESIGN.md`：游戏氛围参考。
  - `awesome-design-md/design-md/raycast/DESIGN.md`：工具面板和快捷状态参考。
- 棋盘必须清晰，棋子黑白对比足够，移动端不能误触。

## 10. 前端状态边界

- 前端不得自行判胜作为最终结果。
- 前端可以做落子前置校验，但服务端拒绝时以后端为准。
- 前端不得信任路由中的 `roomId` 直接展示对局，必须等待服务端确认用户属于房间。
- 关闭房间页时不直接判负，判负由服务端断线策略决定。

## 11. 验证点

- 登录用户可进入游戏中心，未登录跳转登录。
- 大厅、游戏、房间三条连接互不影响通知 WebSocket。
- 开始匹配、取消匹配按钮状态正确。
- 匹配成功后正确跳转房间。
- 棋盘 15x15 渲染稳定，移动端不溢出。
- 非己方回合无法落子。
- 服务端拒绝落子时前端回滚 pending。
- 胜负弹窗展示正确，并可返回五子棋页。
- 页面离开时连接清理，不产生重复匹配。
