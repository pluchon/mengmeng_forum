// 聊天发送偏好（本地）：回车发送
const STORAGE_KEY = 'forum.chat.enterToSend'

export function getEnterToSendEnabled() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw == null) return true
    return raw === '1' || raw === 'true'
  } catch {
    return true
  }
}

export function setEnterToSendEnabled(enabled) {
  try {
    localStorage.setItem(STORAGE_KEY, enabled ? '1' : '0')
  } catch {
    // ignore
  }
  window.dispatchEvent(new CustomEvent('forum-chat-enter-to-send-changed', {
    detail: { enabled: Boolean(enabled) },
  }))
}

export function onEnterToSendChanged(handler) {
  const listener = (event) => handler(Boolean(event?.detail?.enabled))
  window.addEventListener('forum-chat-enter-to-send-changed', listener)
  return () => window.removeEventListener('forum-chat-enter-to-send-changed', listener)
}
