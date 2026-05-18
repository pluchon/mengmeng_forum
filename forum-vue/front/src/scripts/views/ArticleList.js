import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useBoardStore } from '@/stores/board'
import { getArticleList } from '@/api/article'
import '@/assets/styles/article.css'

export function useArticleList() {
  const route = useRoute()
  const boardStore = useBoardStore()
  const loading = ref(false)
  const articleList = ref([])
  const page = ref(1)
  const pageSize = ref(15)
  const total = ref(0)

  const boardName = ref('')

  onMounted(() => {
    if (boardStore.boardList.length === 0) boardStore.fetchBoardList()
    fetch(1)
  })

  watch(() => route.params.id, () => fetch(1))

  async function fetch(p = 1) {
    page.value = p
    loading.value = true
    const boardId = route.params.id
    const board = boardStore.boardList.find(b => String(b.id) === String(boardId))
    boardName.value = board?.name || ''
    try {
      const res = await getArticleList({ boardId, pageNum: p, pageSize: pageSize.value })
      if (res.code === 0) {
        articleList.value = res.data?.records || []
        total.value = res.data?.total || 0
      }
    } finally {
      loading.value = false
    }
  }

  return {
    articleList,
    boardName,
    fetch,
    loading,
    page,
    pageSize,
    total,
  }
}
