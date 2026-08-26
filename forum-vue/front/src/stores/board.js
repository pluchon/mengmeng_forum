import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getTopBoardList } from '../api/board'
import { ElMessage } from 'element-plus'

export const useBoardStore = defineStore('board', () => {
  const boardList = ref([])
  const orderStatus = ref(0)
  const currentBoardId = ref(null)

  const categoryList = ref([])

  async function fetchBoardList() {
    try {
      const res = await getTopBoardList(orderStatus.value)
      if (res.code === 0) {
        boardList.value = res.data || []
      } else {
        ElMessage.error(res.message || '获取版块列表失败')
      }
    } catch {
      ElMessage.error('获取版块列表失败，请稍后重试')
    }
  }

  async function fetchCategoryList() {
    try {
      // 动态导入以避免循环依赖问题
      const { getCategoryWithBoards } = await import('../api/board')
      const res = await getCategoryWithBoards()
      if (res.code === 0) {
        categoryList.value = res.data || []
      }
    } catch {
      ElMessage.error('获取分类列表失败，请稍后重试')
    }
  }

  function setOrderStatus(status) {
    orderStatus.value = status
    fetchBoardList()
  }

  function setCurrentBoardId(id) {
    currentBoardId.value = id
  }

  return { boardList, categoryList, orderStatus, currentBoardId, fetchBoardList, fetchCategoryList, setOrderStatus, setCurrentBoardId }
}, {
  // 分类/版块名会随种子变更，禁止整表持久化，否则会长期卡在旧导航
  persist: {
    pick: ['orderStatus', 'currentBoardId'],
  },
})
