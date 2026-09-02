defineOptions({ name: 'UnifiedSearchFeed' })

import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { searchArticles, searchUsers } from '@/api/search'
import { followUser, unfollowUser } from '@/api/userFollow'
import SearchArticleCard from '@/components/search/SearchArticleCard.vue'
import SearchUserRow from '@/components/search/SearchUserRow.vue'
import Masonry from '@/components/common/Masonry.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import articleNotFoundAssetUrl from '@/assets/images/article_not_found.png'
import userNotFoundAssetUrl from '@/assets/images/user_not_found.png'
import {
  captureFeedCardOrigin,
  captureFeedOpenFrom,
  getFeedCardOrigin,
} from '@/utils/feedNavigation'

function useUnifiedSearchFeed() {
  const route = useRoute()
  const router = useRouter()
  const shell = useHomeShellContext()

  const searchTab = ref('article') // article | user
  const loading = ref(false)
  const hasSearched = ref(false)
  const source = ref('empty')
  const articleRecords = ref([])
  const userRecords = ref([])
  const pageNum = ref(1)
  // 与首页瀑布流保持一致，列数相同时最后一行不会一边缺角
  const pageSize = ref(20)
  const total = ref(0)
  const followSavingIds = ref(new Set())

  // 搜索条件属于结果页自身状态。详情页打开后，当前全局路由会变为 /article/:id
  // 不能再直接读取 route.查询，否则普通搜索和 AI 搜索的背景结果都会被清空
  const resultKeyword = ref('')
  const resultAiRag = ref(false)
  const preferAiRag = computed(() => resultAiRag.value)
  const keyword = computed(() => resultKeyword.value)
  const masonryReloadKey = computed(() => {
    const first = articleRecords.value?.[0]?.article?.id
    return `${searchTab.value}-${pageNum.value}-${preferAiRag.value ? 1 : 0}-${first || ''}-${articleRecords.value.length}`
  })

  const feedList = computed(() =>
    searchTab.value === 'article' ? articleRecords.value : [],
  )
  const masonryCards = computed(() => {
    const list = articleRecords.value || []
    return list.map((entry, index) => {
      const article = entry?.article || {}
      const seed = Number(article?.id) || index + 1
      const titleLength = String(article?.title || '').length
      const titleHeight = Math.min(2, Math.max(1, Math.ceil(titleLength / 24))) * 24
      return {
        id: String(article?.id || `search-article-${index}`),
        entry,
        height: 170 + Math.abs(seed % 5) * 24 + titleHeight + 112,
      }
    })
  })

  let handledSearchSubmitVersion = -1
  // AI 搜索要几秒，连按两次回车会并发两个请求，谁后返回谁显示——
  // 很容易出现"搜 A 却显示 B 的结果"
  let searchSeq = 0

  async function runSearch(pn = 1) {
    const kw = keyword.value
    if (!kw) return
    const seq = ++searchSeq
    pageNum.value = pn
    loading.value = true
    hasSearched.value = true
    if (preferAiRag.value) {
      articleRecords.value = []
      userRecords.value = []
      total.value = 0
    }
    try {
      if (searchTab.value === 'user') {
        const res = await searchUsers({
          keyword: kw,
          pageNum: pageNum.value,
          pageSize: pageSize.value,
          ...(preferAiRag.value ? { ai: 1 } : {}),
        })
        if (seq !== searchSeq) return
        source.value = res.data?.source || 'empty'
        const page = res.data?.page || {}
        userRecords.value = page.records || []
        total.value = page.total || 0
        articleRecords.value = []
      } else {
        const res = await searchArticles({
          keyword: kw,
          pageNum: pageNum.value,
          pageSize: pageSize.value,
          ...(preferAiRag.value ? { ai: 1 } : {}),
        })
        if (seq !== searchSeq) return
        source.value = res.data?.source || 'empty'
        const page = res.data?.page || {}
        articleRecords.value = page.records || []
        total.value = page.total || 0
        userRecords.value = []
      }
    } finally {
      if (seq === searchSeq) loading.value = false
    }
  }

  function setSearchTab(tab) {
    if (searchTab.value === tab) return
    searchTab.value = tab
    const query = { ...route.query, tab }
    if (keyword.value) query.keyword = keyword.value
    if (preferAiRag.value) query.ai = '1'
    router.replace({ path: '/search', query })
  }

  function openArticle(entry, event, meta = {}) {
    const articleId = entry?.article?.id
    if (!articleId) return
    const card = event?.currentTarget?.closest?.('.note-card') || event?.currentTarget
    const cover = card?.querySelector?.('.note-cover') || card
    if (cover) {
      captureFeedCardOrigin(articleId, cover, {
        coverUrl: meta.previewUrl || entry?.article?.coverImg || '',
      })
    }
    getFeedCardOrigin(articleId)
    captureFeedOpenFrom(route.fullPath)
    router.push(`/article/${articleId}`)
  }

  function openUser(user) {
    if (!user?.id) return
    router.push(`/profile/${user.id}`)
  }

  function isFollowSaving(userId) {
    return followSavingIds.value.has(Number(userId))
  }

  function setFollowSaving(userId, saving) {
    const next = new Set(followSavingIds.value)
    const normalizedId = Number(userId)
    if (saving) next.add(normalizedId)
    else next.delete(normalizedId)
    followSavingIds.value = next
  }

  async function toggleUserFollow(user) {
    if (!user?.id || isFollowSaving(user.id)) return
    if (!(await ensureLoggedIn('关注用户需要登录'))) return
    if (Number(user.id) === Number(shell.userStore.id)) return

    const wasFollowing = Boolean(user.isFollowing)
    setFollowSaving(user.id, true)
    try {
      const res = await (wasFollowing ? unfollowUser(user.id) : followUser(user.id))
      user.isFollowing = !wasFollowing
      const currentFollowers = Number(user.followerCount) || 0
      user.followerCount = Math.max(0, currentFollowers + (wasFollowing ? -1 : 1))
      ElMessage.success(wasFollowing ? '已取消关注' : '关注成功')
    } finally {
      setFollowSaving(user.id, false)
    }
  }

  watch(
    () => [route.path, route.query.keyword, route.query.ai, route.query.tab],
    ([path, nextKeyword, nextAi, nextTab]) => {
      // 仅在真正进入搜索页时同步路由条件；详情弹窗期间保留原结果页的普通/AI 搜索状态
      if (path !== '/search') return
      resultKeyword.value = (nextKeyword ?? '').toString().trim()
      resultAiRag.value = nextAi === '1' || nextAi === 'true'
      if (nextTab === 'user') searchTab.value = 'user'
      else if (nextTab === 'article') searchTab.value = 'article'
    },
    { immediate: true },
  )

  watch(
    () => [route.path, shell.searchSubmitVersion.value],
    ([path, submitVersion]) => {
      if (path !== '/search' || !keyword.value) return
      if (hasSearched.value && handledSearchSubmitVersion === submitVersion) return
      handledSearchSubmitVersion = submitVersion
      runSearch(1)
    },
    { immediate: true },
  )

  watch(searchTab, (tab, prev) => {
    if (route.path !== '/search' || !keyword.value || tab === prev) return
    runSearch(1)
  })

  return {
    articleNotFoundImageUrl: articleNotFoundAssetUrl,
    feedList,
    hasSearched,
    isFollowSaving,
    keyword,
    loading,
    masonryCards,
    masonryReloadKey,
    openArticle,
    openUser,
    pageNum,
    pageSize,
    preferAiRag,
    runSearch,
    searchTab,
    setSearchTab,
    source,
    total,
    toggleUserFollow,
    userStore: shell.userStore,
    userRecords,
    userNotFoundImageUrl: userNotFoundAssetUrl,
  }
}

const {
  articleNotFoundImageUrl,
  feedList,
  hasSearched,
  isFollowSaving,
  keyword,
  loading,
  openArticle,
  openUser,
  pageNum,
  pageSize,
  preferAiRag,
  runSearch,
  searchTab,
  setSearchTab,
  total,
  toggleUserFollow,
  userRecords,
  userNotFoundImageUrl,
  userStore,
  masonryCards,
  masonryReloadKey,
} = useUnifiedSearchFeed()
