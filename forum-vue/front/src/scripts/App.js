import { computed, nextTick, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import TheHeader from '@/components/layout/TheHeader.vue'
import { useUserStore } from '@/stores/user'
import { useWebSocket } from '@/composables/useWebSocket'
import { useGroupVoiceStore } from '@/stores/groupVoice'
import { useThemeStore } from '@/stores/theme'
import { useMascotUiStore } from '@/stores/mascotUi'

export function useApp() {
  const route = useRoute()
  const userStore = useUserStore()
  const groupVoiceStore = useGroupVoiceStore()
  const themeStore = useThemeStore()
  const mascotUi = useMascotUiStore()
  const { initWebSocket, closeWebSocket } = useWebSocket()
  let mascotDomObserver = null

  function cleanupMascotDom() {
    if (typeof document === 'undefined') return
    document
      .querySelectorAll('#oml2d-stage, #oml2d-canvas, #oml2d-tips, #oml2d-statusBar')
      .forEach((node) => node.remove())
  }

  function stopMascotDomGuard() {
    if (!mascotDomObserver) return
    mascotDomObserver.disconnect()
    mascotDomObserver = null
  }

  function startMascotDomGuard() {
    cleanupMascotDom()
    if (mascotDomObserver || typeof MutationObserver === 'undefined' || !document.body) return
    mascotDomObserver = new MutationObserver(cleanupMascotDom)
    mascotDomObserver.observe(document.body, { childList: true, subtree: true })
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
  /** 深色模式覆盖游戏中心和匹配页，但不覆盖实际对战/游玩界面 */
  const isGameThemeExcluded = computed(() => (
    route.path.startsWith('/games/gobang/rooms/')
    || route.path.startsWith('/games/jinzi/rooms/')
    || route.path.startsWith('/games/tetris/pk/rooms/')
    || route.path === '/games/tetris'
  ))
  const isThemeAdaptedPage = computed(() => !isAuthPage.value && !isGameThemeExcluded.value)
  /** 登录/注册等认证页不展示看板娘 */
  const showMascot = computed(
    () => import.meta.env.VITE_ENABLE_MASCOT === 'true'
      && mascotUi.visible
      && !isAuthPage.value
      && !isGamePage.value,
  )

  watch(
    [() => themeStore.mode, isThemeAdaptedPage],
    ([mode, adapted]) => {
      if (typeof document === 'undefined') return
      document.documentElement.dataset.theme = adapted ? mode : 'light'
    },
    { immediate: true },
  )

  watch(
    [isAuthPage, isGamePage],
    async ([authPage, gamePage]) => {
      if (!authPage && !gamePage) return stopMascotDomGuard()
      await nextTick()
      startMascotDomGuard()
    },
    { immediate: true },
  )

  onUnmounted(stopMascotDomGuard)

  return {
    ElConfigProvider,
    TheHeader,
    isAuthPage,
    showGlobalHeader,
    showMascot,
    isThemeAdaptedPage,
    zhCn,
  }
}
