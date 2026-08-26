import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useUserStore } from '@/stores/user'
import { useWebSocket } from '@/composables/useWebSocket'
import { useMascotUiStore } from '@/stores/mascotUi'

export function useApp() {
  const route = useRoute()
  const userStore = useUserStore()
  const mascotUi = useMascotUiStore()
  const { initWebSocket, closeWebSocket } = useWebSocket()

  watch(
    () => userStore.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) {
        initWebSocket()
      } else {
        closeWebSocket()
      }
    },
    { immediate: true },
  )

  const isAuthPage = computed(() => route.meta.layout === 'auth')
  const isGamePage = computed(() => route.path === '/games' || route.path.startsWith('/games/'))
  const showMascot = computed(
    () => import.meta.env.VITE_ENABLE_MASCOT === 'true'
      && mascotUi.visible
      && !isAuthPage.value
      && !isGamePage.value
      && route.meta.hideMascot !== true,
  )

  return {
    ElConfigProvider,
    isAuthPage,
    showMascot,
    zhCn,
  }
}
