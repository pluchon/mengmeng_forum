import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import TheHeader from '@/components/layout/TheHeader.vue'
import { useUserStore } from '@/stores/user'
import { useWebSocket } from '@/composables/useWebSocket'

export function useApp() {
  const route = useRoute()
  const userStore = useUserStore()
  const { initWebSocket, closeWebSocket } = useWebSocket()

  watch(
    () => userStore.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) initWebSocket()
      else closeWebSocket()
    },
    { immediate: true },
  )
  const isAuthPage = computed(() => route.meta.layout === 'auth')
  /** 主壳（与首页同布局）自带顶栏与侧栏，不重复渲染全局 TheHeader */
  const showGlobalHeader = computed(
    () => !isAuthPage.value && !route.matched.some(r => r.meta?.shell),
  )

  return {
    ElConfigProvider,
    TheHeader,
    isAuthPage,
    showGlobalHeader,
    zhCn,
  }
}
