# 6 人小聊天室系统 PRD

> 文档版本：V0.1
>
> 当前状态：规划中
>
> 适用范围：论坛聊天室、文字聊天、6 人语音小房间、麦位状态管理。

## 1. 背景

论坛后续计划新增聊天室能力。聊天室不是群聊，也不是直播间；它更像论坛里的 6 人小茶馆，强调小范围实时聊天和语音互动。

考虑服务器资源有限，聊天室必须按小房间设计，不做大房间语音社区。

本需求只定义聊天室第一阶段能力，不实现代码。

## 2. 产品定位

聊天室定位：

- 小房间。
- 轻社交。
- 文字 + 语音。
- 最多 6 人。
- 不做大规模围观。

聊天室不等于群聊：

| 能力 | 聊天室 | 群聊 |
|---|---|---|
| 核心关系 | 临时房间在线互动 | 长期成员关系 |
| 人数 | 最多 6 人 | 后续可更大 |
| 重点 | 在线氛围、上麦、语音 | 群成员、群消息、群管理 |
| 消息历史 | 保留近期历史即可 | 需要长期会话历史 |
| 是否需要观众席 | 否 | 否 |

## 3. P0 目标

- 用户可以创建聊天室。
- 用户可以进入聊天室。
- 用户可以退出聊天室。
- 每个聊天室最多 6 个在线用户。
- 房间内支持文字消息。
- 房间内展示 6 个座位。
- 进入房间默认闭麦。
- 用户可以上麦、下麦、闭麦、开麦。
- 房主可以踢人下麦、禁言、关闭房间。
- 支持 6 人以内基础语音聊天。
- 房间满员时提示“房间已满”。

## 4. P0 非目标

- 不做超过 6 人的聊天室。
- 不做观众席。
- 不做旁听大房间。
- 不做直播间。
- 不做视频聊天。
- 不做屏幕共享。
- 不做礼物打赏。
- 不做连麦 PK。
- 不做录音回放。
- 不做房间推荐算法。
- 不做房间等级体系。
- 不做付费房间。
- 不引入复杂语音服务架构。

## 5. 硬约束

### 5.1 人数约束

- 每个聊天室最多 6 个在线用户。
- 在线人数达到 6 人后，其他用户不能进入。
- P0 不提供观众身份，也不允许第 7 人旁听。

### 5.2 麦位约束

- 每个聊天室固定 6 个座位。
- 6 个座位都可以上麦。
- 用户进入房间时默认占一个座位，但默认闭麦。
- 用户离开页面时自动退出房间并释放座位。
- 用户断线超过 30 秒自动退出房间并释放座位。
- 同一用户同一时间只能进入一个聊天室。

### 5.3 房主约束

P0 建议：

- 创建者为房主。
- 房主关闭页面或主动离开时，聊天室直接关闭。
- 房间关闭后所有人退出。

暂不做房主转让，避免 P0 复杂化。

## 6. 技术方案

### 6.1 文字与房间状态

文字消息、房间状态、麦位变化走后端 WebSocket。

后端负责：

- 鉴权。
- 校验房间人数。
- 校验用户是否在房间。
- 维护在线成员。
- 维护麦位状态。
- 广播文字消息。
- 广播房间状态变化。

### 6.2 语音

语音流不走 Java WebSocket。

P0 建议使用 WebRTC 点对点 mesh：

- 浏览器之间直接传输语音流。
- 后端 WebSocket 只负责 WebRTC 信令交换。
- 房间最多 6 人，mesh 复杂度可控。
- 不引入 LiveKit / mediasoup / Janus。

后续如果房间人数上限变大，再考虑 SFU 服务；但当前规划固定 6 人，不需要上 SFU。

### 6.3 音频限制

- 只做音频，不做视频。
- 使用浏览器默认 Opus 编码。
- 前端尽量限制低码率。
- 离开页面、断线、房间关闭时必须关闭本地麦克风轨道。

## 7. 数据模型建议

### 7.1 MySQL 表

建议至少新增 3 张业务表。

| 表 | 作用 |
|---|---|
| `chat_room` | 聊天室基础信息 |
| `chat_room_member` | 房间成员与角色 |
| `chat_room_message` | 房间文字消息 |

### 7.2 chat_room

建议字段：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `room_name` | 房间名 |
| `owner_user_id` | 房主用户 ID |
| `cover_url` | 房间封面，可选 |
| `status` | 房间状态：OPEN / CLOSED |
| `max_user_count` | 最大人数，固定 6 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |
| `delete_state` | 逻辑删除 |

### 7.3 chat_room_member

建议字段：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `room_id` | 房间 ID |
| `user_id` | 用户 ID |
| `role` | OWNER / MEMBER |
| `mute_state` | 是否被禁言 |
| `join_time` | 加入时间 |
| `leave_time` | 离开时间 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |
| `delete_state` | 逻辑删除 |

### 7.4 chat_room_message

建议字段：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `room_id` | 房间 ID |
| `sender_user_id` | 发送者用户 ID |
| `message_type` | TEXT |
| `content` | 文本内容 |
| `status` | NORMAL / RECALLED |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |
| `delete_state` | 逻辑删除 |

P0 只做文本消息，不做图片、文件、语音消息落库。

### 7.5 Redis 状态

麦位、在线、信令等短生命周期状态建议放 Redis。

```text
chatroom:online:{roomId}
chatroom:seat:{roomId}
chatroom:mute:{roomId}
chatroom:signal:{roomId}
chatroom:user-room:{userId}
```

Redis 只做实时状态，不作为最终历史数据源。

## 8. HTTP 接口建议

| 接口 | 方法 | 说明 |
|---|---|---|
| `/chat-room/list` | GET | 聊天室列表 |
| `/chat-room/create` | POST | 创建聊天室 |
| `/chat-room/{roomId}` | GET | 房间详情 |
| `/chat-room/{roomId}/join` | POST | 加入房间 |
| `/chat-room/{roomId}/leave` | POST | 离开房间 |
| `/chat-room/{roomId}/close` | POST | 房主关闭房间 |
| `/chat-room/{roomId}/messages` | GET | 最近文字消息 |

说明：

- 实时文字发送不走 HTTP，走 WebSocket。
- HTTP 只负责列表、详情、进入、退出、关闭、历史兜底查询。

## 9. WebSocket 设计

### 9.1 路径

```text
/ws/chat-rooms/{roomId}
```

### 9.2 客户端发送

| type | data | 说明 |
|---|---|---|
| `ping` | `null` | 心跳 |
| `chat` | `{ content }` | 发送文字 |
| `mute_self` | `{ muted }` | 自己闭麦 / 开麦 |
| `leave_seat` | `null` | 下麦 |
| `kick_seat` | `{ targetUserId }` | 房主踢下麦 |
| `mute_user` | `{ targetUserId, muted }` | 房主禁言 |
| `webrtc_offer` | `{ targetUserId, sdp }` | WebRTC offer |
| `webrtc_answer` | `{ targetUserId, sdp }` | WebRTC answer |
| `webrtc_ice` | `{ targetUserId, candidate }` | ICE candidate |

### 9.3 服务端返回

| type | data | 说明 |
|---|---|---|
| `room_ready` | `ChatRoomStateVO` | 连接成功 |
| `room_state_updated` | `ChatRoomStateVO` | 房间状态变化 |
| `room_chat` | `ChatRoomMessageVO` | 房间文字消息 |
| `seat_updated` | `ChatRoomSeatVO` | 麦位变化 |
| `user_joined` | `ChatRoomMemberVO` | 用户进入 |
| `user_left` | `{ userId }` | 用户离开 |
| `room_closed` | `null` | 房间关闭 |
| `webrtc_signal` | `object` | 转发 WebRTC 信令 |
| `room_error` | `message` | 错误提示 |

## 10. 状态规则

### 10.1 房间状态

```text
OPEN
CLOSED
```

### 10.2 成员角色

```text
OWNER
MEMBER
```

### 10.3 麦克风状态

```text
MUTED
UNMUTED
DISCONNECTED
```

### 10.4 进入房间

必须满足：

- 用户已登录。
- 房间存在。
- 房间状态为 OPEN。
- 房间在线人数小于 6。
- 用户当前不在其他聊天室。
- 用户未被全局禁言或封禁。

### 10.5 退出房间

退出时必须：

- 移除在线状态。
- 释放座位。
- 广播用户离开。
- 关闭本地语音连接。

房主退出时：

- P0 直接关闭房间。
- 广播 `room_closed`。
- 清理房间 Redis 状态。

## 11. 前端页面

### 11.1 聊天室列表页

展示：

- 房间名。
- 房主头像昵称。
- 当前人数，例如 `4/6`。
- 房间状态。
- 创建房间按钮。

### 11.2 聊天室房间页

展示：

- 6 个座位。
- 当前用户麦克风状态。
- 开麦 / 闭麦按钮。
- 退出房间按钮。
- 文字聊天区。
- 成员状态。
- 房主操作入口。

交互约束：

- 房间满员时不能进入。
- 麦克风授权失败时只允许文字聊天。
- 用户离开页面时自动退出房间。
- WebRTC 连接失败时显示可重试提示。

## 12. 阶段规划

### P0：6 人小聊天室

- 创建房间。
- 加入 / 退出房间。
- 最多 6 人。
- 文字聊天。
- 6 个麦位。
- WebRTC mesh 语音。
- 房主关闭房间。
- 断线 30 秒释放座位。

### P1：轻量管理

- 房主禁言。
- 房主踢人。
- 房间封面。
- 私密房间邀请码。
- 最近房间列表。

### P2：体验增强

- 房主转让。
- 管理员角色。
- 房间热度排序。
- 最近发言预览。
- 与群聊系统做入口联动。

## 13. 验收标准

- 用户可以创建聊天室。
- 第 1 到第 6 个用户可以进入同一聊天室。
- 第 7 个用户进入时收到“房间已满”提示。
- 房间内文字消息能实时广播。
- 6 个座位状态能正确展示。
- 用户默认闭麦，手动开麦后其他用户能听到。
- 用户关闭页面后能自动退出并释放座位。
- 用户断线超过 30 秒后能释放座位。
- 房主关闭房间后所有用户退出。
- 语音流不经过 Java WebSocket。
- 不存在观众席、视频、屏幕共享入口。
