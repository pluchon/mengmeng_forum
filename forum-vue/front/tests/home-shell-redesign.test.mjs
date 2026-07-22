import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = relativePath => readFile(new URL(relativePath, import.meta.url), 'utf8')

test('logged-out top bar only shows the enlarged login entry', async () => {
  const source = await readSource('../src/components/layout/HomeTopBar.vue')

  assert.doesNotMatch(source, /<template v-else>[\s\S]*?home-shell-avatar-btn/)
  assert.match(source, /class="home-shell-auth-entry"/)
  assert.match(source, /v-if="userStore\.isLoggedIn"[\s\S]*?class="home-shell-points"/)
})

test('message center is a sidebar quick entry and not a top bar tool', async () => {
  const topBar = await readSource('../src/components/layout/HomeTopBar.vue')
  const sidebar = await readSource('../src/components/layout/HomeSidebar.vue')

  assert.doesNotMatch(topBar, /openMessageCenter|home-msg-notify-wrap/)
  assert.match(sidebar, /openMessageCenter/)
  assert.match(sidebar, /消息中心/)
  assert.match(sidebar, /<Message\s*\/>/)
})

test('home category trigger and checkin strip use the compact requested copy', async () => {
  const view = await readSource('../src/views/HomeFeed.vue')
  const script = await readSource('../src/views/HomeFeed.js')

  assert.match(view, /categoryTriggerLabel\(item\)/)
  assert.match(script, /function categoryTriggerLabel\(item\)/)
  assert.match(view, /每日签到/)
  assert.match(view, /连续\s*<strong>\{\{ checkinSummary\.streakDays/)
  assert.match(view, /待签到/)
  assert.match(view, /去签到/)
  assert.doesNotMatch(view, /已攒|萌币 ·/)
})

test('AI search uses a static gold border and home scrollbars stay hidden', async () => {
  const styles = await readSource('../src/assets/styles/home.css')

  assert.match(styles, /border:\s*2px solid #[a-f\d]{6}/i)
  assert.doesNotMatch(styles, /home-search-ai-highlight-orbit|--home-search-ai-angle/)
  assert.match(styles, /\.shell-page-scroll::-webkit-scrollbar\s*\{[^}]*display:\s*none/s)
})
