import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { searchArticles } from '@/api/search'

export function useSearchArticle() {
  const route = useRoute()
  const router = useRouter()

  const keyword = ref('')
  const hasSearched = ref(false)
  const loading = ref(false)
  const source = ref('empty') // db | rag | empty

  const pageNum = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const records = ref([])

  const preferAiRag = computed(() => {
    const v = route.query.ai
    return v === '1' || v === 'true'
  })

  const displayKw = computed(() =>
    (keyword.value || (route.query.keyword ?? '')).toString().trim(),
  )

  const bannerText = computed(() => {
    if (!hasSearched.value) return ''
    const k = displayKw.value
    if (!k) return ''
    if (preferAiRag.value && source.value === 'rag') {
      return `基于「${k}」的 AI 语义搜索结果`
    }
    if (source.value === 'rag') {
      return `基于「${k}」的语义推荐（标题未精确命中）`
    }
    if (source.value === 'db') {
      return `基于「${k}」的搜索结果`
    }
    if (source.value === 'empty') {
      return `基于「${k}」未找到相关帖子`
    }
    return ''
  })

  let lastSearchSig = ''

  async function doSearch(pn = 1) {
    const kw = keyword.value?.trim()
    if (!kw) {
      ElMessage.warning('请输入关键词')
      return
    }

    pageNum.value = pn
    loading.value = true
    hasSearched.value = true
    try {
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
      const page = res.data?.page || { records: [], total: 0 }
      records.value = page?.records || []
      total.value = page?.total || 0
    } finally {
      loading.value = false
    }

    try {
      const nextQuery = { keyword: keyword.value.trim() }
      if (preferAiRag.value) nextQuery.ai = '1'
      router.replace({ path: '/search', query: nextQuery })
    } catch {
      // 忽略
    }
  }

  function openArticle(item) {
    const id = item?.article?.id
    if (id == null) return
    router.push(`/article/${id}`)
  }

  watch(
    () => [route.query.keyword, route.query.ai],
    () => {
      const kw = (route.query.keyword ?? '').toString()
      if (!kw) return
      const sig = `${kw}\0${route.query.ai ?? ''}`
      if (sig === lastSearchSig) return
      lastSearchSig = sig
      keyword.value = kw
      doSearch(1)
    },
    { immediate: true },
  )

  return {
    bannerText,
    doSearch,
    hasSearched,
    keyword,
    loading,
    openArticle,
    pageNum,
    pageSize,
    preferAiRag,
    records,
    source,
    total,
  }
}
