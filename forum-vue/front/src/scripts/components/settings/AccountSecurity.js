import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLoginLogs } from '@/api/settings'

export function useAccountSecurity() {
  const loginLogVisible = ref(false)
  const loadingLogs = ref(false)
  const loginLogs = ref([])

  async function openLoginLogs() {
    loginLogVisible.value = true
    loadingLogs.value = true
    try {
      const res = await getLoginLogs(20)
      if (res.code === 0 && Array.isArray(res.data)) {
        loginLogs.value = res.data
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
    loadingLogs,
    loginLogVisible,
    loginLogs,
    openLoginLogs,
  }
}
