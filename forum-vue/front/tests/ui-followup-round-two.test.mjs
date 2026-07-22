import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'
import test from 'node:test'

const frontRoot = new URL('../src/', import.meta.url)
const backendJavaRoot = new URL('../../../backend/src/main/java/org/example/forumdemo/', import.meta.url)
const backendResourceRoot = new URL('../../../backend/src/main/resources/', import.meta.url)
const packageRoot = new URL('../../../nginx/package/', import.meta.url)
const aiServerRoot = new URL('../../../ai-server/', import.meta.url)

async function source(root, relativePath) {
  return readFile(new URL(relativePath, root), 'utf8')
}

async function pathExists(root, relativePath) {
  try {
    await access(new URL(relativePath, root))
    return true
  } catch {
    return false
  }
}

test('shop emoji popover is teleported above the article detail overlay', async () => {
  const view = await source(frontRoot, 'components/article/CommentShopEmojiPopover.vue')

  assert.match(view, /:teleported="true"/)
  assert.match(view, /:popper-style="\{ zIndex: 4200 \}"/)
})

test('direct floor replies omit mentions while nested targets keep them', async () => {
  const script = await source(frontRoot, 'scripts/views/ArticleDetail.js')
  const floorFlow = script.match(/function startReplyToFloor\(item\) \{[\s\S]*?\n  \}/)?.[0] || ''
  const nestedFlow = script.match(/function startReplyToSub\(payload\) \{[\s\S]*?\n  \}/)?.[0] || ''

  assert.match(floorFlow, /replyUserId:\s*null/)
  assert.match(floorFlow, /showMention:\s*false/)
  assert.match(nestedFlow, /replyUserId:\s*payload\.replyUserId/)
  assert.match(nestedFlow, /showMention:\s*true/)
  assert.match(script, /replyTarget\.value\.showMention\s*\?\s*`回复给 @\$\{nickname\}`\s*:\s*`回复给 \$\{nickname\}`/)
})

test('checkin summary uses three equal cards with restrained visual styling', async () => {
  const checkinView = await source(frontRoot, 'views/Checkin.vue')
  const checkinCss = await source(frontRoot, 'assets/styles/checkin.css')
  const driftCss = await source(frontRoot, 'assets/styles/drift-bottle.css')
  const statBlock = checkinCss.match(/\.checkin-stat-mini\s*\{[\s\S]*?\n\}/)?.[0] || ''
  const panelBlock = checkinCss.match(/\.checkin-stats-panel\s*\{[\s\S]*?\n\}/)?.[0] || ''
  const gridBlock = checkinCss.match(/\.checkin-stats-grid\s*\{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(gridBlock, /display:\s*grid/)
  assert.match(gridBlock, /grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\)/)
  assert.match(statBlock, /width:\s*100%/)
  assert.match(statBlock, /height:\s*auto/)
  assert.match(statBlock, /box-shadow:/)
  assert.match(statBlock, /radial-gradient/)
  assert.doesNotMatch(statBlock, /aspect-ratio/)
  assert.match(panelBlock, /background:\s*transparent\s*!important/)
  assert.doesNotMatch(checkinView, /checkin-stat-mini-bg|checkin-stat-mini-mask|statCardBgUrl/)
  assert.doesNotMatch(driftCss, /\.ocean-waves\s*\{[\s\S]{0,100}display:\s*none/)
  assert.match(driftCss, /\.ocean-waves\s*\{[\s\S]*height:\s*100px/)
})

test('lottery page removes demo, heat chart and surprise interaction', async () => {
  const view = await source(frontRoot, 'views/LotteryView.vue')
  const script = await source(frontRoot, 'scripts/views/LotteryView.js')
  const api = await source(frontRoot, 'api/lottery.js')

  for (const content of [view, script, api]) {
    assert.doesNotMatch(content, /演示说明|近期开奖热度|点我看看|surprise-bonus|LotterySurprise|claimLotterySurpriseBonus/)
  }
  assert.doesNotMatch(script, /barOption|prizeWinHeat|LOTTERY_DEMO_NOTICE|surprisePhase/)
  assert.match(view, /lottery-core-rules/)
  assert.doesNotMatch(view, /lottery-cost-rules/)
})

test('lottery backend no longer exposes heat or page surprise code', async () => {
  const controller = await source(backendJavaRoot, 'controller/LotteryController.java')
  const service = await source(backendJavaRoot, 'service/interfaces/lottery/LotteryService.java')
  const implementation = await source(backendJavaRoot, 'service/impl/lottery/LotteryServiceImpl.java')
  const mapper = await source(backendJavaRoot, 'mapper/LotteryDrawRecordMapper.java')
  const infoVo = await source(backendJavaRoot, 'entity/vo/lottery/LotteryActivityInfoVO.java')
  const businessConstants = await source(backendJavaRoot, 'common/constant/ForumBusinessConstants.java')
  const userEntity = await source(backendJavaRoot, 'entity/db/User.java')
  const userSessionVo = await source(backendJavaRoot, 'entity/vo/user/UserSessionVO.java')
  const createSql = await source(backendResourceRoot, 'sql/create.sql')
  const packageCreateSql = await source(packageRoot, 'sql/create.sql')

  for (const content of [controller, service, implementation, mapper, infoVo, businessConstants]) {
    assert.doesNotMatch(content, /LotteryPrizeHeat|PrizeWinHeat|selectHeatByActivity|LotterySurprise|surpriseBonus|LOTTERY_PAGE_SURPRISE/)
  }
  for (const content of [userEntity, userSessionVo, createSql, packageCreateSql]) {
    assert.doesNotMatch(content, /lotterySurpriseClaimed|lottery_surprise_claimed/)
  }
  assert.equal(await pathExists(backendJavaRoot, 'entity/vo/lottery/LotteryPrizeHeatVO.java'), false)
  assert.equal(await pathExists(backendJavaRoot, 'entity/vo/lottery/LotterySurpriseClaimVO.java'), false)
})

test('python entry adds its own directory before importing local modules', async () => {
  const main = await source(aiServerRoot, 'main.py')
  const pathSetupIndex = main.indexOf('sys.path.insert(0, str(_SCRIPT_DIR))')
  const localImportIndex = main.indexOf('from api import api as api_blueprint')

  assert.match(main, /from pathlib import Path/)
  assert.ok(pathSetupIndex >= 0)
  assert.ok(localImportIndex > pathSetupIndex)
})
