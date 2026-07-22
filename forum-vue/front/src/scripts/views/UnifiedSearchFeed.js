import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { searchArticles, searchUsers } from '@/api/search'
import { followUser, unfollowUser } from '@/api/userFollow'
import SearchArticleCard from '@/components/search/SearchArticleCard.vue'
import SearchUserRow from '@/components/search/SearchUserRow.vue'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useHomeMasonry } from '@/composables/useHomeMasonry'
import { ensureLoggedIn } from '@/utils/loginPrompt'

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
  const pageSize = ref(12)
  const total = ref(0)
  const followSavingIds = ref(new Set())

  const preferAiRag = computed(() => shell.aiSearchMode.value)

  const keyword = computed(() => (route.query.keyword ?? '').toString().trim())

  const feedList = computed(() =>
    searchTab.value === 'article' ? articleRecords.value : [],
  )

  const { containerRef: masonryRef, columns: masonryColumns } = useHomeMasonry(feedList, {
    columnWidth: 220,
    gap: 16,
  })

  let lastSig = ''

  async function runSearch(pn = 1) {
    const kw = keyword.value
    if (!kw) return
    pageNum.value = pn
    loading.value = true
    hasSearched.value = true
    try {
      if (searchTab.value === 'user') {
        const res = await searchUsers({
          keyword: kw,
          pageNum: pageNum.value,
          pageSize: pageSize.value,
          ...(preferAiRag.value ? { ai: 1 } : {}),
        })
        if (res.code !== 0) {
          ElMessage.error(res.message || '搜索失败')
          return
        }
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
        if (res.code !== 0) {
          ElMessage.error(res.message || '搜索失败')
          return
        }
        source.value = res.data?.source || 'empty'
        const page = res.data?.page || {}
        articleRecords.value = page.records || []
        total.value = page.total || 0
        userRecords.value = []
      }
    } finally {
      loading.value = false
    }
  }

  function setSearchTab(tab) {
    if (searchTab.value === tab) return
    searchTab.value = tab
    const query = { ...route.query, tab }
    if (keyword.value) query.keyword = keyword.value
    if (preferAiRag.value) query.ai = '1'
    router.replace({ path: '/search', query })
    if (keyword.value) runSearch(1)
  }

  function openArticle(entry) {
    const articleId = entry?.article?.id
    if (!articleId) return
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
      if (res.code !== 0) {
        ElMessage.error(res.message || '操作失败')
        return
      }
      user.isFollowing = !wasFollowing
      const currentFollowers = Number(user.followerCount) || 0
      user.followerCount = Math.max(0, currentFollowers + (wasFollowing ? -1 : 1))
      ElMessage.success(wasFollowing ? '已取消关注' : '关注成功')
    } finally {
      setFollowSaving(user.id, false)
    }
  }

  watch(
    () => route.query.tab,
    (tab) => {
      if (tab === 'user') searchTab.value = 'user'
      else if (tab === 'article') searchTab.value = 'article'
    },
    { immediate: true },
  )

  watch(
    () => [route.query.keyword, route.query.ai, shell.aiSearchMode.value],
    () => {
      const kw = keyword.value
      if (!kw) {
        hasSearched.value = false
        articleRecords.value = []
        userRecords.value = []
        return
      }
      const sig = `${kw}\0${preferAiRag.value ? '1' : '0'}\0${searchTab.value}`
      if (sig === lastSig && hasSearched.value) return
      lastSig = sig
      runSearch(1)
    },
    { immediate: true },
  )

  watch(searchTab, (tab, prev) => {
    if (!keyword.value || tab === prev) return
    lastSig = ''
    runSearch(1)
  })

  return {
    feedList,
    hasSearched,
    isFollowSaving,
    keyword,
    loading,
    masonryColumns,
    masonryRef,
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
  }
}

const {
  feedList,
  hasSearched,
  isFollowSaving,
  keyword,
  loading,
  masonryColumns,
  masonryRef,
  openArticle,
  openUser,
  pageNum,
  pageSize,
  runSearch,
  searchTab,
  setSearchTab,
  total,
  toggleUserFollow,
  userRecords,
  userStore,
} = useUnifiedSearchFeed()
