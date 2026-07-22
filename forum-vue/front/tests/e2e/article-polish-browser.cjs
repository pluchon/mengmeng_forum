const fs = require('node:fs')
const { chromium } = require(process.env.PW_MODULE)

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:5174'
const outputDir = 'C:/JavaCode/items/luntan/output/playwright/article-polish'

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function run() {
  fs.mkdirSync(outputDir, { recursive: true })
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
  const pageErrors = []
  page.on('pageerror', (error) => pageErrors.push(error.message))

  const login = await page.request.post(`${baseUrl}/user/login`, {
    headers: { 'X-Captcha-Ticket': process.env.LOGIN_TICKET },
    data: { userName: process.env.LOGIN_USERNAME, password: process.env.LOGIN_PASSWORD },
  })
  const loginPayload = await login.json().catch(() => null)
  const token = login.headers()['authorization']
  assert(token, `one-time login failed, code=${String(loginPayload?.code)}`)
  await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' })
  await page.evaluate((jwt) => localStorage.setItem('user', JSON.stringify({ token: jwt })), token)

  await page.route('**/favorite/article/save*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: null }) }))
  await page.route('**/favorite/article/cancel*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: null }) }))
  await page.route('**/article/submitForAudit*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: 'browser-regression-only' }) }))
  await page.route('**/article/updateArticleCoverByUrl*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data: null }) }))

  async function gotoDetail(id) {
    await page.goto(`${baseUrl}/article/${id}`, { waitUntil: 'domcontentloaded' })
    await page.locator('.red-detail-container').waitFor({ state: 'visible', timeout: 15000 })
    await page.waitForTimeout(300)
  }

  async function box(locator) {
    const value = await locator.boundingBox()
    return value && Object.fromEntries(Object.entries(value).map(([key, number]) => [key, Math.round(number)]))
  }

  async function style(locator, properties) {
    return locator.evaluate((element, names) => {
      const computed = getComputedStyle(element)
      return Object.fromEntries(names.map((name) => [name, computed[name]]))
    }, properties)
  }

  await gotoDetail(1)
  const tagsBefore = await page.locator('.content-meta .article-detail-tag').count()
  const toggle = page.locator('.article-tags-toggle')
  await toggle.waitFor({ state: 'visible' })
  const toggleText = (await toggle.textContent()).trim()
  await toggle.click()
  const tagsAfter = await page.locator('.content-meta .article-detail-tag').count()
  assert(tagsBefore === 2 && tagsAfter === 5, `tag collapse failed: ${tagsBefore}/${tagsAfter}`)

  await page.getByRole('button', { name: '生成摘要', exact: true }).click()
  const summary = await page.locator('.ai-summary-textarea').inputValue()
  assert(summary === '内容过少，无法生成摘要', 'short summary copy mismatch')

  const upload = page.locator('.comment-upload-btn')
  const input = page.locator('.comment-input-wrap')
  const send = page.locator('.comment-send-btn')
  const composer = {
    upload: await box(upload),
    input: await box(input),
    send: await box(send),
    sendText: (await send.textContent()).trim(),
    sendStyle: await style(send, ['backgroundColor', 'color']),
  }
  assert(composer.sendText === '发送', 'send button copy mismatch')
  assert(composer.upload.x < composer.input.x && composer.input.x < composer.send.x, 'composer order mismatch')
  const footerCount = await page.locator('.action-btns-row .action-item').count()
  assert(footerCount === 3, 'duplicate footer comment action remains')

  await page.locator('.action-btns-row .action-item').nth(1).click()
  const favorite = page.locator('.favorite-dialog')
  await favorite.waitFor({ state: 'visible' })
  const favoriteInfo = {
    title: (await favorite.locator('.el-dialog__title').textContent()).trim(),
    buttonCount: await favorite.locator('.el-dialog__footer .el-button').count(),
    confirmText: (await favorite.locator('.favorite-dialog-confirm').textContent()).trim(),
    defaultMarkerCount: await page.getByText(/（默认）|\(默认\)/).count(),
  }
  assert(favoriteInfo.title === '添加到收藏夹', 'favorite title mismatch')
  assert(favoriteInfo.buttonCount === 1 && favoriteInfo.confirmText === '确定', 'favorite actions mismatch')
  assert(favoriteInfo.defaultMarkerCount === 0, 'favorite default marker remains')
  await page.screenshot({ path: `${outputDir}/detail-favorite.png` })
  await favorite.locator('.el-dialog__headerbtn').click()

  await gotoDetail(2)
  const play = page.locator('.detail-video-player__btn--play')
  await play.waitFor({ state: 'visible' })
  const videoInfo = {
    text: (await play.textContent()).trim(),
    svgCount: await play.locator('svg').count(),
    style: await style(play, ['backgroundColor', 'borderRadius', 'boxShadow']),
  }
  assert(videoInfo.text === '' && videoInfo.svgCount === 1, 'video play control is not icon-only')
  assert(videoInfo.style.backgroundColor === 'rgba(0, 0, 0, 0)', 'video play control background remains')
  await page.screenshot({ path: `${outputDir}/detail-video.png` })

  await page.goto(`${baseUrl}/article/13/cover`, { waitUntil: 'domcontentloaded' })
  await page.locator('.cover-setup-card').waitFor({ state: 'visible', timeout: 15000 })
  const draft = page.getByRole('button', { name: '保存为草稿', exact: true })
  const publish = page.getByRole('button', { name: '发布', exact: true })
  const coverInfo = {
    promptLabelCount: await page.getByText('封面描述词', { exact: true }).count(),
    draftIconCount: await draft.locator('svg, img').count(),
    publishIconCount: await publish.locator('svg, img').count(),
  }
  assert(coverInfo.promptLabelCount === 0, 'cover prompt label remains')
  assert(coverInfo.draftIconCount === 0 && coverInfo.publishIconCount === 0, 'cover action icon remains')
  await publish.click()
  const audit = page.locator('.audit-submit-msgbox')
  await audit.waitFor({ state: 'visible' })
  const cancel = audit.getByRole('button', { name: '取消', exact: true })
  const confirm = audit.getByRole('button', { name: '确定', exact: true })
  const auditInfo = {
    title: (await audit.locator('.el-message-box__title').textContent()).trim(),
    closeCount: await audit.locator('.el-message-box__headerbtn').count(),
    cancel: await box(cancel),
    confirm: await box(confirm),
    flexDirection: await audit.locator('.el-message-box__btns').evaluate((element) => getComputedStyle(element).flexDirection),
  }
  assert(auditInfo.title === '是否确认发布', 'publish dialog title mismatch')
  assert(auditInfo.closeCount === 0, 'publish dialog close button remains')
  assert(auditInfo.flexDirection === 'row', 'publish actions are not inline')
  assert(Math.abs(auditInfo.cancel.width - auditInfo.confirm.width) <= 2, 'publish actions are not equal width')
  await page.screenshot({ path: `${outputDir}/publish-confirm.png` })
  await cancel.click()

  assert(pageErrors.length === 0, `page errors: ${pageErrors.join(' | ')}`)
  console.log(JSON.stringify({
    authenticated: true,
    tags: { before: tagsBefore, toggleText, after: tagsAfter },
    summary,
    composer,
    footerCount,
    favorite: favoriteInfo,
    video: videoInfo,
    cover: coverInfo,
    audit: auditInfo,
    screenshots: [
      `${outputDir}/detail-favorite.png`,
      `${outputDir}/detail-video.png`,
      `${outputDir}/publish-confirm.png`,
    ],
  }))
  await browser.close()
}

run().catch((error) => {
  console.error(JSON.stringify({ error: error.message, stack: error.stack }))
  process.exit(1)
})
