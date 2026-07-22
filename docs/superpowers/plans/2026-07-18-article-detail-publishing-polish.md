# Article Detail And Publishing Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成帖子详情、评论媒体、收藏弹窗、封面发布与站内信审核通知的一致化优化。

**Architecture:** 保留现有路由页作为编排层，将商城表情气泡和楼中楼展示规则放在各自组件中。审核接口删除邮件选项，Service 继续负责状态流转、站内信和 WebSocket。

**Tech Stack:** Vue 3、Element Plus、Pinia、Spring Boot、MyBatis-Plus、Node test、Maven。

## Global Constraints

- 不新增表、字段、接口或业务流程。
- 前端组件继续使用 `.vue` / `.js` / `.scss` 外置结构。
- 所有 AI 请求保持现有本地 7897 端口链路。
- 不修改通用邮件服务，只删除帖子审核邮件调用。

---

### Task 1: 回归测试契约

**Files:**
- Create: `forum-vue/front/tests/article-detail-publishing-polish.test.mjs`

- [ ] 编写评论编辑器、标签折叠、AI 文案、视频按钮、收藏弹窗、封面发布、表情气泡、楼中楼和审核通知源码契约。
- [ ] 运行 `node --test tests/article-detail-publishing-polish.test.mjs`，确认因功能尚未实现而失败。

### Task 2: 帖子详情与评论编辑器

**Files:**
- Modify: `forum-vue/front/src/views/ArticleDetail.vue`
- Modify: `forum-vue/front/src/scripts/views/ArticleDetail.js`
- Modify: `forum-vue/front/src/assets/styles/article.css`
- Modify: `forum-vue/front/src/views/ArticleDetail.scss`
- Modify: `forum-vue/front/src/components/article/ArticleDetailVideo.vue`
- Modify: `forum-vue/front/src/assets/styles/article-detail-video.css`

- [ ] 移除底部评论操作项，重排评论输入区。
- [ ] 增加标签展开状态和短内容摘要提示。
- [ ] 重做 AI 导读、收藏弹窗和视频播放图标样式。

### Task 3: 商城表情气泡与楼中楼

**Files:**
- Create: `forum-vue/front/src/components/article/CommentShopEmojiPopover.vue`
- Create: `forum-vue/front/src/scripts/components/article/CommentShopEmojiPopover.js`
- Create: `forum-vue/front/src/components/article/CommentShopEmojiPopover.scss`
- Modify: `forum-vue/front/src/components/article/CommentReplyMediaDisplay.vue`
- Modify: `forum-vue/front/src/scripts/components/article/CommentReplyMediaDisplay.js`
- Replace style companion: `forum-vue/front/src/components/article/CommentReplyMediaDisplay.scss`
- Modify: `forum-vue/front/src/components/article/SubReplyArea.vue`
- Modify: `forum-vue/front/src/scripts/components/article/SubReplyArea.js`
- Create: `forum-vue/front/src/components/article/SubReplyArea.scss`

- [ ] 用现有 `getShopDetail(shopId)` 懒加载气泡元数据。
- [ ] 普通图片保留预览，商城表情不再直接跳转或显示笑脸角标。
- [ ] 通过 `root-reply-user-id` 判断是否展示楼中楼回复目标。

### Task 4: 封面发布与审核通知

**Files:**
- Modify: `forum-vue/front/src/views/ArticleCoverSetup.vue`
- Create: `forum-vue/front/src/views/ArticleCoverSetup.js`
- Create: `forum-vue/front/src/views/ArticleCoverSetup.scss`
- Modify: `forum-vue/front/src/scripts/views/ArticleCoverSetup.js`
- Modify: `forum-vue/front/src/composables/useArticleAuditSubmit.js`
- Modify: `forum-vue/front/src/assets/styles/global.css`
- Modify: `backend/src/main/java/org/example/forumdemo/controller/ArticleController.java`
- Modify: `backend/src/main/java/org/example/forumdemo/entity/dto/article/SubmitForAuditRequest.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/article/ArticleService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/article/ArticleAuditService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/article/ArticleServiceImpl.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/article/ArticleAuditServiceImpl.java`

- [ ] 更新封面页文案、按钮和发布确认框。
- [ ] 提交成功后统一返回首页并提示消息中心。
- [ ] 删除审核邮件参数、注入和调用，保留站内信与 WebSocket。

### Task 5: 验证与风险检查

- [ ] 运行新增测试并确认通过。
- [ ] 运行 `node --test tests/*.test.mjs`。
- [ ] 运行 `npm run build`，检查无 `console.log`。
- [ ] 运行后端相关测试与 Maven compile。
- [ ] 使用真实浏览器检查关键页面，记录尚未由 IDEA Maven Tool Window 验证的风险。
