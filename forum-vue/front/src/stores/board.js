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
    } catch (error) {
      console.error('获取版块列表异常', error)
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
    } catch (error) {
      console.error('获取分类列表异常', error)
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
  persist: true // 如果需要在刷新后记住当前所在的版块，也可以开启持久化
})
