import request from './request'

// 获取站内信会话列表 按联系人聚合
export function getSessionList(params) {
  return request({ url: '/message/queryMessageSessionWithPage', method: 'get', params })
}

// 获取与某用户的聊天记录详情
export function getMessageList(params) {
  // 传 receiveId, pageNum, pageSize
  return request({ url: '/message/queryMessageDetailWithPage', method: 'get', params })
}

// WebSocket 收到 dbMessageId 后拉取完整气泡 MessageDetailResponse
export function getMessageDetailById(messageId) {
  return request({
    url: '/message/queryMessageDetailById',
    method: 'get',
    params: { messageId },
    silentBizCodes: [1001, 1002, 1005],
  })
}

// 单条状态更新 如需精确单条已读；常态用 markRead 批量即可
export function updateMessageStatusByMessageId(messageId, status = 1) {
  return request({
    url: '/message/updateMessageStatusByMessageId',
    method: 'put',
    params: { messageId, status },
  })
}

// 发送私信
// 开关私信会话免打扰；只影响实时提醒，消息与未读数照常
export function muteMessageSession(data) {
  return request({ url: '/message/session/mute', method: 'post', data })
}

// 置顶 / 取消置顶私信会话，最多十个
export function pinMessageSession(data) {
  return request({ url: '/message/session/pin', method: 'post', data })
}

export function sendMessage(data) {
  return request({ url: '/message/sendMessage', method: 'post', data })
}

// 获取未读消息数
export function getUnReadCount() {
  return request({ url: '/message/getUnReadMessage', method: 'get' })
}

// 标记某发信人的所有消息为已读
export function markRead(senderId) {
  return request({ url: '/message/markAllMessageReadBySender', method: 'put', params: { senderId } })
}

// 撤回私信
export function recallMessage(messageId) {
  return request({ url: '/message/recallMessage', method: 'put', params: { messageId } })
}

// 上传聊天图片 OSS …/message/ ，成功后需再调 sendImageMessage
export function uploadChatImage(file, { onUploadProgress, silentBizCodes } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadChatImage',
    method: 'post',
    data: formData,
    timeout: 300000,
    onUploadProgress,
    silentBizCodes,
  })
}

// 批量上传聊天图片（一次最多 9 张，支持部分成功）
export function uploadChatImages(files, { onUploadProgress, silentBizCodes, silentHttpError } = {}) {
  const formData = new FormData()
  const list = Array.isArray(files) ? files : [files]
  list.forEach((file) => {
    if (file) formData.append('files', file)
  })
  return request({
    url: '/file/uploadChatImages',
    method: 'post',
    data: formData,
    timeout: 300000,
    onUploadProgress,
    silentBizCodes,
    silentHttpError: !!silentHttpError,
  })
}

// 上传自定义表情 OSS …/emoji/ ，成功后需再调 favoriteEmoji
export function uploadChatEmoji(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadChatEmoji',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

// 批量上传自定义表情（一次最多 9 张，支持部分成功；成功后再逐张 favorite）
export function uploadChatEmojis(files, { onUploadProgress, silentHttpError } = {}) {
  const formData = new FormData()
  const list = Array.isArray(files) ? files : [files]
  list.forEach((file) => {
    if (file) formData.append('files', file)
  })
  return request({
    url: '/file/uploadChatEmojis',
    method: 'post',
    data: formData,
    timeout: 300000,
    onUploadProgress,
    silentHttpError: !!silentHttpError,
  })
}

// 发送图片 / GIF 私信 不含正文
export function sendImageMessage(data) {
  return request({ url: '/message/sendImage', method: 'post', data })
}

// 发送一至十张图片组成的私信图集，可附带文字
export function sendAlbumMessage(data) {
  return request({ url: '/message/sendAlbum', method: 'post', data })
}

// 收藏表情 自上传 url 或聊天消息引用
export function favoriteEmoji(data) {
  return request({ url: '/message/emoji/favorite', method: 'post', data })
}

export function deleteFavoriteEmoji(emojiId) {
  return request({ url: `/message/emoji/${emojiId}`, method: 'delete' })
}

export function getEmojiList(params) {
  return request({ url: '/message/emoji/list', method: 'get', params })
}

export function searchMessageSessions(params) {
  return request({ url: '/message/searchSessions', method: 'get', params })
}

export function hideMessageSession(peerUserId) {
  return request({ url: '/message/session/hide', method: 'post', data: { peerUserId } })
}

export function restoreMessageSession(peerUserId) {
  return request({ url: '/message/session/restore', method: 'post', data: { peerUserId } })
}

export function getHiddenMessageSessions(params) {
  return request({ url: '/message/session/hidden', method: 'get', params })
}

export function reportChatMessage(data) {
  return request({ url: '/message/report', method: 'post', data })
}
