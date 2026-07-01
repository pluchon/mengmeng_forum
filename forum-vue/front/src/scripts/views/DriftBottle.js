import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Compass,
  Delete,
  Refresh,
  Warning,
  Sunny,
  Umbrella,
  EditPen,
  Document,
  Message,
  Promotion,
  ArrowDown,
  Check,
  Box,
  ArrowRight,
  IceDrink
} from '@element-plus/icons-vue'
import {
  commentDriftBottle,
  createDriftBottle,
  deleteDriftBottle,
  getDriftBottleDetail,
  getDriftBottleQuota,
  getMyDriftBottles,
  pickDriftBottle,
  reportDriftBottle,
  reportDriftBottleComment,
} from '@/api/driftBottle'
import { unwrapPageRecords } from '@/utils/apiData'
import { formatForumDateTimeShanghai } from '@/utils/datetime'

export const DRIFT_MOODS = ['开心', '难过', '迷茫', '压力', '秘密', '求安慰', '随便说说']

const REPORT_REASON = '用户举报'

export function useDriftBottle() {
  const loading = ref(false)
  const pageError = ref('')
  const activeBottle = ref(null)
  const createSubmitting = ref(false)
  const pickLoading = ref(false)
  const commentSubmitting = ref(false)
  const mineLoading = ref(false)
  const myBottles = ref([])
  const expandedBottles = ref({})
  const commentPages = ref({})
  const commentPageSize = 5
  const myTotal = ref(0)
  const myPage = ref(1)
  const myPageSize = 5
  const quota = ref(null)
  const moodDialogVisible = ref(false)

  const createForm = reactive({
    content: '',
    moodType: '随便说说',
  })
  const commentContent = ref('')

  const contentCount = computed(() => createForm.content.trim().length)
  const commentCount = computed(() => commentContent.value.trim().length)
  const hasActiveBottle = computed(() => !!activeBottle.value?.id)
  const comments = computed(() => activeBottle.value?.comments || [])
  const currentBottleEmptyText = computed(() => (pageError.value ? pageError.value : '还没有捞到瓶子'))

  async function loadEntry() {
    loading.value = true
    pageError.value = ''
    try {
      await Promise.all([loadQuota(), loadMine(1)])
    } catch {
      pageError.value = '漂流瓶暂时无法打开'
    } finally {
      loading.value = false
    }
  }

  async function loadQuota() {
    const res = await getDriftBottleQuota()
    if (res.code === 0) {
      quota.value = res.data
    }
  }

  async function loadMine(page = myPage.value) {
    mineLoading.value = true
    try {
      const res = await getMyDriftBottles({ pageNum: page, pageSize: myPageSize })
      if (res.code === 0) {
        myBottles.value = unwrapPageRecords(res.data)
        myTotal.value = Number(res.data?.total) || myBottles.value.length
        myPage.value = page
      }
    } finally {
      mineLoading.value = false
    }
  }

  async function submitBottle(e) {
    const content = createForm.content.trim()
    if (content.length < 20 || content.length > 500) {
      ElMessage.warning('瓶子内容需要 20 到 500 字')
      return
    }
    const btnEl = e?.currentTarget || e?.target
    createSubmitting.value = true
    try {
      const res = await createDriftBottle({
        content,
        moodType: createForm.moodType,
      })
      if (res.code === 0) {
        if (btnEl) {
          playThrowAnimation(btnEl)
        }
        createForm.content = ''
        ElMessage.success('瓶子已经漂向海里')
        await Promise.all([loadQuota(), loadMine(1)])
      }
    } finally {
      createSubmitting.value = false
    }
  }

  function playThrowAnimation(btn) {
    const rect = btn.getBoundingClientRect()
    const startX = rect.left + rect.width / 2 - 16
    const startY = rect.top - 16
    const endX = window.innerWidth / 2 + (Math.random() * 200 - 100)
    const endY = window.innerHeight - 60

    const bottle = document.createElement('div')
    bottle.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:100%;height:100%;color:#fff;filter:drop-shadow(0 2px 4px rgba(0,0,0,0.2))"><path d="M10 2v3a6 6 0 0 0-3 5v7a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-7a6 6 0 0 0-3-5V2"></path><path d="M8.5 2h7"></path></svg>`
    bottle.style.position = 'fixed'
    bottle.style.left = '0px'
    bottle.style.top = '0px'
    bottle.style.width = '32px'
    bottle.style.height = '32px'
    bottle.style.zIndex = '9999'
    bottle.style.pointerEvents = 'none'
    document.body.appendChild(bottle)

    const animX = bottle.animate([
      { transform: `translateX(${startX}px)` },
      { transform: `translateX(${endX}px)` }
    ], { duration: 1000, easing: 'linear', fill: 'forwards' })

    const animY = bottle.children[0].animate([
      { transform: `translateY(${startY}px) rotate(0deg)`, easing: 'ease-out' },
      { transform: `translateY(${startY - 150}px) rotate(180deg)`, offset: 0.4, easing: 'ease-in' },
      { transform: `translateY(${endY}px) rotate(360deg)` }
    ], { duration: 1000, fill: 'forwards' })

    animX.onfinish = () => {
      bottle.remove()
      const splash = document.createElement('div')
      splash.style.position = 'fixed'
      splash.style.left = `${endX - 14}px`
      splash.style.top = `${endY + 16}px`
      splash.style.width = '60px'
      splash.style.height = '20px'
      splash.style.borderRadius = '50%'
      splash.style.border = '3px solid rgba(255,255,255,0.8)'
      splash.style.zIndex = '9998'
      splash.style.pointerEvents = 'none'
      splash.style.opacity = '1'
      document.body.appendChild(splash)

      splash.animate([
        { transform: 'scale(0.5)', opacity: 0.8, borderWidth: '3px' },
        { transform: 'scale(2)', opacity: 0, borderWidth: '0px' }
      ], { duration: 500, easing: 'ease-out', fill: 'forwards' }).onfinish = () => splash.remove()
    }
  }

  async function pickOne() {
    pickLoading.value = true
    pageError.value = ''
    try {
      const res = await pickDriftBottle()
      if (res.code === 0) {
        if (res.data == null) {
          activeBottle.value = null
          pageError.value = '海里暂时没有可打捞的瓶子了~'
        } else {
          activeBottle.value = res.data
          commentContent.value = ''
        }
        await loadQuota()
      }
    } catch (e) {
      pageError.value = e?.message || '暂时没有可打捞的瓶子'
    } finally {
      pickLoading.value = false
    }
  }

  async function submitComment() {
    if (!activeBottle.value?.id) return
    const content = commentContent.value.trim()
    if (!content || content.length > 200) {
      ElMessage.warning('评论需要 1 到 200 字')
      return
    }
    commentSubmitting.value = true
    try {
      const res = await commentDriftBottle(activeBottle.value.id, { content })
      if (res.code === 0) {
        activeBottle.value = res.data
        commentContent.value = ''
        ElMessage.success('回应已送达')
        await Promise.all([loadQuota(), loadMine(myPage.value)])
      }
    } finally {
      commentSubmitting.value = false
    }
  }

  async function openMyBottle(row) {
    if (!row?.id) return
    if (expandedBottles.value[row.id]) {
      expandedBottles.value[row.id] = false
      return
    }
    if (!row.commentsFetched) {
      const res = await getDriftBottleDetail(row.id)
      if (res.code === 0) {
        row.fullContent = res.data.content
        row.comments = res.data.comments || []
        row.commentsFetched = true
      }
    }
    expandedBottles.value[row.id] = true
  }

  async function deleteMine(row) {
    if (!row?.id) return
    await ElMessageBox.confirm('确认删除这个漂流瓶吗？删除后其他人不会再捞到它。', '删除漂流瓶', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteDriftBottle(row.id)
    ElMessage.success('漂流瓶已删除')
    if (activeBottle.value?.id === row.id) {
      activeBottle.value = null
    }
    await Promise.all([loadQuota(), loadMine(myPage.value)])
  }

  async function reportCurrentBottle() {
    if (!activeBottle.value?.id) return
    const detail = await promptReport('举报漂流瓶')
    if (detail == null) return
    await reportDriftBottle(activeBottle.value.id, {
      reasonType: REPORT_REASON,
      reasonDetail: detail,
    })
    ElMessage.success('举报已提交')
  }

  async function reportComment(row) {
    if (!row?.id) return
    const detail = await promptReport('举报评论')
    if (detail == null) return
    await reportDriftBottleComment(row.id, {
      reasonType: REPORT_REASON,
      reasonDetail: detail,
    })
    ElMessage.success('举报已提交')
  }

  async function promptReport(title) {
    try {
      const { value } = await ElMessageBox.prompt('', title, {
        confirmButtonText: '提交',
        cancelButtonText: '取消',
        inputType: 'text',
        inputPlaceholder: '例如：广告、辱骂、暴露隐私等',
        inputValidator(value) {
          const text = String(value || '').trim()
          if (!text) return '请填写举报原因'
          if (text.length > 200) return '举报原因不能超过 200 字'
          return true
        },
      })
      return String(value || '').trim()
    } catch {
      return null
    }
  }

  function onMinePageChange(page) {
    loadMine(page)
  }

  function pagedComments(row) {
    if (!row.comments || !row.comments.length) return []
    const page = commentPages.value[row.id] || 1
    const start = (page - 1) * commentPageSize
    return row.comments.slice(start, start + commentPageSize)
  }

  function onCommentPageChange(bottleId, page) {
    commentPages.value[bottleId] = page
  }

  function formatTime(value) {
    return formatForumDateTimeShanghai(value)
  }

  onMounted(() => {
    loadEntry()
  })

  return {
    ChatDotRound,
    Compass,
    Delete,
    DRIFT_MOODS,
    Refresh,
    Warning,
    Sunny,
    Umbrella,
    EditPen,
    Document,
    Message,
    Promotion,
    ArrowDown,
    Check,
    Box,
    ArrowRight,
    IceDrink,
    activeBottle,
    commentContent,
    commentCount,
    commentPages,
    commentPageSize,
    commentSubmitting,
    comments,
    contentCount,
    createForm,
    createSubmitting,
    currentBottleEmptyText,
    deleteMine,
    expandedBottles,
    formatTime,
    hasActiveBottle,
    loadEntry,
    loading,
    mineLoading,
    myBottles,
    myPage,
    myPageSize,
    myTotal,
    moodDialogVisible,
    onCommentPageChange,
    onMinePageChange,
    openMyBottle,
    pagedComments,
    pageError,
    pickLoading,
    pickOne,
    quota,
    reportComment,
    reportCurrentBottle,
    submitBottle,
    submitComment,
  }
}
