import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const frontRoot = new URL('../src/', import.meta.url)

async function source(relativePath) {
  return readFile(new URL(relativePath, frontRoot), 'utf8')
}

test('shop emoji uses a clickable popover trigger before pack navigation', async () => {
  const view = await source('components/article/CommentShopEmojiPopover.vue')
  const mediaView = await source('components/article/CommentReplyMediaDisplay.vue')

  assert.match(view, /v-model:visible="visible"/)
  assert.match(view, /trigger="manual"/)
  assert.match(view, /@click\.stop="togglePreview"/)
  assert.match(view, /@show="loadDetail"/)
  assert.match(mediaView, /@open-shop="\(id\) => emit\('open-shop', id\)"/)
  assert.doesNotMatch(mediaView, /comment-media-emoji-badge|表情包/)
})

test('article detail loads and renders backend tags', async () => {
  const view = await source('views/ArticleDetail.vue')
  const entry = await source('views/ArticleDetail.js')
  const script = await source('scripts/views/ArticleDetail.js')

  assert.match(script, /articleTags\.value\s*=\s*Array\.isArray\(res\.data\.tags\)/)
  assert.match(entry, /visibleArticleTags/)
  assert.match(view, /v-for="t in visibleArticleTags"/)
})

test('home article cards do not show the following badge', async () => {
  const view = await source('views/HomeFeed.vue')
  const entry = await source('views/HomeFeed.js')

  assert.doesNotMatch(view, /FollowingBadge|你的关注/)
  assert.doesNotMatch(entry, /FollowingBadge/)
})

test('nested replies mention every explicit reply target including the article author', async () => {
  const view = await source('components/article/SubReplyArea.vue')
  const script = await source('scripts/components/article/SubReplyArea.js')
  const detail = await source('views/ArticleDetail.vue')

  assert.doesNotMatch(script, /articleAuthorId/)
  assert.doesNotMatch(script, /rootReplyUserId/)
  assert.doesNotMatch(detail, /:article-author-id="author\?\.id"/)
  assert.doesNotMatch(view, /rootReplyUserId/)
  assert.match(script, /return Boolean\(sub\?\.replyUserNickname\)/)
})

test('profile favorite folders use backend pagination with five items per page', async () => {
  const view = await source('views/Profile.vue')
  const script = await source('scripts/views/Profile.js')
  const api = await source('api/favorite.js')

  assert.match(script, /FAVORITE_FOLDER_PAGE_SIZE\s*=\s*5/)
  assert.match(script, /res\.data\?\.records/)
  assert.match(script, /favoriteFolderTotal/)
  assert.match(view, /favoriteFolderTotalPages/)
  assert.match(api, /getMyFavoriteFolders\(params/)
})

test('favorite article return state restores the outer folder page', async () => {
  const script = await source('scripts/views/Profile.js')

  assert.match(script, /folderPage:\s*favoriteFolderPageNum\.value/)
  assert.match(script, /loadFavoriteFolders\(Number\(state\.folderPage\)\s*\|\|\s*1\)/)
})

test('favorite article rows center stats and reserve the right side for larger author information', async () => {
  const view = await source('views/Profile.vue')
  const script = await source('scripts/views/Profile.js')

  assert.match(view, /profile-fav-item-author/)
  assert.match(view, /<UserAvatarVip[\s\S]*row\.author\?\.vipTier/)
  assert.match(view, /profile-fav-item-stats[\s\S]*profile-fav-item-side/)
  assert.doesNotMatch(view.match(/profile-fav-item-side[\s\S]*?<\/div>\s*<\/button>/)?.[0] || '', /profile-fav-item-stats/)
  assert.match(view, /row\.article\?\.content/)
  assert.doesNotMatch(script, /favoriteSnippet|\.slice\(0,\s*15\)/)
})

test('favorite rename uses a small green check button and liked tab shows total', async () => {
  const view = await source('views/Profile.vue')
  const script = await source('scripts/views/Profile.js')

  assert.match(view, /profile-fav-rename-save/)
  assert.match(view, />\s*✓\s*<\/button>/)
  assert.match(view, /likedTotal/)
  assert.match(script, /favoriteFolderRenameSaving/)
})
