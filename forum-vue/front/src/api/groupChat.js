import request from './request'

// 我的群聊会话列表
export function getGroupChatSessions(params) {
  return request({ url: '/group-chat/sessions', method: 'get', params })
}

// 创建群聊
export function createGroupChat(data) {
  return request({ url: '/group-chat/create', method: 'post', data })
}

// 修改群聊资料
export function updateGroupChat(groupId, data) {
  return request({ url: `/group-chat/${groupId}`, method: 'put', data })
}

// 查询群聊消息
export function getGroupChatMessages(groupId, params) {
  return request({ url: `/group-chat/${groupId}/messages`, method: 'get', params })
}

// 发送群聊消息
export function sendGroupChatMessage(data) {
  return request({ url: '/group-chat/messages', method: 'post', data })
}

// 标记群聊已读
export function markGroupChatRead(groupId, messageId) {
  return request({
    url: `/group-chat/${groupId}/read`,
    method: 'put',
    params: { messageId },
  })
}

// 查询公开群聊
export function getPublicGroupChats(params) {
  return request({ url: '/group-chat/public', method: 'get', params })
}

// 加入公开群聊
export function joinPublicGroupChat(groupId) {
  return request({ url: `/group-chat/${groupId}/join`, method: 'post' })
}

// 查询群成员
export function getGroupChatMembers(groupId) {
  return request({ url: `/group-chat/${groupId}/members`, method: 'get' })
}

// 邀请群成员
export function inviteGroupChatMember(groupId, inviteeUserId) {
  return request({
    url: `/group-chat/${groupId}/invite`,
    method: 'post',
    data: { inviteeUserId },
  })
}

// 退出群聊
export function leaveGroupChat(groupId) {
  return request({ url: `/group-chat/${groupId}/leave`, method: 'post' })
}

// 移除群成员
export function removeGroupChatMember(groupId, targetUserId) {
  return request({ url: `/group-chat/${groupId}/members/${targetUserId}`, method: 'delete' })
}

// 禁言或解除禁言成员
export function muteGroupChatMember(groupId, targetUserId, minutes) {
  return request({
    url: `/group-chat/${groupId}/members/mute`,
    method: 'put',
    data: { targetUserId, minutes },
  })
}

// 解散群聊
export function dissolveGroupChat(groupId) {
  return request({ url: `/group-chat/${groupId}`, method: 'delete' })
}

// 举报群消息
export function reportGroupChatMessage(groupId, data) {
  return request({ url: `/group-chat/${groupId}/messages/report`, method: 'post', data })
}
