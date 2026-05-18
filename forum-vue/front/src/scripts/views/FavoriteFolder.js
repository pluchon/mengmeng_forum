import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFavoriteFolderArticles, getMyFavoriteFolders, moveArticleFavorite } from '@/api/favorite'

export function useFavoriteFolder() {
  const route = useRoute()

  const loading = ref(false)
  const folderId = computed(() => Number(route.params.folderId))

  const foldersLoading = ref(false)
  const myFolders = ref([])

  const records = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)

  const moveDialogVisible = ref(false)
  const movingArticleId = ref(null)
  const moveToFolderId = ref(null)
  const moving = ref(false)

  async function loadMyFolders() {
    foldersLoading.value = true
    try {
      const res = await getMyFavoriteFolders()
      if (res.code === 0) myFolders.value = res.data || []
    } finally {
      foldersLoading.value = false
    }
  }

  async function loadArticles(pn = 1) {
    if (!folderId.value) return
    pageNum.value = pn
    loading.value = true
    try {
      const res = await getFavoriteFolderArticles(folderId.value, { pageNum: pageNum.value, pageSize: pageSize.value })
      if (res.code === 0) {
        records.value = res.data?.records || []
        total.value = res.data?.total || 0
      } else {
        ElMessage.error(res.message || '加载失败')
      }
    } finally {
      loading.value = false
    }
  }

  function openMove(item) {
    const id = item?.article?.id
    if (!id) return
    movingArticleId.value = id
    moveToFolderId.value = null
    moveDialogVisible.value = true
  }

  async function confirmMove() {
    if (!movingArticleId.value) return
    if (!moveToFolderId.value) return ElMessage.warning('请选择目标收藏夹')
    moving.value = true
    try {
      const res = await moveArticleFavorite({ articleId: movingArticleId.value, toFolderId: moveToFolderId.value })
      if (res.code === 0) {
        ElMessage.success('已移动')
        moveDialogVisible.value = false
        await loadArticles(pageNum.value)
      } else {
        ElMessage.error(res.message || '移动失败')
      }
    } finally {
      moving.value = false
    }
  }

  onMounted(async () => {
    await loadMyFolders()
    await loadArticles(1)
  })

  return {
    confirmMove,
    folderId,
    foldersLoading,
    loadArticles,
    moveDialogVisible,
    moveToFolderId,
    moving,
    myFolders,
    openMove,
    pageNum,
    pageSize,
    records,
    total,
    loading,
  }
}

