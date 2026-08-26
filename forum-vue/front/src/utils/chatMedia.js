// 与后端 Constant.OSS_PATH_* 保持一致 外链可能带或不带 forum_db_item/ 根前缀
export const OSS_PATH_CHAT_MESSAGE = 'forum_db_item/forum_chat_picture/message/'
export const OSS_PATH_CHAT_EMOJI = 'forum_db_item/forum_chat_picture/emoji/'
export const OSS_PATH_EMOJI_SHOP = 'forum_db_item/forum_emoji_shop/'

const CHAT_EMOJI_URL_MARKER = 'forum_chat_picture/emoji/'

// 与 docs/chat media api.md §2.1 白名单一致
const CHAT_IMAGE_MIMES = new Set(['image/jpeg', 'image/jpg', 'image/png', 'image/gif'])

export function isChatAllowedImageMime(file) {
  const t = (file?.type || '').toLowerCase().trim()
  return CHAT_IMAGE_MIMES.has(t)
}

export function validateChatImageMime(file) {
  if (!file) return { ok: false, message: '请选择文件' }
  if (!isChatAllowedImageMime(file)) {
    return { ok: false, message: '聊天图片仅支持 JPG / PNG / GIF' }
  }
  return { ok: true }
}

// 对方从表情商城发送的图 不可「添加到表情」收藏
export function isEmojiShopMediaUrl(url) {
  const s = typeof url === 'string' ? url.trim() : ''
  return s.length > 0 && (s.includes(OSS_PATH_EMOJI_SHOP) || s.includes('forum_emoji_shop/'))
}

// 用户主动上传的聊天表情 OSS …/emoji/，无来源消息
export function isUserUploadedChatEmoji(item) {
  if (!item?.mediaUrl) return false
  if (item.originMessageId != null && Number(item.originMessageId) > 0) return false
  const url = String(item.mediaUrl).trim()
  return url.includes(OSS_PATH_CHAT_EMOJI) || url.includes(CHAT_EMOJI_URL_MARKER)
}

// 从他人聊天/内容收藏的表情
export function isFavoriteFromChatEmoji(item) {
  if (!item?.mediaUrl) return false
  if (item.originMessageId != null && Number(item.originMessageId) > 0) return true
  const url = String(item.mediaUrl).trim()
  return !url.includes(OSS_PATH_CHAT_EMOJI)
    && !url.includes(CHAT_EMOJI_URL_MARKER)
    && !isEmojiShopMediaUrl(url)
}

// 是否允许将对方聊天中的图片/GIF 加入自己的表情收藏
export function canFavoriteChatMediaMessage(message) {
  if (!message?.mediaUrl) return false
  const t = Number(message.messageType)
  if (t !== 1 && t !== 2) return false
  return !isEmojiShopMediaUrl(message.mediaUrl)
}

export function readImageNaturalSize(url) {
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () =>
      resolve({
        width: img.naturalWidth || 0,
        height: img.naturalHeight || 0,
      })
    img.onerror = () => resolve({ width: 0, height: 0 })
    img.src = url
  })
}
