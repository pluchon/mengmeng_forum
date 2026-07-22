# Forum UI Follow-up Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复帖子表情层级、楼中楼 @ 语义、签到与漂流瓶视觉问题，精简抽奖活动页并恢复 PyCharm 对 `ai-server/main.py` 的直接启动。

**Architecture:** 保持现有 Vue 三文件组件结构与 `Controller → Service → Mapper` 后端调用链。楼层回复通过提交 `replyUserId=null` 表示直接回复一级评论，不新增数据库字段；抽奖热度和彩蛋功能从接口、服务和 UI 同步移除，但不对线上历史数据库列做破坏性删除。

**Tech Stack:** Vue 3、Element Plus、Vite、Spring Boot、MyBatis-Plus、Python 3.14、Node.js test runner、Playwright CLI。

## Global Constraints

- Vue 组件继续使用项目既有 `.vue` / `.js` / `.scss` 或 `.css` 分离方式。
- Java Controller 只调用 Service；写操作继续由 Service 的事务边界负责。
- 不新增表、字段、接口或业务流程。
- 所有 AI 请求路径仍保持本地 7897 端口约束，本计划不改 AI 请求路由。
- 不覆盖当前工作区内与本需求无关的未提交修改。

---

### Task 1: 回归契约测试

**Files:**
- Create: `forum-vue/front/tests/ui-followup-round-two.test.mjs`

**Interfaces:**
- Consumes: 当前 Vue、Java、Python 源码文本。
- Produces: 对弹层层级、楼层回复语义、签到宽度、海浪显示、抽奖功能删除和 Python 路径引导的可重复回归检查。

- [ ] 写入测试，分别断言：表情 popover 显式传送且层级高于详情弹窗；一级评论回复提交空 `replyUserId`；签到卡占满列；海浪不再隐藏；活动页不含热度、演示说明和彩蛋；后端不含热度查询和彩蛋接口；`main.py` 在本地导入前补入脚本目录。
- [ ] 运行 `node --test tests/ui-followup-round-two.test.mjs`，确认测试因现有实现缺失而失败。

### Task 2: 帖子表情与楼中楼语义

**Files:**
- Modify: `forum-vue/front/src/components/article/CommentShopEmojiPopover.vue`
- Modify: `forum-vue/front/src/scripts/views/ArticleDetail.js`

**Interfaces:**
- Consumes: Element Plus `el-popover` 与现有 `submitSubReply` 请求。
- Produces: `teleported=true` 且层级高于 3100 的表情预览；`replyTarget.showMention` 明确区分一级回复和楼中楼回复。

- [ ] 给表情 popover 设置组件级 `popper-style` 层级并显式传送到 body。
- [ ] `startReplyToFloor` 设置 `replyUserId=null`、`showMention=false`；`startReplyToSub` 保留目标用户并设置 `showMention=true`；输入框提示按该标记渲染。
- [ ] 运行契约测试，确认相关断言通过。

### Task 3: 签到卡和漂流瓶海洋

**Files:**
- Modify: `forum-vue/front/src/assets/styles/checkin.css`
- Modify: `forum-vue/front/src/assets/styles/drift-bottle.css`

**Interfaces:**
- Consumes: 现有三列统计网格和 `.ocean-waves` 三层波浪 DOM。
- Produces: 每张签到图卡宽度与网格列完全一致；紧凑布局底部保留低高度动态海浪。

- [ ] 移除签到卡固定高度约束，设置 `width: 100%`、`height: auto` 和 16:9，移动端保持单列。
- [ ] 恢复海洋渐变背景和高度约 100px 的波浪，保留低层级与 `pointer-events: none`，增加减少动态偏好处理。
- [ ] 运行契约测试，确认相关断言通过。

### Task 4: 抽奖页信息精简

**Files:**
- Modify: `forum-vue/front/src/views/LotteryView.vue`
- Modify: `forum-vue/front/src/scripts/views/LotteryView.js`
- Modify: `forum-vue/front/src/assets/styles/lottery.css`
- Modify: `forum-vue/front/src/assets/styles/lottery-overlays.css`
- Modify: `forum-vue/front/src/api/lottery.js`

**Interfaces:**
- Consumes: 活动列表、活动信息、抽奖与历史记录接口。
- Produces: 三列紧凑工作区“积分状态 / 抽奖操作 / 核心规则”，以及奖池与单个概率图；不再存在彩蛋交互。

- [ ] 删除演示说明、近期开奖热度和彩蛋预览及 Dialog。
- [ ] 移除彩蛋状态、图片、请求方法、条形图配置和无用图标导入。
- [ ] 将右侧说明压缩为三条核心规则，删除重复 bullet 文案；调整三列比例、间距和底部概率图宽度。
- [ ] 删除孤立的彩蛋 CSS，并运行契约测试。

### Task 5: 抽奖后端清理

**Files:**
- Modify: `backend/src/main/java/org/example/forumdemo/controller/LotteryController.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/interfaces/lottery/LotteryService.java`
- Modify: `backend/src/main/java/org/example/forumdemo/service/impl/lottery/LotteryServiceImpl.java`
- Modify: `backend/src/main/java/org/example/forumdemo/mapper/LotteryDrawRecordMapper.java`
- Modify: `backend/src/main/java/org/example/forumdemo/entity/vo/lottery/LotteryActivityInfoVO.java`
- Delete: `backend/src/main/java/org/example/forumdemo/entity/vo/lottery/LotteryPrizeHeatVO.java`
- Delete: `backend/src/main/java/org/example/forumdemo/entity/vo/lottery/LotterySurpriseClaimVO.java`
- Modify: `backend/src/main/java/org/example/forumdemo/common/constant/ForumBusinessConstants.java`
- Modify: `backend/src/main/java/org/example/forumdemo/common/constant/Constant.java`
- Modify: `backend/src/main/java/org/example/forumdemo/entity/db/User.java`
- Modify: `backend/src/main/java/org/example/forumdemo/entity/vo/user/UserSessionVO.java`
- Modify: `backend/src/main/java/org/example/forumdemo/converter/UserConverter.java`
- Modify: `backend/src/main/java/org/example/forumdemo/mapper/UserMapper.java`
- Modify: `backend/src/main/resources/sql/create.sql`
- Modify: `nginx/package/sql/create.sql`

**Interfaces:**
- Consumes: 抽奖基础信息、抽奖、记录查询能力。
- Produces: 不再查询全站开奖热度，不再提供 `/lottery/surprise-bonus`；历史数据库中已存在的冗余列可继续保留但不再被应用读取。

- [ ] 删除活动信息 VO 的热度和彩蛋字段及 Service 装配查询。
- [ ] 删除 Mapper 热度聚合方法、彩蛋 Controller/Service/VO、积分来源常量、用户实体映射和新库建表列。
- [ ] 全仓扫描 `PrizeWinHeat|surprise-bonus|点我看看|lottery_surprise_claimed`，除非属于历史文档，否则产品源码应为零匹配。
- [ ] 使用 IntelliJ 内置 Maven 执行 `clean compile`。

### Task 6: Python 入口兼容

**Files:**
- Modify: `ai-server/main.py`

**Interfaces:**
- Consumes: PyCharm AI Profiler 的 `runpy.run_path` 启动方式。
- Produces: 在导入 `api`、`config`、`workers` 前确保 `Path(__file__).resolve().parent` 位于 `sys.path`。

- [ ] 在标准库导入区加入 `sys` 与 `Path`，幂等插入脚本目录。
- [ ] 使用 Python 3.14 模拟移除脚本目录后的 `runpy.run_path`，确认不再因 `No module named 'api'` 失败。

### Task 7: 完整验证

**Files:**
- Test: `forum-vue/front/tests/ui-followup-round-two.test.mjs`

**Interfaces:**
- Consumes: 全部修改结果。
- Produces: 编译、构建与真实浏览器证据。

- [ ] 运行相关 Node 契约测试与 `npm run build`。
- [ ] 运行 IntelliJ 内置 Maven `clean compile`。
- [ ] 使用一次性票据登录，在真实浏览器验证表情弹层位于详情卡之上、签到三卡对齐、漂流瓶海浪可见、抽奖页模块已精简。
- [ ] 关闭 Playwright 与临时测试服务，执行 `git diff --check` 和残留扫描。

