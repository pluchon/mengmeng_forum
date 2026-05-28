import { ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CaretBottom, CaretTop } from '@element-plus/icons-vue'
import { getSubReplyList, submitSubReply } from '@/api/reply'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { unwrapPageRecords } from '@/utils/apiData'
import '@/assets/styles/article.css'

export function useSubReplyArea(props) {
  const router = useRouter()
  const userStore = useUserStore()
  const defaultAvatar = DEFAULT_AVATAR

  function goProfile(userId) {
    if (userId == null || userId === '') return
    router.push(`/profile/${userId}`)
  }
  const expanded = ref(false)
  const subList = ref([])
  const page = ref(1)
  const pageSize = ref(5)
  const total = ref(0)
  const inputContent = ref('')
  const replyTarget = ref(null)
  const submitting = ref(false)
  const subInputRef = ref(null)

  watch(
    () => [props.replyId, props.articleId],
    async ([replyId, articleId]) => {
      if (replyId == null || replyId === '' || articleId == null || articleId === '') return
      expanded.value = false
      inputContent.value = ''
      replyTarget.value = null
      page.value = 1
      subList.value = []
      total.value = 0
      await loadSubs(1)
    },
    { immediate: true },
  )

  function toggle() {
    expanded.value = !expanded.value
  }

  async function loadSubs(p = 1) {
    page.value = p
    const res = await getSubReplyList({ replyId: props.replyId, pageNum: p, pageSize: pageSize.value })
    if (res.code === 0) {
      const raw = res.data
      subList.value = unwrapPageRecords(raw)
      total.value = raw?.total != null ? Number(raw.total) : subList.value.length
    }
  }

  function setReplyTarget(sub) {
    replyTarget.value = sub
  }

  async function openReplyTo(floorUser) {
    if (total.value > 0) expanded.value = true
    replyTarget.value = floorUser ? { postUser: floorUser } : null
    await nextTick()
    subInputRef.value?.focus?.()
  }

  async function submitSub() {
    if (!userStore.isLoggedIn) {
      const { ensureLoggedIn } = await import('@/utils/loginPrompt')
      if (!(await ensureLoggedIn('回复需要登录'))) return
    }
    if (blockIfMuted(userStore)) return
    const content = inputContent.value.trim()
    if (!content) {
      ElMessage.warning('请输入回复内容')
      return
    }
    submitting.value = true
    try {
      const res = await submitSubReply({
        articleId: props.articleId,
        replyId: props.replyId,
        replyUserId: replyTarget.value?.postUser?.id || null,
        content,
      })
      if (res.code === 0) {
        inputContent.value = ''
        replyTarget.value = null
        await loadSubs(1)
        if (total.value > 0) expanded.value = true
      } else {
        ElMessage.error(res.message || '楼中楼发送失败')
      }
    } catch (err) {
      if (err?.code === 1104) return
      ElMessage.error(err?.message || '楼中楼发送失败')
    } finally {
      submitting.value = false
    }
  }

  return {
    CaretBottom,
    CaretTop,
    defaultAvatar,
    expanded,
    inputContent,
    loadSubs,
    openReplyTo,
    page,
    pageSize,
    replyTarget,
    setReplyTarget,
    subInputRef,
    subList,
    submitSub,
    submitting,
    toggle,
    total,
    goProfile,
  }
}
