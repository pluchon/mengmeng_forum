import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const frontRoot = new URL('../src/', import.meta.url)
const backendRoot = new URL('../../../backend/src/main/java/org/example/forumdemo/', import.meta.url)

async function frontSource(relativePath) {
  return readFile(new URL(relativePath, frontRoot), 'utf8')
}

async function backendSource(relativePath) {
  return readFile(new URL(relativePath, backendRoot), 'utf8')
}

test('shop emoji preview uses an explicit stopped click trigger', async () => {
  const view = await frontSource('components/article/CommentShopEmojiPopover.vue')
  const script = await frontSource('scripts/components/article/CommentShopEmojiPopover.js')

  assert.match(view, /trigger="manual"/)
  assert.match(view, /@click\.stop="togglePreview"/)
  assert.match(script, /function togglePreview\(\)/)
  assert.match(script, /visible\.value = !visible\.value/)
})

test('checkin page removes cumulative trend and today shortcut while keeping intrinsic stat cards', async () => {
  const view = await frontSource('views/Checkin.vue')
  const script = await frontSource('scripts/views/Checkin.js')
  const css = await frontSource('assets/styles/checkin.css')
  const controller = await backendSource('controller/CheckinController.java')
  const service = await backendSource('service/interfaces/checkin/CheckinService.java')

  for (const source of [view, script, controller, service]) {
    assert.doesNotMatch(source, /萌币累计趋势|trendOverlay|openTrend|iconTrend|getMonthTrend/)
  }
  assert.doesNotMatch(view, /回到今天|iconTodayUrl|goTodayCalendar/)
  assert.match(css, /\.checkin-stats-panel\s*\{[^}]*background:\s*transparent\s*!important/s)
  assert.match(css, /\.checkin-stats-grid\s*\{[^}]*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\)/s)
  assert.match(css, /\.checkin-stat-mini\s*\{[^}]*width:\s*100%/s)
  assert.doesNotMatch(css, /\.checkin-stat-mini\s*\{[^}]*aspect-ratio/s)
})

test('creative center uses compact centered heading, inline monthly delta and plain post titles', async () => {
  const view = await frontSource('views/CreativeCenter.vue')
  const css = await frontSource('assets/styles/creative-center.css')

  assert.doesNotMatch(view, /管理你的灵感与创作/)
  assert.doesNotMatch(view, /<router-link[\s\S]*row\.title/)
  assert.match(view, /creative-stat-value-row/)
  assert.match(css, /\.creative-center-title\s*\{[\s\S]*font-weight:\s*700[\s\S]*text-align:\s*center/)
  assert.match(css, /\.creative-stat-value-row\s*\{[\s\S]*justify-content:\s*space-between/)
  assert.match(css, /\.creative-post-date[\s\S]*text-align:\s*center/)
  assert.match(css, /\.creative-post-actions[\s\S]*justify-content:\s*center/)
})

test('vip center removes subtitle and discontinued model marketing', async () => {
  const view = await frontSource('views/VipCenter.vue')
  const service = await backendSource('service/impl/vip/VipCenterServiceImpl.java')

  assert.doesNotMatch(view, /选择适合你的方案/)
  assert.doesNotMatch(`${view}\n${service}`, /Gemini|Claude|gemini|claude/)
  const featureTexts = [...service.matchAll(/feat\("([^"]+)"/g)].map((match) => match[1])
  assert.ok(featureTexts.length > 0)
  featureTexts.forEach((text) => assert.doesNotMatch(text, /（|）|\(|\)/))
})

test('drift bottle layout is constrained for a compact desktop workbench', async () => {
  const css = await frontSource('assets/styles/drift-bottle.css')

  assert.match(css, /\.drift-shell\s*\{[\s\S]*max-width:\s*1180px/)
  assert.match(css, /\.drift-grid\s*\{[\s\S]*grid-template-columns:\s*minmax\(0,\s*5fr\)\s+minmax\(0,\s*7fr\)/)
  assert.match(css, /\.drift-compose \.drift-textarea[\s\S]*min-height:\s*180px/)
  assert.match(css, /\.ocean-waves\s*\{[\s\S]*display:\s*block/)
})
