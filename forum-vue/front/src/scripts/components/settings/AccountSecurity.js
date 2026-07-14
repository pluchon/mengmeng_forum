import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLoginLogs } from '@/api/settings'
import { getRecommendationInterests, saveRecommendationInterests } from '@/api/recommendation'
import { useBoardStore } from '@/stores/board'

export function useAccountSecurity() {
  const boardStore = useBoardStore()
  const loginLogVisible = ref(false)
  const loadingLogs = ref(false)
  const loginLogs = ref([])
  const loginLogPage = ref(1)
  const loginLogPageSize = 10
  const loadingPersonalization = ref(true)
  const savingPersonalization = ref(false)
  const personalizedEnabled = ref(true)
  const preferenceBoardIds = ref([])
  const interestDialogVisible = ref(false)
  const interestDraftBoardIds = ref([])
  const interestLoading = ref(false)
  const interestError = ref('')

  const categoriesWithId = computed(() => boardStore.categoryList.filter(item => item.category?.id))

  const pagedLoginLogs = computed(() => {
    const start = (loginLogPage.value - 1) * loginLogPageSize
    return loginLogs.value.slice(start, start + loginLogPageSize)
  })

  function isLocalIp(ip) {
    if (!ip) return false
    const value = String(ip).trim().toLowerCase()
    return value === '127.0.0.1'
      || value === 'localhost'
      || value === '::1'
      || value === '0:0:0:0:0:0:0:1'
      || value.startsWith('192.168.')
      || value.startsWith('10.')
      || /^172\.(1[6-9]|2\d|3[0-1])\./.test(value)
  }

  function formatIpLocation(ip) {
    if (isLocalIp(ip)) return '本地'
    return ip || '未知'
  }

  async function openLoginLogs() {
    loginLogVisible.value = true
    loadingLogs.value = true
    try {
      const res = await getLoginLogs(50)
      if (res.code === 0 && Array.isArray(res.data)) {
        loginLogs.value = res.data.map((row) => ({
          ...row,
          ipLocation: formatIpLocation(row.ipAddress),
        }))
        loginLogPage.value = 1
      } else {
        loginLogs.value = []
      }
    } catch {
      loginLogs.value = []
      ElMessage.error('加载登录日志失败')
    } finally {
      loadingLogs.value = false
    }
  }

  async function loadPersonalization() {
    loadingPersonalization.value = true
    try {
      const res = await getRecommendationInterests()
      if (res.code === 0) {
        personalizedEnabled.value = res.data?.personalizedEnabled !== false
        preferenceBoardIds.value = Array.isArray(res.data?.boardIds) ? res.data.boardIds.map(Number) : []
        return true
      }
    } catch {
      personalizedEnabled.value = true
      preferenceBoardIds.value = []
      ElMessage.error('加载个性化推荐设置失败')
    } finally {
      loadingPersonalization.value = false
    }
    return false
  }

  async function togglePersonalization(enabled) {
    const previous = !enabled
    savingPersonalization.value = true
    try {
      await saveRecommendationInterests({
        personalizedEnabled: enabled,
        boardIds: preferenceBoardIds.value,
      })
      ElMessage.success(enabled ? '已开启个性化推荐' : '已关闭个性化推荐')
    } catch {
      personalizedEnabled.value = previous
    } finally {
      savingPersonalization.value = false
    }
  }

  async function openInterestEditor() {
    interestDialogVisible.value = true
    interestLoading.value = true
    interestError.value = ''
    try {
      if (boardStore.categoryList.length === 0) {
        await boardStore.fetchCategoryList()
      }
      if (categoriesWithId.value.length === 0) {
        interestError.value = '没有获取到可选择的兴趣板块，请稍后重试'
        return
      }
      const preferenceLoaded = await loadPersonalization()
      if (!preferenceLoaded) {
        interestError.value = '没有获取到当前兴趣设置，请稍后重试'
        return
      }
      interestDraftBoardIds.value = [...preferenceBoardIds.value]
    } catch {
      interestError.value = '兴趣板块加载失败，请稍后重试'
    } finally {
      interestLoading.value = false
    }
  }

  async function saveInterestPreferences() {
    if (interestDraftBoardIds.value.length > 8) {
      ElMessage.warning('最多选择 8 个细分板块')
      return
    }
    savingPersonalization.value = true
    try {
      await saveRecommendationInterests({
        personalizedEnabled: true,
        boardIds: interestDraftBoardIds.value,
      })
      preferenceBoardIds.value = [...interestDraftBoardIds.value]
      personalizedEnabled.value = true
      interestDialogVisible.value = false
      ElMessage.success('推荐兴趣已更新')
    } catch {
      // 请求层统一展示错误提示，保留弹窗中的当前选择方便重试。
    } finally {
      savingPersonalization.value = false
    }
  }

  onMounted(loadPersonalization)

  return {
    loginLogPage,
    loginLogPageSize,
    loadingLogs,
    loadingPersonalization,
    categoriesWithId,
    interestDialogVisible,
    interestDraftBoardIds,
    interestError,
    interestLoading,
    loginLogVisible,
    loginLogs,
    openLoginLogs,
    openInterestEditor,
    pagedLoginLogs,
    personalizedEnabled,
    savingPersonalization,
    saveInterestPreferences,
    togglePersonalization,
  }
}
