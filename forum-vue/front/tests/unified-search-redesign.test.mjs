import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = relativePath => readFile(new URL(relativePath, import.meta.url), 'utf8')

test('unified search composes dedicated article and user components without banner copy', async () => {
  const view = await readSource('../src/views/UnifiedSearchFeed.vue')
  const script = await readSource('../src/scripts/views/UnifiedSearchFeed.js')

  assert.match(view, /<SearchArticleCard/)
  assert.match(view, /<SearchUserRow/)
  assert.match(view, /style scoped lang="scss" src="\.\/UnifiedSearchFeed\.scss"/)
  assert.doesNotMatch(`${view}\n${script}`, /bannerText|unified-search-nav__hint/)
})

test('search article card reuses question semantics from the home feed', async () => {
  const view = await readSource('../src/components/search/SearchArticleCard.vue')
  const script = await readSource('../src/components/search/SearchArticleCard.js')

  assert.match(view, /note-card--question/)
  assert.match(view, /question-card-status/)
  assert.match(view, /question-answer-count/)
  assert.match(script, /isQuestionArticle/)
  assert.match(script, /questionStatusLabel/)
})

test('search user row presents aligned stats and explicit follow states', async () => {
  const view = await readSource('../src/components/search/SearchUserRow.vue')

  assert.match(view, /关注数：\{\{ user\.followingCount/)
  assert.match(view, /粉丝数：\{\{ user\.followerCount/)
  assert.match(view, /user\.isFollowing \? '已关注' : '关注'/)
  assert.match(view, /@click\.stop="emitToggleFollow"/)
})
