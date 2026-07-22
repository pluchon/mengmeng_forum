# 帖子详情与个人主页收藏修订 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成帖子详情 4 项修复和个人主页收藏/点赞 3 项增强，并用定向测试、构建和登录冒烟验证。

**Architecture:** Vue 继续采用 `.vue` / `.js` / `.scss` 三文件边界，页面仅编排数据与展示。后端通过 Controller → Service → Mapper 分页查询，并由 converter 将 Entity 转为专用 VO 和执行截断。

**Tech Stack:** Spring Boot 3、MyBatis-Plus Lambda API、JUnit 5、Vue 3、Element Plus、Vite、Node test、Playwright CLI。

## Global Constraints

- 不新增表、字段或业务流程。
- 收藏夹外层列表后端分页，每页固定展示 5 个。
- 标题、正文、作者昵称的截断全部在后端完成。
- 所有写操作保留事务，认证身份只来自服务端上下文。
- 前端不直接调用 Axios，不修改 Store 内部状态，不添加 `console.log`。
- 连续修改超过 3 个文件前已提示用户提交当前状态。

---

### Task 1: 建立失败契约

**Files:** `forum-vue/front/tests/profile-favorites-followup.test.mjs`、`backend/src/test/java/org/example/forumdemo/converter/FavoriteConverterTest.java`、`backend/src/test/java/org/example/forumdemo/service/impl/favorite/FavoriteFolderServiceImplTest.java`

- [ ] 运行 `node --test tests/profile-favorites-followup.test.mjs`，确认缺少分页、作者布局或后端摘要时失败。
- [ ] 运行 IDEA Maven 的 `-Dtest=FavoriteConverterTest,FavoriteFolderServiceImplTest test`，确认缺少 converter 或分页签名时失败。

### Task 2: 修复帖子详情交互

**Files:** `CommentShopEmojiPopover.*`、`CommentReplyMediaDisplay.*`、`SubReplyArea.*`、`ArticleDetail.vue`、两个 `ArticleDetail.js`

- [ ] 对照当前数据流确认每个症状的根因。
- [ ] 仅修改根因处，使表情气泡、标签、关注标识与 `@` 规则满足契约。
- [ ] 重跑前端契约，确认详情相关断言通过。

### Task 3: 后端收藏分页与摘要 VO

**Files:** `FavoriteController.java`、`FavoriteFolderService.java`、`FavoriteFolderServiceImpl.java`、`FolderArticleVO.java`、新建 `FavoriteArticleSummaryVO.java` 与 `FavoriteConverter.java`、`FavoriteArticleServiceImpl.java`

- [ ] 用 MyBatis-Plus `selectPage` 实现本人/他人收藏夹分页，保留公开性过滤。
- [ ] converter 去除 HTML 和连续空白，按码点截断标题、正文和昵称，不修改 Entity。
- [ ] 收藏帖子查询批量加载作者并转换为 VO。
- [ ] 重跑两个后端定向测试，确认通过。

### Task 4: 个人主页收藏与点赞 UI

**Files:** `api/favorite.js`、`views/Profile.vue`、`scripts/views/Profile.js`、`assets/styles/user.css`

- [ ] API 转发分页参数，页面固定请求 5 条并处理加载、错误、无权限和空数据。
- [ ] 删除前端摘要截断，直接展示后端返回的标题、正文和昵称。
- [ ] 用 `UserAvatarVip` 显示作者头像环，右侧两行对齐。
- [ ] 改名按钮提交期间禁用，点赞页展示 `likedTotal`。
- [ ] 重跑前端契约测试。

### Task 5: 完整验证与登录冒烟

**Files:** 复用 `forum-vue/front/tests/e2e/article-polish-browser.cjs`

- [ ] 使用 IDEA 内置 Maven 运行定向测试和 `clean compile`。
- [ ] 在 `forum-vue/front` 运行 `npm run build`。
- [ ] 搜索本次生产文件，确认没有新增 `console.log`。
- [ ] 检查 `npx`，启动临时前端服务并签发短时一次性票据。
- [ ] 登录后检查帖子详情商城表情、标签、关注标识、楼中楼，以及个人主页收藏分页、作者行、改名勾和点赞总数。
- [ ] 停止临时服务，确认没有遗留测试进程或凭据文件。
