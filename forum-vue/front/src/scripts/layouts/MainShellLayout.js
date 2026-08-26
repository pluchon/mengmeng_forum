import { computed, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import HomeSidebar from '@/components/layout/HomeSidebar.vue'
import HomeTopBar from '@/components/layout/HomeTopBar.vue'
import HomeFeed from '@/views/HomeFeed.vue'
import UnifiedSearchFeed from '@/views/UnifiedSearchFeed.vue'
import Profile from '@/views/Profile.vue'
import CreativeCenter from '@/views/CreativeCenter.vue'
import { provideHomeShellContext } from '@/composables/useHomeShell'
import { getFeedReturnPath } from '@/utils/feedNavigation'

const route = useRoute()
const isPortalRoute = computed(() => route.matched.some((item) => item.meta?.portal))
const isShellBare = computed(() => route.matched.some((r) => r.meta?.shellBare))
const hideShellTopBar = computed(() => route.matched.some((r) => r.meta?.hideShellTopBar))
const isShellParticle = computed(() => route.matched.some((r) => r.meta?.shellParticle))
const isArticleDetailRoute = computed(() => route.name === 'articleDetail')
// 进入帖子详情前记住当前壳层页，热帖榜等来源用它做背景
const lastShellComponent = shallowRef(HomeFeed)

const articleBackgroundComponent = computed(() => {
  // 路由变化时重新读取本次详情来源：个人主页 / 创作中心 / 搜索 / 热帖榜 / 社区各自留背景
  void route.fullPath
  const returnPath = getFeedReturnPath()
  if (route.query.from === 'profile' || returnPath.startsWith('/profile')) {
    return Profile
  }
  if (route.query.from === 'creative' || returnPath.startsWith('/creative')) {
    return CreativeCenter
  }
  if (returnPath.startsWith('/search')) {
    return UnifiedSearchFeed
  }
  if (route.query.from === 'hot') {
    return lastShellComponent.value || HomeFeed
  }
  return HomeFeed
})

function resolveShellLayer(Component) {
  if (!isArticleDetailRoute.value) {
    if (Component) {
      lastShellComponent.value = Component
    }
    return Component
  }
  return articleBackgroundComponent.value
}

provideHomeShellContext()
