# Lottery, AI Session Delete and Hot Trend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复抽奖保底展示、增加 AI 会话删除，并为每日热帖榜增加周期趋势箭头。

**Architecture:** 抽奖计数在现有事务内封顶，前端结合头奖库存显示真实状态；AI 会话沿用现有两张表的逻辑删除字段；热榜在现有蓝绿重算旁维护 Redis 指标快照和趋势枚举，接口只返回方向。

**Tech Stack:** Spring Boot 3、MyBatis-Plus Lambda API、Redis、Vue 3、Element Plus、Node Test、JUnit 5、Mockito。

## Global Constraints

- 不新增数据库表或字段，不修改历史 SQL。
- Controller 只取认证用户、调用 Service、返回 Result。
- 写操作使用 `@Transactional(rollbackFor = Exception.class)`。
- Vue 保持 `.vue`、`.js`、`.scss/.css` 三文件结构，危险删除必须二次确认。
- 前端不计算榜单热度，只渲染后端枚举。

---

### Task 1: 失败契约测试

**Files:**
- Create: `backend/src/test/java/org/example/forumdemo/service/impl/lottery/LotteryPityPolicyTest.java`
- Create: `backend/src/test/java/org/example/forumdemo/service/impl/mascot/CompanionMemoryServiceImplTest.java`
- Create: `backend/src/test/java/org/example/forumdemo/service/impl/article/ArticleHotRankingServiceImplTest.java`
- Create: `forum-vue/front/tests/ui-followup-round-four.test.mjs`

- [ ] 编写保底封顶、会话归属删除、热度趋势和 UI 契约测试。
- [ ] 运行定向测试，确认因功能尚未实现而失败。

### Task 2: 抽奖状态与奖池布局

**Files:**
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/lottery/LotteryServiceImpl.java`
- Modify: `forum-vue/front/src/scripts/views/LotteryView.js`
- Modify: `forum-vue/front/src/views/LotteryView.vue`
- Modify: `forum-vue/front/src/assets/styles/lottery.css`

- [ ] 将未中奖后的计数封顶到硬保底阈值。
- [ ] 根据头奖可用库存派生保底文案。
- [ ] 奖品名称和库存拆为两行，普通左对齐、头奖右对齐。
- [ ] 运行定向测试确认通过。

### Task 3: AI 会话软删除

**Files:**
- Modify: `backend/src/main/java/org/example/forumdemo/controller/MascotController.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/mascot/CompanionMemoryService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/mascot/CompanionMemoryServiceImpl.java`
- Modify: `forum-vue/front/src/api/mascot.js`
- Modify: `forum-vue/front/src/components/mascot/MascotDock.vue`
- Modify: `forum-vue/front/src/scripts/components/mascot/MascotDock.js`
- Modify: `forum-vue/front/src/assets/styles/mascot-dock.css`

- [ ] 增加当前用户会话 DELETE 接口和事务软删。
- [ ] 增加悬停删除按钮、确认、提交锁和本地状态切换。
- [ ] 运行定向测试确认通过。

### Task 4: 热帖周期趋势

**Files:**
- Modify: `backend/src/main/java/org/example/forumdemo/common/constant/ForumRedisKeys.java`
- Create: `backend/src/main/java/org/example/forumdemo/common/enums/HotArticleTrendDirection.java`
- Modify: `backend/src/main/java/org/example/forumdemo/entity/vo/article/HotArticleListItemVO.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/article/ArticleHotRankingService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/article/HotArticleRedisOps.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/article/ArticleHotRankingServiceImpl.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/article/ArticleServiceImpl.java`
- Modify: `forum-vue/front/src/views/HomeFeed.vue`
- Modify: `forum-vue/front/src/assets/styles/home.css`

- [ ] 读取累计快照并计算周期点击、赞、藏增量分。
- [ ] 比较上周期分，批量保存方向和新基线。
- [ ] 将方向加入热榜 VO 并渲染红升蓝降箭头。
- [ ] 运行定向测试确认通过。

### Task 5: 静态金边与全量验证

**Files:**
- Modify: `forum-vue/front/src/assets/styles/home.css`
- Modify: existing frontend contract tests that assert the retired animation.

- [ ] 删除 AI 搜索高光伪元素、关键帧和动画属性，只保留静态金色边框。
- [ ] 运行 `node --test tests/*.test.mjs`，预期零失败。
- [ ] 运行 `npm run build`，预期构建成功。
- [ ] 使用 IDEA 内置 Maven 执行相关测试及 `clean compile`，预期 BUILD SUCCESS。
- [ ] 使用真实浏览器验证删除按钮、抽奖状态和趋势箭头布局。

