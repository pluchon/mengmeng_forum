import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const frontRoot = new URL('../src/', import.meta.url)
const backendRoot = new URL('../../../backend/src/main/java/org/example/forumdemo/', import.meta.url)

async function source(root, relativePath) {
  return readFile(new URL(relativePath, root), 'utf8')
}

test('lottery sold-out jackpot state and prize card alignment are explicit', async () => {
  const view = await source(frontRoot, 'views/LotteryView.vue')
  const script = await source(frontRoot, 'scripts/views/LotteryView.js')
  const style = await source(frontRoot, 'assets/styles/lottery.css')
  const service = await source(backendRoot, 'service/impl/lottery/LotteryServiceImpl.java')

  assert.match(script, /const jackpotAvailable = computed/)
  assert.match(view, /jackpotAvailable\s*\?\s*'下次必得'\s*:\s*'头奖已发完'/)
  assert.match(view, /lottery-prize-chip__name[\s\S]*lottery-prize-chip__stock/)
  assert.match(style, /\.lottery-pool-chip\s*\{[\s\S]*flex-direction:\s*column[\s\S]*align-items:\s*flex-start/)
  assert.match(style, /\.lottery-prize-chip\.jackpot\s*\{[\s\S]*?align-items:\s*flex-start[\s\S]*?text-align:\s*left/)
  assert.doesNotMatch(style, /\.lottery-prize-chip\.jackpot\s*\{[^}]*padding-top:\s*32px/)
  assert.match(style, /\.lottery-pool-chip\.jackpot::before\s*\{[^}]*left:\s*auto[^}]*right:\s*10px/)
  assert.match(service, /nextPityAfterMiss\(pity\)/)
})

test('assistant sessions expose a hover delete action backed by soft deletion', async () => {
  const view = await source(frontRoot, 'components/mascot/MascotDock.vue')
  const script = await source(frontRoot, 'scripts/components/mascot/MascotDock.js')
  const style = await source(frontRoot, 'assets/styles/mascot-dock.css')
  const api = await source(frontRoot, 'api/mascot.js')
  const controller = await source(backendRoot, 'controller/MascotController.java')
  const service = await source(backendRoot, 'service/impl/mascot/CompanionMemoryServiceImpl.java')

  assert.match(view, /mascot-fs-session-item__delete/)
  assert.match(view, /@click\.stop="deleteSession\(sess\)"/)
  assert.match(view, /mascot-fs-sidebar__header[\s\S]*mascot-new-session-icon/)
  assert.match(script, /ElMessageBox\.confirm/)
  assert.match(script, /async function deleteSession\(session\)/)
  assert.match(script, /selectLocalSession\(nextSession\.id, false\)/)
  assert.match(style, /\.mascot-fs-session-item__delete\s*\{[\s\S]*opacity:\s*0/)
  assert.match(style, /\.mascot-fs-session-item:hover[\s\S]*mascot-fs-session-item__delete[\s\S]*opacity:\s*1/)
  assert.match(style, /\.mascot-fs-sidebar\s*\{[^}]*overflow-x:\s*hidden/)
  assert.match(style, /\.mascot-fs-session-item\s*\{[^}]*box-sizing:\s*border-box/)
  assert.match(style, /\.mascot-new-session-icon\s*\{[^}]*border:\s*0[^}]*background:\s*transparent/)
  assert.match(api, /method:\s*'delete'/)
  assert.match(controller, /@DeleteMapping\("\/companion\/sessions\/\{sessionId\}"\)/)
  assert.match(service, /deleteSession\(Long userId, Long sessionId\)/)
  assert.match(service, /LambdaUpdateWrapper<ForumCompanionMessage>/)
  assert.match(service, /LambdaUpdateWrapper<ForumCompanionSession>/)
})

test('daily hot ranking persists snapshots and renders one plain red up arrow', async () => {
  const view = await source(frontRoot, 'views/HomeFeed.vue')
  const style = await source(frontRoot, 'assets/styles/home.css')
  const keys = await source(backendRoot, 'common/constant/ForumRedisKeys.java')
  const service = await source(backendRoot, 'service/impl/article/ArticleHotRankingServiceImpl.java')
  const redisOps = await source(backendRoot, 'service/impl/article/HotArticleRedisOps.java')
  const vo = await source(backendRoot, 'entity/vo/article/HotArticleListItemVO.java')

  assert.match(keys, /HOT_ARTICLES_METRIC_BASELINE/)
  assert.match(keys, /HOT_ARTICLES_PERIOD_SCORE/)
  assert.match(keys, /HOT_ARTICLES_TREND/)
  assert.match(service, /computePeriodScore/)
  assert.match(service, /updateDailyTrendSnapshots/)
  assert.match(redisOps, /replaceDailyTrendSnapshots/)
  assert.match(vo, /HotArticleTrendDirection trendDirection/)
  assert.match(view, /entry\.trendDirection === 'UP'/)
  assert.doesNotMatch(view, /entry\.trendDirection === 'DOWN'/)
  assert.match(style, /\.home-hot-trend\.is-up[\s\S]*#(?:e5484d|[a-f0-9]{6})/i)
  const trendBlock = style.match(/\.home-hot-trend\s*\{[\s\S]*?\n\}/)?.[0] || ''
  assert.doesNotMatch(trendBlock, /background|border-radius|width|height/)
  assert.doesNotMatch(style, /\.home-hot-trend\.is-down/)
})
