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

test('detail footer removes the duplicate comment action', async () => {
  const view = await frontSource('views/ArticleDetail.vue')
  const actionBlock = view.match(/<div class="action-btns action-btns-row">([\s\S]*?)<\/div>\s*<\/div>\s*<\/div>/)?.[1] || ''

  assert.doesNotMatch(actionBlock, /replyCountDisplay/)
  assert.doesNotMatch(actionBlock, /ChatDotRound/)
})

test('comment composer places image and send actions outside the input', async () => {
  const view = await frontSource('views/ArticleDetail.vue')

  assert.match(view, /comment-composer[\s\S]*comment-upload-btn[\s\S]*comment-input-wrap[\s\S]*comment-send-btn/)
  assert.match(view, /comment-send-btn[\s\S]*>\s*发送\s*<\/button>/)
  assert.doesNotMatch(view, /comment-send-btn[\s\S]{0,180}sendIconUrl/)
})

test('article tags stay collapsed after two items until explicitly expanded', async () => {
  const view = await frontSource('views/ArticleDetail.vue')
  const entry = await frontSource('views/ArticleDetail.js')
  const script = await frontSource('scripts/views/ArticleDetail.js')

  assert.match(view, /v-for="t in visibleArticleTags"/)
  assert.match(view, /article-tags-toggle[\s\S]*tagsExpanded/)
  assert.match(entry, /visibleArticleTags/)
  assert.match(entry, /hiddenArticleTagCount/)
  assert.match(entry, /tagsExpanded/)
  assert.match(entry, /toggleArticleTags/)
  assert.match(script, /const tagsExpanded = ref\(false\)/)
  assert.match(script, /const visibleArticleTags = computed[\s\S]*slice\(0, 2\)/)
})

test('AI guide uses the concise short-content message', async () => {
  const script = await frontSource('scripts/views/ArticleDetail.js')
  const css = await frontSource('assets/styles/article.css')

  assert.match(script, /内容过少，无法生成摘要/)
  assert.match(css, /\.ai-summary-box\s*\{[\s\S]*border:\s*1px solid rgba\(255, 36, 66/)
  assert.match(css, /\.ai-summary-box::before[\s\S]*background:\s*#ff2442/)
})

test('video play control is visually icon-only', async () => {
  const view = await frontSource('components/article/ArticleDetailVideo.vue')
  const css = await frontSource('assets/styles/article-detail-video.css')

  assert.match(view, /detail-video-player__btn--play/)
  assert.match(css, /\.detail-video-player__btn--play\s*\{[\s\S]*background:\s*transparent/)
  assert.match(css, /\.detail-video-player__btn--play\s*\{[\s\S]*border-radius:\s*0/)
})

test('cover page uses concise publishing labels and confirmation dialog', async () => {
  const view = await frontSource('views/ArticleCoverSetup.vue')
  const script = await frontSource('scripts/views/ArticleCoverSetup.js')
  const auditSubmit = await frontSource('composables/useArticleAuditSubmit.js')
  const publishingFlow = `${script}\n${auditSubmit}`

  assert.doesNotMatch(view, /封面描述词|iconDraft|CircleCheck/)
  assert.match(view, />\s*保存为草稿\s*</)
  assert.match(view, /isPublished \? '保存封面' : '发布'/)
  assert.match(publishingFlow, /'是否确认发布'/)
  assert.match(publishingFlow, /confirmButtonText:\s*'确定'/)
  assert.match(publishingFlow, /showClose:\s*false/)
  const finishFlow = script.match(/async function finishAndSubmitAudit\(\)[\s\S]*?\n  \}/)?.[0] || ''
  assert.ok(finishFlow.indexOf('confirmArticlePublish') < finishFlow.indexOf('persistCoverThen(true)'))
  assert.match(script, /submitArticleForAuditWithPrompt\(articleId, \{ confirmed: true \}\)/)
})

test('article audit submission is station-message only', async () => {
  const composable = await frontSource('composables/useArticleAuditSubmit.js')
  const controller = await backendSource('controller/ArticleController.java')
  const auditService = await backendSource('service/impl/article/ArticleAuditServiceImpl.java')

  assert.doesNotMatch(composable, /notifyEmail|邮件|useUserStore/)
  assert.match(composable, /审核结果可以在消息中心查看/)
  assert.doesNotMatch(controller, /getNotifyEmail/)
  assert.doesNotMatch(auditService, /MailUtil|sendAuditEmail|getAuditNotifyEmail/)
})

test('shop emoji opens a preview popover before navigating to its pack', async () => {
  const mediaView = await frontSource('components/article/CommentReplyMediaDisplay.vue')
  const popoverView = await frontSource('components/article/CommentShopEmojiPopover.vue')
  const popoverScript = await frontSource('scripts/components/article/CommentShopEmojiPopover.js')

  assert.match(mediaView, /CommentShopEmojiPopover/)
  assert.doesNotMatch(mediaView, /comment-media-emoji-badge/)
  assert.match(popoverView, /comment-shop-emoji-popover__preview/)
  assert.match(popoverView, /comment-shop-emoji-popover__pack[\s\S]*detail\.coverUrl[\s\S]*detail\.name/)
  assert.match(popoverScript, /getShopDetail/)
  assert.match(popoverScript, /emit\('open-shop'/)
})

test('favorite dialog keeps one full-width red confirmation action', async () => {
  const view = await frontSource('views/ArticleDetail.vue')

  assert.match(view, /title="添加到收藏夹"/)
  assert.doesNotMatch(view, /（默认）|不选则会收藏到默认收藏夹/)
  assert.match(view, /favorite-dialog-confirm[\s\S]*>确定<\/el-button>/)
  assert.doesNotMatch(view, /favoriteDialogVisible = false">取消/)
})

test('every nested reply with a target user shows a mention', async () => {
  const view = await frontSource('components/article/SubReplyArea.vue')
  const script = await frontSource('scripts/components/article/SubReplyArea.js')

  assert.match(view, /v-if="shouldShowReplyMention\(sub\)"/)
  assert.doesNotMatch(script, /articleAuthorId/)
  assert.match(script, /return Boolean\(sub\?\.replyUserNickname\)/)
})
