# 萌萌论坛 · 前端开发规范

面向 `forum-vue/front` 的约定：**接口路径与数据结构以 `forum-demo` 后端实现为准**（Controller / DTO / `create.sql`）；本文只约定前端工程如何实现，不重复抄录接口清单。

---

## 1. 技术栈（固定）

| 用途 | 选型 |
| --- | --- |
| 框架 | **Vue 3**（Composition API + `<script setup>`） |
| UI | **Element Plus**（表单、导航、反馈、布局的首选） |
| 图表 | **ECharts**（统计与可视化拓展；推荐通过封装组件接入，与全局样式变量协调） |
| 状态 | **Pinia**（全局状态；登录等与会话相关的 Store 使用 **`persist: true`**） |
| 路由 | **Vue Router**（`meta` 控制布局壳层与是否需要登录） |
| 请求 | **Axios**（统一封装于 `src/api/request.js`，含拦截器） |

图标等与 Element Plus 配套的 **`@element-plus/icons-vue`** 可按页面需要使用。

---

## 2. 与后端协作要点（摘录）

实现时注意（与 `forum-demo` 对齐）：

- 响应统一为 **`Result<T>`**：`code === 0` 成功；业务失败读 **`message`**，HTTP **401** 无 Result 包装时需单独处理。
- **JWT**：登录成功可从响应头 **`Authorization`** 取值；后续请求头 **`Authorization: <token>`**（无 `Bearer ` 前缀）。
- **分页**：参数 **`pageNum` / `pageSize`**；列表数据结构见文档中的 **`PageResult`**。
- **上传**：`multipart/form-data` 字段名为 **`file`**；成功后还需调用文档要求的「URL 落库」接口（如头像、封面），勿省略第二步。

---

## 3. 组件与目录

- **页面**：`src/views/`，文件名 **PascalCase**（如 `ArticleDetail.vue`）。
- **可复用 UI**：`src/components/`（通用原子能力放在 `components/common`）。
- **跨页面逻辑**：抽到 **`src/composables`**。
- **页面级 `<script setup>` 配套逻辑**：放在 **`src/scripts/`**，在代码中通过 Vite 别名 **`@scripts`** 引用（例如 `@scripts/views/SignIn`）；样式大块仍放在 **`src/assets/styles/`**。
- **Prop**：脚本里 **camelCase**，模板里 **kebab-case**。

---

## 4. UI 实现原则：少用裸 DOM「手搓」交互控件

目标：**交互控件与常用版面语义优先用成熟组件**，而不是堆 `<div>` + 手写样式模拟按钮、表格、Tabs、对话框等。

- **必选**：按钮 **`el-button`**、输入 **`el-input`**、选项 **`el-checkbox` / `el-radio` / `el-select`**、弹出层 **`el-dialog` / `el-drawer`**、分页 **`el-pagination`**、标签页 **`el-tabs`**、表格 **`el-table`**（适合时用）。
- **布局与留白**：优先 **`el-row` / `el-col`**、**`el-space`**、**`el-card`**、**`el-page-header`** 等；需要整体骨架时用 **`el-container` / `el-header` / `el-main`** 等。
- **数据可视化**：用 **ECharts**（或项目内封装组件），避免自建 SVG/CSS 图表。
- **加载与反馈**：**`ElMessage`** / **`ElMessageBox`**；加载过程 **`v-loading`**、**`el-skeleton`** 等。
- **纯装饰性容器**：少量 `<div>` 用于渐变遮罩、背景插画等可以接受；**不要**用一连串裸 `<div>` 替代组件库已有能力。

视觉基调（圆角、主色变量、`animate-fade-up` 等）沿用 **`src/assets/styles/`** 与全局 CSS 变量，新增页面保持同一套审美。

---

## 5. 逻辑组织

- 统一使用 **`<script setup>`**。
- 同一功能的 **`ref` / `computed` / 方法** 放在一起；过长逻辑抽到 **composable** 或子组件。

---

## 6. 状态管理（Pinia）

- **用户信息、token** 等：走 Store，避免在长链路间层层 **`props`** 传递。
- Store 文件放在 **`src/stores/*.js`**，导出 **`useXxxStore`**。

---

## 7. 路由与权限（Vue Router）

- 通过 **`meta`**（例如 **`layout: 'auth'`**、**`requiresAuth`**）控制是否在 **`App.vue`** 等顶层渲染顶栏等壳层。
- **全局前置守卫**：标记需要登录的路由在未登录时应跳转 **`/sign-in`**（与后端「须带 JWT」的接口保持一致）。

---

## 8. 样式与可维护性

- 组件样式优先 **`scoped`**；全局 tokens、页面级大块样式放在 **`src/assets/styles/`**。
- 修改交互前先查阅 **Element Plus / ECharts** 文档是否有现成能力。

---

*本文随仓库演进更新；接口字段与路径变更请以 `forum-demo` 源码与数据库脚本为准。*
