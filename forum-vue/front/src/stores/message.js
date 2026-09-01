import { defineStore } from 'pinia'
import { ref } from 'vue'
import { showMessageIncomingToast } from '@/utils/messageIncomingToast'

const RECENT_MESSAGE_LIMIT = 120
const recentIncomingMessageIds = new Set()

export const useMessageStore = defineStore('message', () => {
  const unreadCount = ref(0)
  const incomingMessage = ref(null)
  // 可靠触发通知 避免 incomingMessage 引用未变导致 watch 不触发
  const incomingSignal = ref(null)
  const readReceiptSignal = ref(null)
  const auditResultSignal = ref(null)
  const systemUnreadCount = ref(0)
  const systemMessageSignal = ref(null)
  const groupMessageSignal = ref(null)
  const groupAuditSignal = ref(null)
  const privateMessageMutationSignal = ref(null)
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

  function showIncomingTip() {
    showTip.value = true
  }

  function hideTip() {
    showTip.value = false
  }

  function decrementUnread(count = 1) {
    unreadCount.value = Math.max(0, unreadCount.value - count)
    if (unreadCount.value === 0) {
      showTip.value = false
    }
  }

  function onNewMessage(msg) {
    const payload = msg && typeof msg === 'object' ? { ...msg } : {}
    const dbMessageId = payload.dbMessageId != null ? String(payload.dbMessageId) : ''
    if (dbMessageId && recentIncomingMessageIds.has(dbMessageId)) {
      return
    }
    if (dbMessageId) {
      recentIncomingMessageIds.add(dbMessageId)
      if (recentIncomingMessageIds.size > RECENT_MESSAGE_LIMIT) {
        const first = recentIncomingMessageIds.values().next().value
        recentIncomingMessageIds.delete(first)
      }
    }
    incomingMessage.value = payload

    if (payload.selfEcho === true) {
      return
    }

    // 免打扰：消息该进列表还是进列表，只是不弹提示。与群聊同一套约定
    if (payload?.notify === false) {
      return
    }

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

  // 建群审核结果：只是一个信号，页面自己去刷新
  function onGroupCreateAudit(payload) {
    groupAuditSignal.value = { ...payload, seq: Date.now() }
  }

  function onGroupMessage(payload) {
    groupMessageSignal.value = { ...payload, seq: Date.now() }
    if (['group_message_recalled', 'group_message_deleted', 'group_message_audit_failed', 'private_message_deleted'].includes(payload?.type)) {
      return
    }
    if (payload?.notify === false) {
      return
    }
    showTip.value = true
    const sender = String(payload?.senderNickname || '群成员').trim() || '群成员'
    const preview = String(payload?.summary || payload?.content || '你收到一条新的群聊消息').trim()
      || '你收到一条新的群聊消息'
    // 群聊的卡片要说清楚是哪个群：第一行群名，第二行「谁说了什么」
    const groupName = String(payload?.groupName || '').trim()
    const groupAvatarUrl = String(payload?.groupAvatarUrl || '').trim()
    const mentioned = payload?.mentioned === true
    incomingPreview.value = { sender, preview, groupName, groupAvatarUrl, mentioned, kind: 'group' }
    const signal = {
      sender,
      preview,
      groupName,
      groupAvatarUrl,
      kind: 'group',
      mentioned,
      fromUserId: payload?.fromUserId != null ? Number(payload.fromUserId) : null,
      seq: Date.now(),
    }
    incomingSignal.value = signal
    showMessageIncomingToast(signal)
    tipText.value = `${sender}：${preview}`
  }

  function onPrivateMessageMutation(payload) {
    privateMessageMutationSignal.value = { ...payload, seq: Date.now() }
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
    groupMessageSignal,
    privateMessageMutationSignal,
    showTip,
    tipText,
    setUnreadCount,
    showIncomingTip,
    hideTip,
    onNewMessage,
    decrementUnread,
    notifyPeerRead,
    clearReadReceiptSignal,
    onAuditResult,
    setSystemUnreadCount,
    onSystemMessage,
    groupAuditSignal,
    onGroupCreateAudit,
    onGroupMessage,
    onPrivateMessageMutation,
  }
}, {
  persist: {
    omit: ['readReceiptSignal', 'auditResultSignal', 'systemMessageSignal', 'groupMessageSignal', 'privateMessageMutationSignal', 'incomingPreview', 'incomingSignal'],
  },
})
