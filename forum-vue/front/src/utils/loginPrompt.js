import { ElMessageBox } from 'element-plus'
import router from '@/router'

/**
 * 需登录操作：弹窗确认，而非直接跳转登录页。
 * @returns {Promise<boolean>} 用户选择去登录则为 true
 */
export async function promptLogin(_message = '') {
  try {
    await ElMessageBox.confirm('', '需要登录', {
      confirmButtonText: '去登录',
      showCancelButton: false,
      showClose: true,
      closeOnClickModal: false,
      customClass: 'login-required-box',
      confirmButtonClass: 'login-required-box__confirm',
    })
    const redirect = router.currentRoute.value?.fullPath
    if (redirect && redirect !== '/sign-in' && redirect !== '/sign-up') {
      await router.push({ path: '/sign-in', query: { redirect } })
    } else {
      await router.push('/sign-in')
    }
    return true
  } catch {
    return false
  }
}

/** 已登录返回 true；未登录弹窗并返回 false */
export async function ensureLoggedIn(message) {
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  if (userStore.isLoggedIn) return true
  await promptLogin(message)
  return false
}
