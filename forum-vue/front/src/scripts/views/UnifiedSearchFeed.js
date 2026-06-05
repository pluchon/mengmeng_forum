import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { searchArticles, searchUsers } from '@/api/search'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useHomeMasonry } from '@/composables/useHomeMasonry'
import { DEFAULT_AVATAR } from '@/utils/constants'

export function useUnifiedSearchFeed() {
  const route = useRoute()
  const router = useRouter()
  const shell = useHomeShellContext()
  const defaultAvatar = DEFAULT_AVATAR

  const searchTab = ref('article') // article | user
  const loading = ref(false)
  const hasSearched = ref(false)
  const source = ref('empty')
  const articleRecords = ref([])
  const userRecords = ref([])
  const pageNum = ref(1)
  const pageSize = ref(12)
  const total = ref(0)

  const preferAiRag = computed(() => shell.aiSearchMode.value)

  const keyword = computed(() => (route.query.keyword ?? '').toString().trim())

  const feedList = computed(() =>
    searchTab.value === 'article' ? articleRecords.value : [],
  )

  const bannerText = computed(() => {
    if (!hasSearched.value || !keyword.value) return ''
    const mode = preferAiRag.value ? 'AI 语义' : '普通'
    if (searchTab.value === 'user') {
      if (source.value === 'empty') return `「${keyword.value}」· 未检索到对应的用户`
      return `「${keyword.value}」· ${mode} · 用户`
    }
    if (source.value === 'rag') return `「${keyword.value}」· ${mode} · 帖子`
    if (source.value === 'db') return `「${keyword.value}」· 标题/正文匹配`
    if (source.value === 'empty') return `「${keyword.value}」· 未检索到对应的帖子`
    return ''
  })

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

  function coverImageUrl(item) {
    return item.article?.coverImg || ''
  }

  function placeholderMinHeight(seed) {
    const n = Number(seed) || 0
    return `${160 + (n % 5) * 36}px`
  }

  function getRandomPastel() {
    const hues = [0, 200, 330, 260, 160]
    return `hsl(${hues[Math.floor(Math.random() * hues.length)]}, 70%, 92%)`
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
    bannerText,
    defaultAvatar,
    feedList,
    getRandomPastel,
    coverImageUrl,
    hasSearched,
    keyword,
    loading,
    masonryColumns,
    masonryRef,
    pageNum,
    pageSize,
    placeholderMinHeight,
    preferAiRag,
    runSearch,
    searchTab,
    setSearchTab,
    source,
    total,
    userRecords,
  }
}
