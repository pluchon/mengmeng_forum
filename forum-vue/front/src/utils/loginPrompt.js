import { iconConfirm } from '@/utils/appDialog'
import router from '@/router'

let activeLoginPrompt = null

// 需登录操作：弹窗确认，而非直接跳转登录页
export async function promptLogin(_message = '') {
  if (activeLoginPrompt) return activeLoginPrompt

  activeLoginPrompt = (async () => {
    try {
      // 游客随时可以放弃这个操作接着逛，所以给一个明确的"再逛逛"出口，
      // 点遮罩和右上角关闭也都放行；单按钮弹窗会把人困在这里
      const ok = await iconConfirm({
        title: '需要登录',
        message: '登录后即可继续当前操作。',
        confirmText: '去登录',
        cancelText: '再逛逛',
        closeOnClickModal: true,
        showClose: true,
      })
      if (!ok) return false
      const redirect = router.currentRoute.value?.fullPath
      if (redirect && redirect !== '/sign-in' && redirect !== '/sign-up') {
        await router.push({ path: '/sign-in', query: { redirect } })
      } else {
        await router.push('/sign-in')
      }
      return true
    } catch {
      return false
    } finally {
      activeLoginPrompt = null
    }
  })()

  return activeLoginPrompt
}

// 已登录返回 true；未登录弹窗并返回 false
export async function ensureLoggedIn(message) {
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  if (userStore.isLoggedIn) return true
  await promptLogin(message)
  return false
}
