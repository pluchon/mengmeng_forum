import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Compass,
  Delete,
  Refresh,
  Warning,
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
  const myTotal = ref(0)
  const myPage = ref(1)
  const myPageSize = 8
  const quota = ref(null)

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

  async function submitBottle() {
    const content = createForm.content.trim()
    if (content.length < 20 || content.length > 500) {
      ElMessage.warning('瓶子内容需要 20 到 500 字')
      return
    }
    createSubmitting.value = true
    try {
      const res = await createDriftBottle({
        content,
        moodType: createForm.moodType,
      })
      if (res.code === 0) {
        activeBottle.value = res.data
        createForm.content = ''
        ElMessage.success('瓶子已经漂向海里')
        await Promise.all([loadQuota(), loadMine(1)])
      }
    } finally {
      createSubmitting.value = false
    }
  }

  async function pickOne() {
    pickLoading.value = true
    pageError.value = ''
    try {
      const res = await pickDriftBottle()
      if (res.code === 0) {
        activeBottle.value = res.data
        commentContent.value = ''
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
    const res = await getDriftBottleDetail(row.id)
    if (res.code === 0) {
      activeBottle.value = res.data
    }
    pageError.value = ''
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
      const { value } = await ElMessageBox.prompt('请填写举报原因', title, {
        confirmButtonText: '提交',
        cancelButtonText: '取消',
        inputType: 'textarea',
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
    activeBottle,
    commentContent,
    commentCount,
    commentSubmitting,
    comments,
    contentCount,
    createForm,
    createSubmitting,
    currentBottleEmptyText,
    deleteMine,
    formatTime,
    hasActiveBottle,
    loadEntry,
    loading,
    mineLoading,
    myBottles,
    myPage,
    myPageSize,
    myTotal,
    onMinePageChange,
    openMyBottle,
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
