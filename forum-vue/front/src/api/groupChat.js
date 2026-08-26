import request from './request'

// 我的群聊会话列表
export function getGroupChatSessions(params) {
  return request({ url: '/group-chat/sessions', method: 'get', params })
}

// 搜索群聊文本消息所在会话
export function searchGroupChatSessions(params) {
  return request({ url: '/group-chat/searchSessions', method: 'get', params })
}

// 我创建的群聊
export function getOwnedGroupChats(params) {
  return request({ url: '/group-chat/owned', method: 'get', params })
}

// 创建群聊
export function createGroupChat(data) {
  return request({ url: '/group-chat/create', method: 'post', data })
}

// 修改群聊资料
export function updateGroupChat(groupId, data) {
  return request({ url: `/group-chat/${groupId}`, method: 'put', data })
}

// 上传群头像
export function uploadGroupAvatar(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadAvatar',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

// 查询群聊消息
export function getGroupChatMessages(groupId, params) {
  return request({ url: `/group-chat/${groupId}/messages`, method: 'get', params })
}

// 发送群聊消息
export function sendGroupChatMessage(data) {
  return request({ url: '/group-chat/messages', method: 'post', data })
}

// 发送群聊图集
export function sendGroupChatAlbum(data) {
  return request({ url: '/group-chat/messages/album', method: 'post', data })
}

// 撤回群聊消息
export function recallGroupChatMessage(messageId) {
  return request({ url: `/group-chat/messages/${messageId}/recall`, method: 'put' })
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

// 查询某个用户创建的公开群聊
export function getUserPublicGroupChats(ownerUserId, params) {
  return request({ url: `/group-chat/public/users/${ownerUserId}`, method: 'get', params })
}

// 加入公开群聊
export function joinPublicGroupChat(groupId) {
  return request({ url: `/group-chat/${groupId}/join`, method: 'post' })
}

// 查询单条进群请求
export function getGroupJoinRequest(requestId) {
  return request({ url: `/group-chat/requests/${requestId}`, method: 'get' })
}

// 查询我的群收到的进群申请
export function getReceivedGroupJoinRequests(params) {
  return request({ url: '/group-chat/requests/received', method: 'get', params })
}

// 标记我的群收到的进群申请已查看
export function markReceivedGroupJoinRequestsRead() {
  return request({ url: '/group-chat/requests/received/read', method: 'put' })
}

// 查询本人发起的进群申请结果
export function getAppliedGroupJoinRequests(params) {
  return request({ url: '/group-chat/requests/applied', method: 'get', params })
}

// 标记本人发起的进群申请结果已读
export function markAppliedGroupJoinRequestsRead() {
  return request({ url: '/group-chat/requests/applied/read', method: 'put' })
}

// 批准进群申请
export function approveGroupJoinRequest(requestId) {
  return request({ url: `/group-chat/requests/${requestId}/approve`, method: 'put' })
}

// 拒绝进群申请
export function rejectGroupJoinRequest(requestId) {
  return request({ url: `/group-chat/requests/${requestId}/reject`, method: 'put' })
}

// 同意入群邀请
export function acceptGroupInvite(requestId) {
  return request({ url: `/group-chat/requests/${requestId}/accept`, method: 'put' })
}

// 拒绝入群邀请
export function declineGroupInvite(requestId) {
  return request({ url: `/group-chat/requests/${requestId}/decline`, method: 'put' })
}

// 查询群成员
export function getGroupChatMembers(groupId, params) {
  return request({ url: `/group-chat/${groupId}/members`, method: 'get', params })
}

// 修改我的群内备注
export function updateMyGroupRemark(groupId, data) {
  return request({ url: `/group-chat/${groupId}/members/me/remark`, method: 'put', data })
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

// 设置或取消群管理员
export function updateGroupMemberRole(groupId, targetUserId, role) {
  return request({
    url: `/group-chat/${groupId}/members/role`,
    method: 'put',
    data: { targetUserId, role },
  })
}

// 解散群聊
export function dissolveGroupChat(groupId) {
  return request({ url: `/group-chat/${groupId}`, method: 'delete' })
}
