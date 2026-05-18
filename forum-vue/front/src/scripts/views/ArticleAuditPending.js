import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getAuditStatus } from '@/api/article'
import { useWebSocket } from '@/composables/useWebSocket'
import { useMessageStore } from '@/stores/message'
import { useUserStore } from '@/stores/user'
import { ARTICLE_STATUS } from '@/utils/articleStatus'

const POLL_MS = 3000
const POLL_START_DELAY_MS = 0

/** 仅终态可跳转；APPROVED(2) 为过渡态，继续轮询直至 PUBLISHED(5) */
function isTerminalStatus(status) {
  const s = Number(status)
  return (
    s === ARTICLE_STATUS.PUBLISHED
    || s === ARTICLE_STATUS.REJECTED
    || s === ARTICLE_STATUS.AUDIT_ERROR
  )
}

export function useArticleAuditPending() {
  const route = useRoute()
  const router = useRouter()
  const messageStore = useMessageStore()
  const userStore = useUserStore()
  const { initWebSocket } = useWebSocket()

  const LoadingIcon = Loading
  const articleId = ref(Number(route.params.id))
  const taskIdHint = ref((route.query.taskId || '').toString())
  const statusText = ref('审核中')
  const lastPayload = ref(null)

  let pollTimer = null
  let startPollTimer = null

  function clearTimers() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    if (startPollTimer) {
      clearTimeout(startPollTimer)
      startPollTimer = null
    }
  }

  async function pollOnce() {
    const id = articleId.value
    if (!id) return
    try {
      const res = await getAuditStatus(id)
      if (res.code !== 0) return
      const d = res.data
      lastPayload.value = d
      if (d?.statusText) statusText.value = d.statusText

      if (
        d?.retryLimitReached
        && Number(d?.status) !== ARTICLE_STATUS.PENDING_AUDIT
      ) {
        clearTimers()
        ElMessage.error(d?.resultMessage || '审核次数已达上限，请联系管理员')
        router.replace('/creative')
        return
      }

      if (Number(d?.status) === ARTICLE_STATUS.APPROVED) {
        statusText.value = '审核通过，正在发布…'
        return
      }

      if (isTerminalStatus(d?.status)) {
        clearTimers()
        const s = Number(d.status)
        if (s === ARTICLE_STATUS.PUBLISHED) {
          ElMessage.success(d?.resultMessage || '审核通过，已发布')
          router.replace(`/article/${id}`)
          return
        }
        if (s === ARTICLE_STATUS.REJECTED) {
          ElMessage.warning(d?.resultMessage || '审核未通过，请修改后重新提交')
          router.replace(`/article/edit/${id}`)
          return
        }
        ElMessage.error(d?.resultMessage || '审核异常，请稍后重试')
        router.replace(`/article/edit/${id}`)
      }
    } catch {
      /* ignore */
    }
  }

  function applyAuditWs(payload) {
    const aid = Number(payload?.articleId)
    if (!Number.isFinite(aid) || aid !== articleId.value) return
    const fs = (payload?.finalStatus || '').toString().toUpperCase()
    const st = Number(payload?.status)
    if (!fs && !Number.isFinite(st)) {
      pollOnce()
      return
    }
    clearTimers()
    if (fs === 'APPROVED' && Number(payload?.status) !== ARTICLE_STATUS.PUBLISHED) {
      statusText.value = '审核通过，正在发布…'
      pollOnce()
      return
    }
    if (fs === 'APPROVED' || Number(payload?.status) === ARTICLE_STATUS.PUBLISHED) {
      ElMessage.success('审核通过，已发布')
      router.replace(`/article/${aid}`)
      return
    }
    if (fs === 'REJECTED' || Number(payload?.status) === ARTICLE_STATUS.REJECTED) {
      ElMessage.warning(payload?.message || payload?.resultMessage || '审核未通过')
      router.replace(`/article/edit/${aid}`)
      return
    }
    ElMessage.error(payload?.message || payload?.resultMessage || '审核异常')
    router.replace(`/article/edit/${aid}`)
  }

  let stopWsWatch = null
  onMounted(() => {
    if (userStore.isLoggedIn) {
      userStore.fetchUserInfo?.()
      initWebSocket()
    }
    pollOnce()
    startPollTimer = setTimeout(() => {
      pollTimer = setInterval(pollOnce, POLL_MS)
    }, POLL_START_DELAY_MS)

    stopWsWatch = watch(
      () => messageStore.auditResultSignal,
      (sig) => {
        if (!sig?.articleId) return
        if (Number(sig.articleId) !== articleId.value) return
        applyAuditWs(sig)
      },
    )

    watch(
      () => messageStore.systemMessageSignal,
      (sig) => {
        if (!sig?.articleId) return
        if (Number(sig.articleId) !== articleId.value) return
        ElNotification({
          title: sig.title || '审核通知',
          message: sig.content || '请前往消息中心查看',
          type: 'success',
          duration: 6000,
        })
      },
    )
  })

  onUnmounted(() => {
    clearTimers()
    stopWsWatch?.()
  })

  return {
    LoadingIcon,
    articleId,
    lastPayload,
    statusText,
    taskIdHint,
  }
}
