# 首页、搜索与游戏中心调整设计

## 目标

在不新增数据库字段、不改动认证方式和游戏核心结算流程的前提下，完成首页壳、看板娘、搜索结果、个人主页与游戏中心的 14 项界面及接口调整。

## 方案

采用现有结构上的定点改造：

- 首页壳继续由 `HomeTopBar`、`HomeSidebar`、`HomeFeed` 和 `useHomeShellContext` 协作，只移动入口和调整展示，不改变路由。
- 看板娘鼠标穿透仍由 `mascotUi` Pinia Store 持有，顶部入口删除，改由 `MascotDock` 对话框头部调用现有 Action。
- 搜索页拆出文章卡片和用户行组件；文章卡片复用首页问答贴语义，用户搜索响应增加专用 VO，批量提供关注数、粉丝数和当前关注状态。
- 游戏历史记录沿用已经存在的后端分页；俄罗斯方块与俄罗斯方块 PK 排行榜改成 `PageResult` 后端分页。五子棋、井字棋只移除排行榜入口、接口和排行 Service 方法，其余资料、历史、匹配、房间与回放保持不变。
- 俄罗斯方块 PK 排名按胜率降序；最高得分从现有 PK 对局记录计算，不新增表或字段。

## 组件边界

- `HomeTopBar`：顶部搜索、主题、设置、积分及登录态操作。
- `HomeSidebar`：左侧主导航和快速入口，承接消息中心入口与未读数。
- `HomeFeed`：分类板块导航、签到条和首页帖子流。
- `MascotDock`：看板娘舞台与对话框，头部仅展示看板娘状态和鼠标穿透开关。
- `UnifiedSearchFeed`：搜索状态编排、分页及空态。
- `SearchArticleCard`：普通贴/问答贴卡片展示。
- `SearchUserRow`：统一尺寸用户行、关注统计及关注按钮，通过事件通知父级执行关注操作。
- `GameCenter`：游戏中心数据编排、对局统计和排行榜对话框。

## 数据与接口

- `/search/user` 保持路径不变，分页记录改为搜索专用用户项，增加 `followingCount`、`followerCount`、`isFollowing`。
- `/game/tetris/leaderboard`、`/game/tetris/pk/leaderboard` 接收 `pageNum/pageSize`，返回 `PageResult`。
- 删除 `/game/gobang/leaderboard`、`/game/jinzi/leaderboard`。
- 四类 `/records` 接口保持现状，继续由后端分页。

## 风险控制

- 不记录或写入用户提供的账号密码；浏览器验证仅通过既有一次性票据流程。
- 不修改游戏结算、匹配、房间和数据库结构。
- 搜索关注操作沿用现有 `userFollow` API，并在提交期间禁用按钮。
- 修改后执行前端构建、后端编译与定向测试，再做桌面端浏览器回归。
