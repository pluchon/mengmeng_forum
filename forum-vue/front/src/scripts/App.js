import { computed, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import TheHeader from '@/components/layout/TheHeader.vue'
import { useUserStore } from '@/stores/user'
import { useWebSocket } from '@/composables/useWebSocket'
import { useGroupVoiceStore } from '@/stores/groupVoice'

export function useApp() {
  const route = useRoute()
  const userStore = useUserStore()
  const groupVoiceStore = useGroupVoiceStore()
  const { initWebSocket, closeWebSocket } = useWebSocket()

  function cleanupMascotDom() {
    if (typeof document === 'undefined') return
    document
      .querySelectorAll('#oml2d-stage, #oml2d-canvas, #oml2d-tips, #oml2d-statusBar')
      .forEach((node) => node.remove())
  }

  watch(
    () => userStore.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) {
        initWebSocket()
        void groupVoiceStore.restorePersistedSession()
      } else {
        closeWebSocket()
        void groupVoiceStore.leave(false)
      }
    },
    { immediate: true },
  )
  const isAuthPage = computed(() => route.meta.layout === 'auth')
  /** 主壳（与首页同布局）自带顶栏与侧栏，不重复渲染全局 TheHeader */
  const showGlobalHeader = computed(
    () => !isAuthPage.value && !route.matched.some(r => r.meta?.shell),
  )
  /** 游戏页面是独立沉浸式界面，不展示看板娘模型 */
  const isGamePage = computed(() => route.path === '/games' || route.path.startsWith('/games/'))
  /** 登录/注册等认证页不展示看板娘 */
  const showMascot = computed(
    () => import.meta.env.VITE_ENABLE_MASCOT === 'true' && !isAuthPage.value && !isGamePage.value,
  )

  watch(
    isGamePage,
    async (gamePage) => {
      if (!gamePage) return
      await nextTick()
      cleanupMascotDom()
    },
    { immediate: true },
  )

  return {
    ElConfigProvider,
    TheHeader,
    isAuthPage,
    showGlobalHeader,
    showMascot,
    zhCn,
  }
}
