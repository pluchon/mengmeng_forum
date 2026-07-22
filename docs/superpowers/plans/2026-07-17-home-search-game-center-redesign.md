# Home Search Game Center Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成首页、搜索结果、看板娘、个人主页和游戏中心的 14 项桌面端调整，并让剩余游戏排行榜真正由后端分页。

**Architecture:** 在现有 Vue 3 页面壳和 Spring Boot Controller → Service → Mapper 链路上定点修改。搜索使用专用结果 VO 承载关注统计，游戏记录保留现有分页，两个俄罗斯方块排行榜改为 `PageResult`，PK 最高得分从既有记录聚合。

**Tech Stack:** Vue 3、Pinia、Element Plus、Vite、Spring Boot、MyBatis-Plus、JUnit 5。

## Global Constraints

- 不新增数据库表或字段，不改变认证、权限、游戏结算与匹配流程。
- 五子棋和井字棋只删除排行榜 UI/API/Service 排行逻辑。
- Vue 组件保持 `.vue` / `.js` / `.scss` 三文件结构，页面不直接调用 Axios。
- MyBatis-Plus 查询只使用 Lambda API；写操作继续由现有事务 Service 承担。
- 不把账号、密码、验证码或票据写入代码、文档和日志。

---

### Task 1: 首页壳、分类导航与签到条

**Files:**
- Modify: `forum-vue/front/src/components/layout/HomeTopBar.vue`
- Modify: `forum-vue/front/src/scripts/components/layout/HomeTopBar.js`
- Modify: `forum-vue/front/src/components/layout/HomeSidebar.vue`
- Modify: `forum-vue/front/src/scripts/components/layout/HomeSidebar.js`
- Modify: `forum-vue/front/src/views/HomeFeed.vue`
- Modify: `forum-vue/front/src/views/HomeFeed.js`
- Modify: `forum-vue/front/src/assets/styles/home.css`
- Test: `forum-vue/front/tests/home-shell-redesign.test.mjs`

**Interfaces:**
- Consumes: `useHomeShellContext().openMessageCenter`, `msgUnread`, `currentBoardId`, `checkinSummary`。
- Produces: `categoryTriggerLabel(item)`，返回当前分类按钮应显示的分类名或已选板块名。

- [ ] 写静态结构测试，断言未登录模板不再包含默认头像/积分分支、侧栏包含“消息中心”、签到条文案结构及分类标签函数存在。
- [ ] 运行 `node --test tests/home-shell-redesign.test.mjs`，确认因旧结构而失败。
- [ ] 修改顶部栏、侧栏、分类标签、单行签到条、顺时针边框动画和滚动条样式。
- [ ] 重新运行测试并确认通过。

### Task 2: 看板娘与个人主页

**Files:**
- Modify: `forum-vue/front/src/components/mascot/MascotDock.vue`
- Modify: `forum-vue/front/src/scripts/components/mascot/MascotDock.js`
- Modify: `forum-vue/front/src/assets/styles/mascot-dock.css`
- Modify: `forum-vue/front/src/views/Profile.vue`
- Modify: `forum-vue/front/src/scripts/views/Profile.js`
- Test: `forum-vue/front/tests/mascot-profile-redesign.test.mjs`

**Interfaces:**
- Consumes: `mascotUi.pointerPassThrough` 与 `mascotUi.togglePointerPassThrough()`。
- Produces: 对话框头部“看板娘鼠标穿透”开关；个人主页不再输出独立皇冠图片。

- [ ] 写结构测试并验证旧代码失败。
- [ ] 删除顶部用户头像/昵称与个人主页皇冠，接入现有 Store Action，补充可访问的 switch 状态。
- [ ] 运行结构测试确认通过。

### Task 3: 搜索结果卡片与关注数据

**Files:**
- Create: `forum-vue/front/src/components/search/SearchArticleCard.vue`
- Create: `forum-vue/front/src/components/search/SearchArticleCard.js`
- Create: `forum-vue/front/src/components/search/SearchArticleCard.scss`
- Create: `forum-vue/front/src/components/search/SearchUserRow.vue`
- Create: `forum-vue/front/src/components/search/SearchUserRow.js`
- Create: `forum-vue/front/src/components/search/SearchUserRow.scss`
- Modify: `forum-vue/front/src/views/UnifiedSearchFeed.vue`
- Modify: `forum-vue/front/src/scripts/views/UnifiedSearchFeed.js`
- Create: `forum-vue/front/src/views/UnifiedSearchFeed.scss`
- Create: `backend/src/main/java/org/example/forumdemo/entity/vo/search/SearchUserItemVO.java`
- Modify: `backend/src/main/java/org/example/forumdemo/entity/vo/search/SearchUserResponse.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/search/SearchService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/search/SearchServiceImpl.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/user/UserFollowService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/user/UserFollowServiceImpl.java`
- Test: `forum-vue/front/tests/unified-search-redesign.test.mjs`
- Test: `backend/src/test/java/org/example/forumdemo/service/impl/search/SearchUserResultTest.java`

**Interfaces:**
- Produces: `SearchUserItemVO(id, nickname, avatarUrl, vipTier, vipExpireAt, followingCount, followerCount, isFollowing)`；`SearchUserRow` emits `toggle-follow`。

- [ ] 先写前端结构测试与后端用户结果组装测试并分别确认失败。
- [ ] 新增专用搜索 VO 和批量关注统计组装，匿名搜索的 `isFollowing` 为 `false`。
- [ ] 拆分搜索卡片，复用 `articleQuestion` 工具展示问答状态/回答数，移除搜索提示文案。
- [ ] 接入关注/取消关注 API，提交期间禁用按钮并更新当前行。
- [ ] 运行定向前后端测试确认通过。

### Task 4: 游戏排行榜后端分页

**Files:**
- Modify: `backend/src/main/java/org/example/forumdemo/controller/GameController.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/game/GameUserProfileService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/game/TetrisService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/game/TetrisPkService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/game/GameUserProfileServiceImpl.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/game/TetrisServiceImpl.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/game/TetrisPkServiceImpl.java`
- Create: `backend/src/main/java/org/example/forumdemo/entity/vo/game/TetrisPkLeaderboardVO.java`
- Test: `backend/src/test/java/org/example/forumdemo/service/impl/game/GameLeaderboardPageTest.java`

**Interfaces:**
- Produces: `PageResult<TetrisProfileVO> listLeaderboard(Integer pageNum, Integer pageSize)`；`PageResult<TetrisPkLeaderboardVO> listLeaderboard(Integer pageNum, Integer pageSize)`。
- PK 排序键：胜率降序、最高得分降序、用户 ID 升序。

- [ ] 写分页边界和 PK 排序测试并确认失败。
- [ ] 删除五子棋/井字棋排行榜 Controller 与通用排行 Service 方法。
- [ ] 将单人俄罗斯方块排行榜改为 MyBatis-Plus `selectPage`。
- [ ] 用现有 PK 资料和对局记录在 Service 内计算胜率/最高得分并返回稳定分页结果，不新增字段。
- [ ] 运行游戏定向测试确认通过。

### Task 5: 游戏中心展示

**Files:**
- Modify: `forum-vue/front/src/api/game.js`
- Modify: `forum-vue/front/src/views/GameCenter.vue`
- Modify: `forum-vue/front/src/scripts/views/GameCenter.js`
- Modify: `forum-vue/front/src/assets/styles/game-center.css`
- Test: `forum-vue/front/tests/game-center-redesign.test.mjs`

**Interfaces:**
- Consumes: 两个排行榜 `PageResult`；四类对局记录现有 `PageResult`。
- Produces: 仅俄罗斯方块/俄罗斯方块 PK 两个天梯榜标签；统一一行对战记录；Tetris 统计显示最高得分。

- [ ] 写结构和请求参数测试并确认失败。
- [ ] 删除五子棋/井字棋排行榜 API 调用和标签，接入真实 `pageNum/pageSize/total`。
- [ ] 调整标题、统计卡、记录列、排行榜行、积分余额与观战搜索按钮样式。
- [ ] 运行结构测试确认通过。

### Task 6: 构建与桌面端回归

**Files:**
- Verify only.

- [ ] 运行所有新增 Node 结构测试。
- [ ] 在 `backend` 运行定向 Maven 测试和 `mvn compile`。
- [ ] 在 `forum-vue/front` 运行 `npm run build`。
- [ ] 检查修改范围内不存在 `console.log`，检查 `git diff --check`。
- [ ] 使用既有一次性票据登录流程完成桌面端首页、搜索、个人主页和游戏中心回归；不在命令输出中暴露凭据或票据。
