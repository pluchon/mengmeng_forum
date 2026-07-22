import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const frontRoot = new URL('../src/', import.meta.url)
const backendJavaRoot = new URL('../../../backend/src/main/java/org/example/forumdemo/', import.meta.url)

async function source(root, relativePath) {
  return readFile(new URL(relativePath, root), 'utf8')
}

test('lottery summary is compact, aligned, and prize chips omit probability', async () => {
  const view = await source(frontRoot, 'views/LotteryView.vue')
  const script = await source(frontRoot, 'scripts/views/LotteryView.js')
  const style = await source(frontRoot, 'assets/styles/lottery.css')

  assert.doesNotMatch(view, /当前积分余额|十连还差|lottery-progress-bg|chip-weight/)
  assert.doesNotMatch(script, /ptsToTen|tenProgressPct|formatPrizePercent/)
  assert.match(style, /\.lottery-main-grid\s*\{[\s\S]*?align-items:\s*stretch/)
  assert.match(style, /\.lottery-info-main\s*\{[\s\S]*?display:\s*flex[\s\S]*?flex-direction:\s*column/)
  assert.match(style, /\.lottery-activity-picker\s*\{[\s\S]*?margin-top:\s*auto/)
})

test('group message report prompt has a centered title and bounded autosizing input', async () => {
  const script = await source(frontRoot, 'scripts/views/MessageView.js')
  const globalStyle = await source(frontRoot, 'assets/styles/global.css')

  assert.match(script, /ElMessageBox\.prompt\('',\s*'举报群消息'/)
  assert.match(script, /customClass:\s*'group-message-report-box'/)
  assert.doesNotMatch(script, /ElMessageBox\.prompt\('请填写举报原因'/)
  assert.match(globalStyle, /\.group-message-report-box[\s\S]*?\.el-message-box__title[\s\S]*?font-weight:\s*800/)
  assert.match(globalStyle, /\.group-message-report-box[\s\S]*?field-sizing:\s*content/)
  assert.match(globalStyle, /\.group-message-report-box[\s\S]*?max-height:\s*144px/)
})

test('announcement entry lives in the left quick links instead of avatar menu', async () => {
  const sidebar = await source(frontRoot, 'components/layout/HomeSidebar.vue')
  const sidebarScript = await source(frontRoot, 'scripts/components/layout/HomeSidebar.js')
  const topbar = await source(frontRoot, 'components/layout/HomeTopBar.vue')
  const topbarScript = await source(frontRoot, 'scripts/components/layout/HomeTopBar.js')

  assert.match(sidebar, /@click="showAnnouncement"[\s\S]*?公告与活动/)
  assert.match(sidebarScript, /showAnnouncement/)
  assert.doesNotMatch(topbar, /公告与活动|showAnnouncement/)
  assert.doesNotMatch(topbarScript, /showAnnouncement/)
})

test('captcha close rejects verification and login prompt uses only one action', async () => {
  const captchaView = await source(frontRoot, 'components/captcha/BehaviorCaptchaDialog.vue')
  const captchaScript = await source(frontRoot, 'scripts/components/captcha/BehaviorCaptchaDialog.js')
  const captchaStyle = await source(frontRoot, 'assets/styles/captcha-dialog.css')
  const loginPrompt = await source(frontRoot, 'utils/loginPrompt.js')
  const globalStyle = await source(frontRoot, 'assets/styles/global.css')

  assert.match(captchaView, /<script setup src="@\/scripts\/components\/captcha\/BehaviorCaptchaDialog\.js"><\/script>/)
  assert.match(captchaView, /captcha-dialog-title">安全验证/)
  assert.doesNotMatch(captchaView, /#footer|>取消</)
  assert.match(captchaScript, /function onDialogClosed\(\)[\s\S]*?rejectPromise\?\.\(new Error\('cancelled'\)\)/)
  assert.doesNotMatch(captchaScript, /function closeReject/)
  assert.match(captchaStyle, /\.captcha-dialog-title[\s\S]*?text-align:\s*center[\s\S]*?font-weight:\s*800/)

  assert.match(loginPrompt, /ElMessageBox\.confirm\('',\s*'需要登录'/)
  assert.match(loginPrompt, /showCancelButton:\s*false/)
  assert.match(loginPrompt, /customClass:\s*'login-required-box'/)
  assert.doesNotMatch(loginPrompt, /暂不登录/)
  assert.match(globalStyle, /\.login-required-box[\s\S]*?\.el-message-box__confirm[\s\S]*?width:\s*100%/)
})

test('AI search uses a static inset gold border without animation', async () => {
  const style = await source(frontRoot, 'assets/styles/home.css')
  const block = style.match(/\/\* AI 增强搜索[\s\S]*?\.home-search-prefix-inner\s*\{/)?.[0] || ''

  assert.match(block, /border:\s*2px solid #[0-9a-f]{6}/i)
  assert.doesNotMatch(block, /animation:|conic-gradient|mask-composite|::before/)
  assert.doesNotMatch(style, /home-search-ai-highlight-orbit|--home-search-ai-angle/)
})

test('growth records open from the XP card in a ten-row backend-paged dialog', async () => {
  const view = await source(frontRoot, 'views/GrowthCenter.vue')
  const script = await source(frontRoot, 'scripts/views/GrowthCenter.js')
  const api = await source(frontRoot, 'api/growth.js')
  const controller = await source(backendJavaRoot, 'controller/GrowthController.java')
  const service = await source(backendJavaRoot, 'service/impl/growth/GrowthExperienceServiceImpl.java')

  assert.match(view, /growth-record-trigger[\s\S]*?@click="openRecordDialog"/)
  assert.match(view, /v-model="recordDialogVisible"[\s\S]*?growth-record-dialog-title">成长记录/)
  assert.match(view, /<el-table[\s\S]*?label="名称"[\s\S]*?label="来源"[\s\S]*?label="时间"[\s\S]*?label="经验"/)
  assert.doesNotMatch(view, /<section[^>]*class="growth-record-card"/)
  assert.match(script, /const RECORD_PAGE_SIZE = 10/)
  assert.match(script, /function openRecordDialog\(\)/)
  assert.match(api, /getGrowthRecords = \(pageNum = 1, pageSize = 10\)/)
  assert.match(controller, /@RequestParam\(defaultValue = "10"\) Integer pageSize/)
  assert.match(service, /DEFAULT_RECORD_PAGE_SIZE = 10/)
})
