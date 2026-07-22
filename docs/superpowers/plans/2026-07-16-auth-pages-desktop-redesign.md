# 萌部落三个认证页方案 A 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不改变认证接口与业务流程的前提下，把登录、注册、找回密码统一为粒子海背景上的单卡 2:1 分栏桌面界面，并恢复淡红小红书主题。

**Architecture:** 保留 `App.vue` 现有认证路由粒子海挂载逻辑，不修改 `ParticleSea`。`auth-split-layout.css` 只负责三页共享尺寸、2:1 网格、图片与右侧内容壳层；三个路由视图继续组合各自表单，三个页面脚本继续负责原有 API、验证码、人机验证与跳转。登录脚本仅把现有认证路径展开为四个一级页签。

**Tech Stack:** Vue 3、Composition API、Element Plus、Vite 6、原生 CSS、Node `node:test`、Playwright。

## 约束

- 只修改三个认证界面，不修改全站背景、粒子海实现、后端接口或数据库。
- 三页卡片使用同一宽高规则，上限 `1200px × 700px`，内部列宽 `2fr 1fr`。
- 目标视口为 1280×720、1440×900、1920×1080；不专项适配移动端。
- 页面继续遵守 `.vue` / `.js` / `.css` 分离，不直接调用 Axios，不新增依赖。
- 保留当前加载态、验证码倒计时、行为验证码、协议校验和成功跳转。
- 修改代码不得包含 `console.log`。

### Task 1: 锁定方案 A 的结构契约

**Files:**
- Modify: `forum-vue/front/tests/auth-desktop-layout.test.mjs`

- [ ] 保留三张本地 WebP 的格式与体积测试。
- [ ] 添加共享布局断言：认证画布透明、卡片宽高统一、网格为 `2fr 1fr`、不存在注册页加高类。
- [ ] 添加视觉断言：使用 `var(--primary-red)`，不再出现棕色主题值或英文眉题。
- [ ] 添加登录页断言：恰好四个一级页签、无邮箱二级切换、协议在主按钮之前、两个密码模式都有同行找回按钮。
- [ ] 添加注册与找回页断言：品牌名、简洁名词标题、指定左侧短文案、唯一主按钮。
- [ ] 运行 `node --test tests/auth-desktop-layout.test.mjs`，确认测试因旧结构而按预期失败。

### Task 2: 实现三页共享单卡壳层

**Files:**
- Modify: `forum-vue/front/src/assets/styles/auth-split-layout.css`
- Modify: `forum-vue/front/src/views/SignIn.vue`
- Modify: `forum-vue/front/src/views/SignUp.vue`
- Modify: `forum-vue/front/src/views/ForgotPassword.vue`

- [ ] 将 `.auth-page` 设为透明，使 `ParticleSea` 在卡片外完整可见。
- [ ] 将共享卡片改为普通单层网格，左右列为 `2fr 1fr`；移除绝对定位整图与浮动表单卡样式。
- [ ] 三页使用完全一致的卡片宽高规则，不使用 `.auth-card--tall`。
- [ ] 左侧保留本地图片，加入淡灰亚克力文案块与短红装饰线；删除全部英文眉题和长文案。
- [ ] 右侧统一居中品牌名，注册与找回仅增加 `创建账号`、`找回密码` 名词标题。
- [ ] 弱化备案号并固定在内容区底部。

### Task 3: 展开四种登录方式并恢复淡红主题

**Files:**
- Modify: `forum-vue/front/src/views/SignIn.vue`
- Modify: `forum-vue/front/src/scripts/views/SignIn.js`
- Modify: `forum-vue/front/src/assets/styles/signin.css`
- Modify: `forum-vue/front/src/assets/styles/signup.css`
- Modify: `forum-vue/front/src/assets/styles/forgot.css`

- [ ] 登录页一级页签改为 `短信验证码`、`账号密码`、`邮箱验证码`、`邮箱密码`。
- [ ] 四个页签继续分别映射 `smsLogin`、`login`、`mailLogin`、`login`，验证码用途与行为验证目的保持不变。
- [ ] 两种密码表单的密码框右侧均提供同行 `忘记密码` 弱按钮。
- [ ] 登录和注册协议放在主按钮上方；创建账号、先逛逛和返回登录均为弱文字入口。
- [ ] 三页输入框、页签、按钮、焦点与链接统一使用项目既有 `--primary-red` / `--primary-hover` / `--primary-pale`。
- [ ] 运行结构测试直至全绿。

### Task 4: 构建与真实浏览器回归

**Files:**
- Verify: `forum-vue/front/src/views/SignIn.vue`
- Verify: `forum-vue/front/src/views/SignUp.vue`
- Verify: `forum-vue/front/src/views/ForgotPassword.vue`

- [ ] 运行 `node --test tests/auth-desktop-layout.test.mjs`。
- [ ] 运行 `npm run build`。
- [ ] 检查本次修改文件不存在 `console.log`，并运行 `git diff --check`。
- [ ] 在 1280×720、1440×900、1920×1080 验证三页卡片尺寸一致、粒子海可见、无横向滚动且所有操作可达。
- [ ] 切换四种登录方式、注册页和找回页邮箱/手机切换，确认无控制台错误；不提交无效真实数据。
- [ ] 检查 Git 边界，确保 nginx 与用户未跟踪文件未被改动或暂存。

