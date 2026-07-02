import request from './request'

// 获取站内信会话列表（按联系人聚合）
export function getSessionList(params) {
  return request({ url: '/message/queryMessageSessionWithPage', method: 'get', params })
}

// 获取与某用户的聊天记录详情
export function getMessageList(params) {
  // 传 receiveId, pageNum, pageSize
  return request({ url: '/message/queryMessageDetailWithPage', method: 'get', params })
}

/** WebSocket 收到 dbMessageId 后拉取完整气泡（MessageDetailResponse） */
export function getMessageDetailById(messageId) {
  return request({
    url: '/message/queryMessageDetailById',
    method: 'get',
    params: { messageId },
    silentBizCodes: [1001, 1002, 1005],
  })
}

/** 单条状态更新（如需精确单条已读；常态用 markRead 批量即可） */
export function updateMessageStatusByMessageId(messageId, status = 1) {
  return request({
    url: '/message/updateMessageStatusByMessageId',
    method: 'put',
    params: { messageId, status },
  })
}

// 发送私信
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

/** 上传聊天图片（OSS …/message/），成功后需再调 sendImageMessage */
export function uploadChatImage(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadChatImage',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

/** 上传自定义表情（OSS …/emoji/），成功后需再调 favoriteEmoji */
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

/** 发送图片 / GIF 私信（不含正文） */
export function sendImageMessage(data) {
  return request({ url: '/message/sendImage', method: 'post', data })
}

/** 收藏表情（自上传 url 或聊天消息引用） */
export function favoriteEmoji(data) {
  return request({ url: '/message/emoji/favorite', method: 'post', data })
}

export function deleteFavoriteEmoji(emojiId) {
  return request({ url: `/message/emoji/${emojiId}`, method: 'delete' })
}

export function getEmojiList() {
  return request({ url: '/message/emoji/list', method: 'get' })
}

// 查询私聊语音状态
export function getPrivateVoiceSession(peerUserId) {
  return request({ url: `/message/private-voice/${peerUserId}`, method: 'get' })
}

// 发起私聊语音
export function startPrivateVoiceSession(peerUserId) {
  return request({ url: `/message/private-voice/${peerUserId}/start`, method: 'post' })
}

// 接听私聊语音
export function acceptPrivateVoiceSession(peerUserId) {
  return request({ url: `/message/private-voice/${peerUserId}/accept`, method: 'post' })
}

// 拒绝私聊语音
export function declinePrivateVoiceSession(peerUserId) {
  return request({ url: `/message/private-voice/${peerUserId}/decline`, method: 'post' })
}

// 离开私聊语音
export function leavePrivateVoiceSession(peerUserId) {
  return request({ url: `/message/private-voice/${peerUserId}/leave`, method: 'post' })
}
