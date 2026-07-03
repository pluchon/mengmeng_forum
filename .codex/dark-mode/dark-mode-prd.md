# 深色模式 PRD

> 文档版本：V0.2
>
> 当前状态：第一版浅色 / 深色切换已开始落地
>
> 更新时间：2026-07-03
>
> 本轮执行范围：
>
> - [x] 新增前端主题 store
> - [x] 在 `html` 上挂载 `data-theme`
> - [x] 兼容 Element Plus 的 `html.dark`
> - [x] 用户选择本地持久化
> - [x] 首页左侧快速入口增加“主题模式”
> - [x] 首页主壳、侧栏、卡片、基础输入框做第一版深色适配
>
> 本轮暂不包含：
>
> - [ ] 跟随系统
> - [ ] 设置页入口
> - [ ] 全站所有业务页面精修
> - [ ] 后端保存主题设置

## 1. 功能定位

深色模式是前端全站主题能力，不是单个页面换色。

目标是让用户可以在浅色、深色、跟随系统之间切换，并保证核心页面在深色下长期可用、可读、不卡顿。

当前第一版先实现浅色 / 深色两档切换，入口放在首页左侧快速入口，后续再补跟随系统和设置页入口。

## 2. 当前判断

现有前端样式存在两类情况：

- 部分样式已经使用 CSS 变量。
- 大量页面仍直接写死 `#fff`、`#1d2129`、`#f7f8fa`、浅色边框和浅色渐变。

因此深色模式不能靠统一滤镜或只改 body 背景实现，必须先建立主题变量，再分阶段替换高频页面的硬编码颜色。

## 3. P0 目标

P0 实现真正可用的深色模式基础能力。

必须支持：

- 主题模式：浅色、深色、跟随系统。
- 用户选择持久化。
- 首次进入页面时按保存设置或系统设置初始化主题。
- 跟随系统时响应 `prefers-color-scheme` 变化。
- 在 `html` 上挂载 `data-theme="light"` 或 `data-theme="dark"`。
- 全局语义色变量。
- Element Plus 基础组件深色适配。
- 核心页面深色可读。
- 游戏页不被全局深色样式错误污染。

## 4. 非目标

P0 不做：

- 全站所有页面一次性完美适配。
- 自定义主题色。
- 多套皮肤商城。
- 跟随时间自动切换。
- 图片、头像、视频反色。
- 对游戏画布内容做自动滤镜。
- 后端保存主题设置。

## 5. 主题模式

### 5.1 light

强制浅色模式，不跟随系统。

### 5.2 dark

强制深色模式，不跟随系统。

### 5.3 system

跟随系统设置。

系统为浅色时应用浅色主题，系统为深色时应用深色主题。

## 6. 前端结构

建议新增：

- `src/stores/theme.js`
- `src/assets/styles/theme.css`
- `src/components/theme/ThemeToggle.vue`
- `src/scripts/components/theme/ThemeToggle.js`
- `src/components/theme/ThemeToggle.css`

职责划分：

- theme store：维护主题模式、实际主题、持久化、系统监听。
- theme.css：定义浅色和深色主题变量，覆盖 Element Plus 必要变量。
- ThemeToggle：展示主题切换入口。

## 7. 主题初始化流程

应用启动时：

1. 读取本地持久化主题模式。
2. 如果没有保存记录，默认使用 `system`。
3. 根据模式计算实际主题。
4. 给 `html` 设置 `data-theme`。
5. 给 `html` 设置 `color-scheme`。
6. 如果模式为 `system`，监听系统主题变化。

切换主题时：

1. 更新 theme store。
2. 写入本地持久化。
3. 重新计算实际主题。
4. 更新 `html[data-theme]`。

## 8. 主题变量

变量必须使用语义命名，不用 `--white`、`--black` 这类颜色名。

建议 P0 变量：

```css
--color-bg-page
--color-bg-card
--color-bg-elevated
--color-bg-input
--color-text-primary
--color-text-secondary
--color-text-muted
--color-border
--color-border-soft
--color-primary
--color-primary-bg
--color-danger
--color-success
--color-warning
--color-mask
--shadow-card
```

浅色示例：

```css
:root {
  --color-bg-page: #f7f8fa;
  --color-bg-card: #ffffff;
  --color-bg-elevated: #ffffff;
  --color-text-primary: #1d2129;
  --color-text-secondary: #4e5969;
  --color-text-muted: #86909c;
  --color-border: #e5e6eb;
  --color-border-soft: #f2f3f5;
}
```

深色示例：

```css
html[data-theme='dark'] {
  --color-bg-page: #111318;
  --color-bg-card: #1a1d24;
  --color-bg-elevated: #222733;
  --color-text-primary: #f2f3f5;
  --color-text-secondary: #c9cdd4;
  --color-text-muted: #86909c;
  --color-border: #343946;
  --color-border-soft: #2a2f3a;
}
```

## 9. Element Plus 适配

P0 需要覆盖常用 Element Plus 变量：

- 主色。
- 页面背景。
- 文本颜色。
- 边框颜色。
- 卡片背景。
- 输入框背景。
- 弹窗背景。
- 下拉菜单背景。
- MessageBox 背景。
- Popover 背景。

重点注意：

- 弹窗、下拉、MessageBox 通常挂载在 body 下，不能只在页面局部容器内设置变量。
- 主色红在深色下需要降低刺眼程度，使用暗底浅红或低饱和红。

## 10. P0 页面适配范围

P0 优先适配高频路径：

- 全局布局。
- Header。
- Footer。
- 首页信息流。
- 帖子卡片。
- 文章详情。
- 评论区。
- 消息页。
- 登录注册页。
- 个人主页。
- 游戏中心入口。
- 设置页主题切换入口。

P0 不要求所有边缘页面达到最终视觉质量，但不能出现大面积白块、文字不可读、输入框不可见。

## 11. 游戏页处理

游戏页本身已经存在较多深色视觉。

P0 原则：

- 游戏画布不做自动反色。
- 游戏内固定视觉资源不受全局变量强制覆盖。
- 游戏房间外壳、按钮、弹窗可以跟随主题。
- 已经是暗色的游戏主界面避免二次变暗。

## 12. 图表和富文本

P0 只保证不明显不可读。

P1 再系统处理：

- ECharts 坐标轴、tooltip、legend、grid。
- 富文本编辑器背景、工具栏、占位文字。
- Markdown 代码块主题。

## 13. 实施顺序

### 13.1 P0 第一批

- 新建主题 Store。
- 新建 theme.css。
- 在应用启动时初始化主题。
- 增加主题切换组件。
- 在设置页或 Header 增加切换入口。
- 适配全局布局和 Element Plus 基础变量。

### 13.2 P0 第二批

- 替换首页、帖子卡片、文章详情、评论区硬编码颜色。
- 替换消息页硬编码颜色。
- 替换个人主页核心容器颜色。
- 检查登录注册页。

### 13.3 P0 第三批

- 检查游戏中心入口。
- 检查弹窗、下拉、Popover、MessageBox。
- 检查移动端布局。
- 运行构建。

## 14. P1 增强

- 适配积分、签到、会员中心、表情商店、创作中心、搜索页、收藏页。
- 给 ECharts 提供主题配置生成方法。
- 给富文本编辑器提供深色样式。
- 增加主题切换过渡效果。
- 支持更多业务弹窗细节适配。

## 15. P2 增强

- 自定义主题色。
- 节日或会员主题皮肤。
- 用户主题设置同步到后端。
- 按设备分别保存主题偏好。
- 更完整的视觉回归截图清单。

## 16. 风险点

- 硬编码颜色多，容易出现局部白块。
- Element Plus 浮层挂载在 body，局部覆盖无效。
- ECharts 和富文本不自动读取 CSS 变量。
- 游戏页如果被全局强制覆盖，可能破坏原本视觉。
- 图片、头像、封面不能跟随主题反色。
- 部分 CSS 同时存在 `.vue` 内样式和拆分 CSS，实施时要遵守三文件拆分规范。

## 17. 验收标准

- 用户可在浅色、深色、跟随系统之间切换。
- 刷新页面后主题选择不丢失。
- 跟随系统模式下，系统主题变化后页面能响应。
- `html[data-theme]` 状态正确。
- Header、首页、帖子详情、评论区、消息页、个人页没有明显白块。
- Element Plus 弹窗、输入框、下拉菜单、MessageBox 在深色下可读。
- 游戏页画布和游戏主视觉不被错误反色或二次压暗。
- 移动端核心页面没有文字和背景对比度问题。
- 前端 `npm run build` 通过。
