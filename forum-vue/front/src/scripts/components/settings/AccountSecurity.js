import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLoginLogs, getSecurityAssessment } from '@/api/settings'

export function useAccountSecurity() {
  const loginLogVisible = ref(false)
  const loadingLogs = ref(false)
  const loginLogs = ref([])
  const loginLogPage = ref(1)
  const loginLogPageSize = 10
  const securityAssessment = ref(null)
  const securityAssessmentLoading = ref(false)
  const securityReassessing = ref(false)

  const pagedLoginLogs = computed(() => {
    const start = (loginLogPage.value - 1) * loginLogPageSize
    return loginLogs.value.slice(start, start + loginLogPageSize)
  })

  async function openLoginLogs() {
    loginLogVisible.value = true
    loadingLogs.value = true
    try {
      const res = await getLoginLogs(50)
      if (res.code === 0 && Array.isArray(res.data)) {
        loginLogs.value = res.data.map((row) => ({
          ...row,
          ipAddress: row.ipAddress || '—',
          ipRegion: row.ipRegion || '未知',
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

  async function loadSecurityAssessment() {
    securityAssessmentLoading.value = true
    try {
      const res = await getSecurityAssessment()
      securityAssessment.value = res.code === 0 ? res.data : null
    } catch {
      securityAssessment.value = null
    } finally {
      securityAssessmentLoading.value = false
    }
  }

  async function reassessSecurity() {
    if (securityAssessmentLoading.value) {
      return
    }

    securityAssessmentLoading.value = true
    securityReassessing.value = true
    const request = getSecurityAssessment()
      .then((res) => res)
      .catch(() => null)
    await new Promise((resolve) => window.setTimeout(resolve, 1800))
    try {
      const res = await request
      securityAssessment.value = res?.code === 0 ? res.data : null
    } finally {
      securityReassessing.value = false
      securityAssessmentLoading.value = false
    }
  }

  onMounted(loadSecurityAssessment)

  return {
    loginLogPage,
    loginLogPageSize,
    loadingLogs,
    loginLogVisible,
    loginLogs,
    openLoginLogs,
    pagedLoginLogs,
    securityAssessment,
    securityAssessmentLoading,
    securityReassessing,
    loadSecurityAssessment,
    reassessSecurity,
  }
}
