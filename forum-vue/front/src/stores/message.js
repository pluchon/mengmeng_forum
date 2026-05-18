import { defineStore } from 'pinia'
import { ref } from 'vue'
import { showMessageIncomingToast } from '@/utils/messageIncomingToast'

export const useMessageStore = defineStore('message', () => {
  const unreadCount = ref(0)
  const incomingMessage = ref(null)
  /** 可靠触发通知（避免 incomingMessage 引用未变导致 watch 不触发） */
  const incomingSignal = ref(null)
  const readReceiptSignal = ref(null)
  const auditResultSignal = ref(null)
  const systemUnreadCount = ref(0)
  const systemMessageSignal = ref(null)
  const showTip = ref(false)
  const tipText = ref('')
  const incomingPreview = ref(null)

  function setUnreadCount(count, { keepTip = false } = {}) {
    const next = Math.max(0, Number(count) || 0)
    const shouldKeepTip = keepTip || showTip.value || !!incomingPreview.value
    unreadCount.value = next
    if (unreadCount.value === 0 && !shouldKeepTip) {
      showTip.value = false
    }
  }

  function decrementUnread(count = 1) {
    unreadCount.value = Math.max(0, unreadCount.value - count)
    if (unreadCount.value === 0) {
      showTip.value = false
    }
  }

  function onNewMessage(msg) {
    const payload = msg && typeof msg === 'object' ? { ...msg } : {}
    incomingMessage.value = payload

    const sender = (
      payload.fromUser
      || payload.senderNickname
      || payload.nickname
      || '新私信'
    ).toString()
    const body = (payload.summary || payload.content || payload.message || '').toString().trim()

    incomingPreview.value = {
      sender,
      preview: body || '您收到一条新私信',
    }
    const signal = {
      sender,
      preview: incomingPreview.value.preview,
      fromUserId: payload.fromUserId != null ? Number(payload.fromUserId) : null,
      seq: Date.now(),
    }
    incomingSignal.value = signal
    showMessageIncomingToast(signal)

    unreadCount.value = Math.max(0, Number(unreadCount.value) || 0) + 1
    showTip.value = true
    tipText.value = body ? `${sender}：${body}` : '您收到一条新消息'
  }

  function notifyPeerRead(payload) {
    const readerUserId = Number(payload?.readerUserId)
    if (!Number.isFinite(readerUserId)) return
    readReceiptSignal.value = {
      readerUserId,
      messageId: payload.messageId != null ? Number(payload.messageId) : null,
      seq: Date.now(),
    }
  }

  function clearReadReceiptSignal() {
    readReceiptSignal.value = null
  }

  function onAuditResult(payload) {
    auditResultSignal.value = { ...payload, seq: Date.now() }
  }

  function setSystemUnreadCount(count) {
    systemUnreadCount.value = Math.max(0, Number(count) || 0)
  }

  function onSystemMessage(payload) {
    systemMessageSignal.value = { ...payload, seq: Date.now() }
    systemUnreadCount.value += 1
    showTip.value = true
    tipText.value = payload?.title || '您有一条新的系统通知'
  }

  return {
    unreadCount,
    incomingMessage,
    incomingSignal,
    incomingPreview,
    readReceiptSignal,
    auditResultSignal,
    systemUnreadCount,
    systemMessageSignal,
    showTip,
    tipText,
    setUnreadCount,
    onNewMessage,
    decrementUnread,
    notifyPeerRead,
    clearReadReceiptSignal,
    onAuditResult,
    setSystemUnreadCount,
    onSystemMessage,
  }
}, {
  persist: {
    omit: ['readReceiptSignal', 'auditResultSignal', 'systemMessageSignal', 'incomingPreview', 'incomingSignal'],
  },
})
