import request from './request'

export function getSystemMessageList(params) {
  return request({ url: '/system-message/list', method: 'get', params })
}

export function getSystemMessageUnreadCount() {
  return request({ url: '/system-message/unreadCount', method: 'get' })
}

export function markSystemMessageRead(messageId) {
  return request({ url: '/system-message/markOneRead', method: 'put', params: { messageId } })
}

export function markAllSystemMessagesRead() {
  return request({ url: '/system-message/markAllRead', method: 'put' })
}

export function deleteSystemMessage(messageId) {
  return request({ url: '/system-message/delete', method: 'delete', params: { messageId } })
}
