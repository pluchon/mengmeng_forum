import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = relativePath => readFile(new URL(relativePath, import.meta.url), 'utf8')

const assetPaths = [
  '../src/assets/images/auth/login.png',
  '../src/assets/images/auth/register.png',
  '../src/assets/images/auth/recover.png',
]

for (const relativePath of assetPaths) {
  test(`${relativePath} is a valid PNG image`, async () => {
    const bytes = await readFile(new URL(relativePath, import.meta.url))
    assert.deepEqual([...bytes.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10])
    assert.ok(bytes.length < 4_000_000, 'auth artwork must stay below 4 MB')
  })
}

test('shared auth shell exposes ParticleSea and uses one equal 2:1 card', async () => {
  const shell = await readSource('../src/assets/styles/auth-split-layout.css')
  const signIn = await readSource('../src/views/SignIn.vue')
  const signUp = await readSource('../src/views/SignUp.vue')
  const forgot = await readSource('../src/views/ForgotPassword.vue')

  assert.match(shell, /\.auth-page\s*\{[^}]*background:\s*transparent/s)
  assert.match(shell, /\.auth-card\s*\{[^}]*box-sizing:\s*border-box[^}]*width:\s*min\(1200px,\s*calc\(100vw\s*-\s*64px\)\)[^}]*height:\s*min\(700px,\s*calc\(100vh\s*-\s*48px\)\)/s)
  assert.match(shell, /\.auth-layout\s*\{[^}]*display:\s*grid[^}]*grid-template-columns:\s*minmax\(0,\s*2fr\)\s+minmax\(360px,\s*1fr\)/s)
  assert.doesNotMatch(`${signIn}\n${signUp}\n${forgot}`, /auth-card--tall/)
  assert.doesNotMatch(shell, /\.brand-side\s*\{[^}]*position:\s*absolute/s)
  assert.doesNotMatch(shell, /\.form-side\s*\{[^}]*backdrop-filter/s)
  assert.match(shell, /\.auth-form-body\s*\{[^}]*flex:\s*1\s+1\s+auto[^}]*justify-content:\s*center/s)
  assert.match(shell, /\.image-mask\s*\{[^}]*linear-gradient\([^}]*rgba\(18,\s*20,\s*30,\s*0\.68\)/s)
  assert.doesNotMatch(shell, /background:\s*rgba\(239,\s*241,\s*245,\s*0\.64\)/)
  assert.doesNotMatch(shell, /backdrop-filter:\s*blur\(14px\)/)
  assert.equal((`${signIn}\n${signUp}\n${forgot}`.match(/class="auth-form-body"/g) || []).length, 3)
  assert.match(shell, /\.auth-form-body\s*\{[^}]*padding-bottom:\s*72px/s)
})

test('silent validation keeps a visible red error outline', async () => {
  const shell = await readSource('../src/assets/styles/auth-split-layout.css')

  assert.match(
    shell,
    /\.form-side\s+:deep\(\.el-form-item\.is-error\s+\.el-input__wrapper\)\s*\{[^}]*border-color:\s*var\(--primary-red\)\s*!important[^}]*box-shadow:\s*0\s+0\s+0\s+3px\s+rgba\(255,\s*36,\s*66,\s*0\.1\)\s*!important/s,
  )
})

test('auth pages use the project red theme instead of the rejected brown palette', async () => {
  const stylePaths = [
    '../src/assets/styles/auth-split-layout.css',
    '../src/assets/styles/signin.css',
    '../src/assets/styles/signup.css',
    '../src/assets/styles/forgot.css',
  ]
  const styles = (await Promise.all(stylePaths.map(readSource))).join('\n')

  assert.match(styles, /var\(--primary-red\)/)
  assert.match(styles, /var\(--primary-pale\)/)
  assert.doesNotMatch(styles, /#a45e46|#934f3a|#98543f|#8f513d|#713b2d/i)
})

test('SignIn has four explicit login methods and the policy before its primary action', async () => {
  const source = await readSource('../src/views/SignIn.vue')
  const panes = [...source.matchAll(/<el-tab-pane\s+label="([^"]+)"\s+name="([^"]+)"/g)]

  assert.deepEqual(panes.map(match => match.slice(1)), [
    ['短信验证码', 'phone'],
    ['账号密码', 'userName'],
    ['邮箱验证码', 'emailCode'],
    ['邮箱密码', 'emailPassword'],
  ])
  assert.match(source, /<h1 class="auth-brand-title">\{\{ SITE_NAME \}\}<\/h1>/)
  assert.match(source, /<h2 class="auth-scene-copy__title">遇见同好<\/h2>/)
  assert.match(source, /一个交友、发帖与娱乐兼具的社区。/)
  assert.equal((source.match(/class="password-input-row"/g) || []).length, 2)
  assert.equal((source.match(/class="password-forgot-button"/g) || []).length, 2)
  assert.doesNotMatch(source, /email-mode-switch|emailSubTab|欢迎回来|LUNTAN COMMUNITY/)
  assert.equal((source.match(/:show-message="false"/g) || []).length, 4)
  assert.equal((source.match(/type="primary"/g) || []).length, 1)
  assert.ok(source.indexOf('class="policy-bar"') < source.indexOf('type="primary"'))
})

test('SignIn script maps the four tabs to the existing authentication paths', async () => {
  const source = await readSource('../src/scripts/views/SignIn.js')

  assert.match(source, /const emailCodeFormRef = ref\(\)/)
  assert.match(source, /const emailPasswordFormRef = ref\(\)/)
  assert.match(source, /tab === 'emailCode'/)
  assert.match(source, /tab === 'emailPassword'/)
  assert.match(source, /verifyCaptcha\('MAIL_LOGIN'\)/)
  assert.match(source, /verifyCaptcha\('USER_LOGIN'\)/)
  assert.match(source, /emailCode:\s*\[[\s\S]*?len:\s*6/)
  assert.match(source, /emailPassword:\s*\[[\s\S]*?min:\s*6/)
  assert.doesNotMatch(source, /ElMessage\.warning\('请输入 6 位邮箱验证码'\)/)
  assert.doesNotMatch(source, /ElMessage\.warning\('密码不能少于 6 位'\)/)
  assert.doesNotMatch(source, /emailSubTab|emailFormRef/)
})

test('SignUp uses the same card with concise Chinese content and one primary action', async () => {
  const source = await readSource('../src/views/SignUp.vue')

  assert.match(source, /REGISTER_WEBP_URL as registerScene/)
  assert.match(source, /<h1 class="auth-page-title auth-page-title--standalone">创建账号<\/h1>/)
  assert.match(source, /<h2 class="auth-scene-copy__title">加入萌部落<\/h2>/)
  assert.match(source, /认识新朋友，也分享你的兴趣与日常。/)
  assert.doesNotMatch(source, /SITE_NAME|auth-brand-title|JOIN THE COMMUNITY|A PLACE TO BELONG|auth-card--tall/)
  assert.equal((source.match(/:show-message="false"/g) || []).length, 1)
  assert.equal((source.match(/type="primary"/g) || []).length, 1)
  assert.ok(source.indexOf('class="form-policy"') < source.indexOf('type="primary"'))
})

test('ForgotPassword uses the same card and silent field validation', async () => {
  const source = await readSource('../src/views/ForgotPassword.vue')
  const script = await readSource('../src/scripts/views/ForgotPassword.js')

  assert.match(source, /FIND_WEBP_URL as recoverScene/)
  assert.match(source, /<h1 class="auth-page-title auth-page-title--standalone">找回密码<\/h1>/)
  assert.match(source, /<h2 class="auth-scene-copy__title">重新出发<\/h2>/)
  assert.match(source, /验证账号后，很快就能回到社区。/)
  assert.doesNotMatch(source, /SITE_NAME|auth-brand-title|ACCOUNT RECOVERY|WELCOME BACK SOON/)
  assert.match(source, /ref="recoverFormRef"/)
  assert.match(source, /:rules="rules"/)
  assert.equal((source.match(/:show-message="false"/g) || []).length, 1)
  assert.match(source, /prop="account"/)
  assert.match(source, /prop="code"/)
  assert.match(source, /prop="newPassword"/)
  assert.doesNotMatch(script, /ElMessage\.warning\('(手机号|邮箱)格式不正确'\)/)
  assert.equal((source.match(/type="primary"/g) || []).length, 1)
})

test('auth pages use the configured OSS webp artwork', async () => {
  const clientOss = await readSource('../src/utils/clientOss.js')
  const signIn = await readSource('../src/views/SignIn.vue')

  assert.match(clientOss, /forum_images\/client\/webp\/login\.webp/)
  assert.match(clientOss, /forum_images\/client\/webp\/forget\.webp/)
  assert.match(clientOss, /forum_images\/client\/webp\/register\.webp/)
  assert.match(signIn, /LOGIN_WEBP_URL as loginScene/)
})

test('authentication ocean advances at a low-frequency phase step', async () => {
  const source = await readSource('../src/scripts/components/common/AuthWallThree.js')
  const phaseStep = source.match(/const AUTH_WAVE_PHASE_STEP = (\d+(?:\.\d+)?)/)

  assert.ok(phaseStep, 'auth ocean should expose a named phase step')
  assert.ok(Number(phaseStep[1]) > 0)
  assert.ok(Number(phaseStep[1]) <= 0.015)
  assert.match(source, /count \+= AUTH_WAVE_PHASE_STEP/)
})

test('policy pages omit ICP and keep the original reading layout', async () => {
  const privacy = await readSource('../src/views/Privacy.vue')
  const terms = await readSource('../src/views/Terms.vue')
  const styles = await readSource('../src/assets/styles/user.css')

  for (const source of [privacy, terms]) {
    assert.doesNotMatch(source, /SiteIcpLink|privacy-footer/)
    assert.doesNotMatch(source, /policy-toolbar/)
  }
  assert.doesNotMatch(styles, /\.policy-toolbar\s*\{/)
  assert.match(styles, /\.hero-section\s*\{[^}]*text-align:\s*center/s)
  assert.match(styles, /\.privacy-container\s*\{[^}]*max-width:\s*1000px/s)
})

test('auth routes remove detached mascot DOM that could cover the card', async () => {
  const source = await readSource('../src/scripts/App.js')

  assert.match(source, /watch\(\s*\[isAuthPage, isGamePage\]/s)
  assert.match(source, /\(\[authPage, gamePage\]\)\s*=>\s*\{\s*if \(!authPage && !gamePage\) return/s)
  assert.match(source, /new MutationObserver\(cleanupMascotDom\)/)
  assert.match(source, /mascotDomObserver\.observe\(document\.body,\s*\{\s*childList:\s*true,\s*subtree:\s*true\s*\}\)/s)
  assert.match(source, /mascotDomObserver\.disconnect\(\)/)
})
