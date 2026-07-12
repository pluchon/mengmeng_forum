import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLoginLogs } from '@/api/settings'
import { getRecommendationInterests, saveRecommendationInterests } from '@/api/recommendation'

export function useAccountSecurity() {
  const loginLogVisible = ref(false)
  const loadingLogs = ref(false)
  const loginLogs = ref([])
  const loginLogPage = ref(1)
  const loginLogPageSize = 10
  const loadingPersonalization = ref(true)
  const savingPersonalization = ref(false)
  const personalizedEnabled = ref(true)
  const preferenceBoardIds = ref([])

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
      }
    } catch {
      personalizedEnabled.value = true
      preferenceBoardIds.value = []
      ElMessage.error('加载个性化推荐设置失败')
    } finally {
      loadingPersonalization.value = false
    }
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

  onMounted(loadPersonalization)

  return {
    loginLogPage,
    loginLogPageSize,
    loadingLogs,
    loadingPersonalization,
    loginLogVisible,
    loginLogs,
    openLoginLogs,
    pagedLoginLogs,
    personalizedEnabled,
    savingPersonalization,
    togglePersonalization,
  }
}
