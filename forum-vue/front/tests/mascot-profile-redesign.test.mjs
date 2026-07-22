import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = relativePath => readFile(new URL(relativePath, import.meta.url), 'utf8')

test('mascot dialog removes pointer passthrough in favor of persistent settings visibility', async () => {
  const view = await readSource('../src/components/mascot/MascotDock.vue')
  const store = await readSource('../src/stores/mascotUi.js')
  const settings = await readSource('../src/views/Settings.vue')

  assert.doesNotMatch(view, /看板娘鼠标穿透|pointerPassThrough/)
  assert.match(store, /mascot_visible_v1/)
  assert.match(store, /setVisible/)
  assert.match(settings, /看板娘设置/)
  assert.doesNotMatch(view, /mascot-dlg-head__nickname|mascot-dlg-head__guest/)
})

test('profile no longer renders the detached VIP crown', async () => {
  const view = await readSource('../src/views/Profile.vue')
  const script = await readSource('../src/scripts/views/Profile.js')

  assert.doesNotMatch(view, /profile-vip-crown|vipCrownSrc/)
  assert.doesNotMatch(script, /showVipBadge/)
})
