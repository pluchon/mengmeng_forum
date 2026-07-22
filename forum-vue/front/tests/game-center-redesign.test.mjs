import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = relativePath => readFile(new URL(relativePath, import.meta.url), 'utf8')

test('game leaderboard only keeps the two tetris modes with requested labels', async () => {
  const view = await readSource('../src/views/GameCenter.vue')
  const api = await readSource('../src/api/game.js')

  const leaderboard = view.slice(view.indexOf('v-model="leaderboardVisible"'), view.indexOf('v-model="statsVisible"'))
  assert.doesNotMatch(leaderboard, /五子棋|井字棋/)
  assert.match(leaderboard, /俄罗斯方块/)
  assert.match(leaderboard, /俄罗斯方块PK/)
  assert.doesNotMatch(api, /getGobangLeaderboard|getJinziLeaderboard/)
})

test('game stats use compact columns and tetris highest score', async () => {
  const view = await readSource('../src/views/GameCenter.vue')

  assert.match(view, /最高得分/)
  assert.match(view, /游戏判决结果/)
  assert.match(view, /游戏结束原因/)
  assert.match(view, /积分加减情况/)
  assert.match(view, /俄罗斯方块PK/)
})

test('leaderboard requests pass real backend page parameters', async () => {
  const script = await readSource('../src/scripts/views/GameCenter.js')

  assert.match(script, /pageNum:\s*leaderboardPage\.value/)
  assert.match(script, /pageSize:\s*leaderboardPageSize\.value/)
  assert.match(script, /leaderboardTotal\.value\s*=\s*Number\(res\.data\.total\)/)
  assert.doesNotMatch(script, /requestSize\s*=|rows\.slice\(start, end\)/)
})
