import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLoginLogs } from '@/api/settings'

export function useAccountSecurity() {
  const loginLogVisible = ref(false)
  const loadingLogs = ref(false)
  const loginLogs = ref([])
  const loginLogPage = ref(1)
  const loginLogPageSize = 10

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

  return {
    loginLogPage,
    loginLogPageSize,
    loadingLogs,
    loginLogVisible,
    loginLogs,
    openLoginLogs,
    pagedLoginLogs,
  }
}
