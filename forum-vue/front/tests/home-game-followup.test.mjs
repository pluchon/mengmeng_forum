import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const root = new URL('../src/', import.meta.url)

async function source(relativePath) {
  return readFile(new URL(relativePath, root), 'utf8')
}

test('theme switch lives in the sidebar footer instead of the top bar', async () => {
  const topBar = await source('components/layout/HomeTopBar.vue')
  const sidebar = await source('components/layout/HomeSidebar.vue')

  assert.doesNotMatch(topBar, /ThemeModeSwitch/)
  assert.match(sidebar, /home-sidebar-theme/)
  assert.match(sidebar, /主题模式/)
  assert.match(sidebar, /ThemeModeSwitch/)
})

test('mascot uses a persistent visibility setting and no pointer passthrough', async () => {
  const dock = await source('components/mascot/MascotDock.vue')
  const store = await source('stores/mascotUi.js')
  const settings = await source('views/Settings.vue')
  const mascotSettings = await source('components/settings/MascotSettings.vue')

  assert.doesNotMatch(dock, /pointerPassThrough|鼠标穿透/)
  assert.match(store, /mascot_visible_v1/)
  assert.match(store, /const visible/)
  assert.match(store, /setVisible/)
  assert.match(settings, /index="mascot"/)
  assert.match(settings, /看板娘设置/)
  assert.match(settings, /MascotSettings/)
  assert.match(mascotSettings, /显示\s*\/\s*隐藏看板娘[\s\S]*el-switch/)
  assert.doesNotMatch(mascotSettings, /控制看板娘|mascot-settings-icon|mascot-settings-copy/)
})

test('AI search keeps a static gold border without a highlight animation', async () => {
  const css = await source('assets/styles/home.css')

  assert.match(css, /\.home-search-inner--ai-rag\s*\{[\s\S]*border:\s*2px solid #[a-f\d]{6}/i)
  assert.doesNotMatch(css, /\.home-search-inner--ai-rag::before|home-search-ai-highlight-orbit/)
  assert.doesNotMatch(css, /animation:\s*home-search-ai-gold-orbit/)
})

test('checkin strip exposes a close action to the right of the status action', async () => {
  const view = await source('views/HomeFeed.vue')
  const script = await source('views/HomeFeed.js')

  assert.match(view, /checkin-home-action[\s\S]*checkin-home-close/)
  assert.match(view, /@click\.stop\.prevent="handleDismissCheckin"/)
  assert.match(script, /function handleDismissCheckin\(\)[\s\S]*dismissCheckinHomeStrip\(\)/)
})

test('game metrics stay on one line and statistics headers are separated from summary cards', async () => {
  const css = await source('assets/styles/game-center.css')

  assert.match(css, /\.game-metric-card\s*\{[^}]*display:\s*flex[^}]*justify-content:\s*space-between/)
  assert.match(css, /\.game-metric-card\s*\{[^}]*white-space:\s*nowrap/)
  assert.match(css, /\.game-metric-card strong\s*\{[^}]*margin-top:\s*0/)
  assert.match(css, /\.game-stats-record-columns\s*\{[^}]*margin-top:\s*(?:1[6-9]|[2-9]\d)px/)
})

test('mascot settings use the same white card treatment as the other settings panels', async () => {
  const css = await source('assets/styles/mascot-settings.css')

  assert.match(css, /\.mascot-settings-panel\s*\{[^}]*background:\s*#fff/)
  assert.match(css, /\.mascot-settings-panel\s*\{[^}]*padding:\s*40px/)
  assert.match(css, /\.mascot-settings-panel\s*\{[^}]*border-radius:\s*28px/)
})

test('unfollowed search action is larger and neutral gray', async () => {
  const css = await source('components/search/SearchUserRow.scss')

  assert.match(css, /\.search-user-row__follow\s*\{[\s\S]*width:\s*12\dpx/)
  assert.match(css, /\.search-user-row__follow\s*\{[\s\S]*background:\s*#f[0-9a-f]{5}/i)
  assert.doesNotMatch(css, /\.search-user-row__follow\s*\{[\s\S]*background:\s*#fff0f2/)
})

test('game center separates home, statistics and leaderboard state', async () => {
  const script = await source('scripts/views/GameCenter.js')
  const view = await source('views/GameCenter.vue')

  assert.match(script, /const statsGameCode = ref\('gobang'\)/)
  assert.match(script, /const leaderboardGameCode = ref\('tetris'\)/)
  assert.match(script, /const homeWinRateText = computed/)
  assert.match(script, /statsRequestSequence/)
  assert.match(script, /statsGameCode\.value === 'tetris_pk'[\s\S]*row\.scoreDelta/)
  assert.match(view, /v-model="statsGameCode"/)
  assert.match(view, /v-model="leaderboardGameCode"/)
  assert.doesNotMatch(view, /game-stats-record-head/)
})

test('leaderboard rows render uniform plain avatars and stronger centered titles', async () => {
  const view = await source('views/GameCenter.vue')
  const css = await source('assets/styles/game-center.css')

  assert.match(view, /game-rank-list__avatar/)
  assert.match(view, /row\.avatarUrl \|\| defaultAvatar/)
  assert.match(css, /game-rank-list__avatar[\s\S]*width:\s*40px[\s\S]*height:\s*40px/)
  assert.match(css, /game-dialog-title[\s\S]*font-size:\s*2[6-9]px/)
})

test('match history tables are centered without horizontal scrolling and use gray actions', async () => {
  const gobang = await source('views/GobangGame.vue')
  const jinzi = await source('views/JinziGame.vue')
  const pk = await source('views/TetrisPkGame.vue')
  const css = await source('assets/styles/gobang-game.css')

  assert.match(gobang, /label="结果"[^>]*align="center"/)
  assert.match(jinzi, /label="结果"[^>]*align="center"/)
  assert.match(pk, /label="结束原因"[^>]*align="center"/)
  assert.match(css, /\.gobang-record-table[\s\S]*overflow-x:\s*hidden/)
  assert.match(css, /\.gobang-record-table :deep\(\.el-button\.is-link\)[\s\S]*background:\s*#f[0-9a-f]{5}/i)
})

test('watch lists show player names only and use a light blue watch action', async () => {
  const center = await source('views/GameCenter.vue')
  const pk = await source('views/TetrisPkGame.vue')
  const css = await source('assets/styles/game-center.css')

  assert.doesNotMatch(center, /watchRoomMeta\(roomRow\)/)
  assert.match(center, /game-room-row__watch/)
  assert.match(pk, /redNickname[\s\S]*blueNickname/)
  assert.match(css, /game-room-row__watch[\s\S]*background:\s*#e[a-f\d]{5}/i)
})

test('shell routes suppress the browser root scrollbar', async () => {
  const css = await source('assets/styles/home.css')

  assert.match(css, /html:has\(\.home-xhs-root\)[\s\S]*overflow:\s*hidden/)
  assert.match(css, /body:has\(\.home-xhs-root\)[\s\S]*overflow:\s*hidden/)
})
