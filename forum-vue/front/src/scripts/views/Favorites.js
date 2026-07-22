import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { Plus, Lock, Unlock, Delete, Edit } from '@element-plus/icons-vue'
import {
  createFavoriteFolder,
  deleteFavoriteFolder,
  getMyFavoriteFolders,
  updateFavoriteFolder,
} from '@/api/favorite'

export function useFavorites() {
  const router = useRouter()

  const PlusIcon = Plus
  const LockIcon = Lock
  const UnlockIcon = Unlock
  const DeleteIcon = Delete
  const EditIcon = Edit

  const loading = ref(false)
  const folders = ref([])

  const createDialogVisible = ref(false)
  const editingFolder = ref(null)
  const folderForm = ref({ name: '', isPublic: 1 })
  const saving = ref(false)

  const dialogTitle = computed(() => (editingFolder.value ? '编辑收藏夹' : '新建收藏夹'))

  async function refresh() {
    loading.value = true
    try {
      const res = await getMyFavoriteFolders({ pageNum: 1, pageSize: 100 })
      if (res.code === 0) {
        folders.value = res.data?.records || []
      } else {
        ElMessage.error(res.message || '加载收藏夹失败')
      }
    } finally {
      loading.value = false
    }
  }

  function openFolder(folder) {
    if (!folder?.id) return
    router.push(`/favorites/folder/${folder.id}`)
  }

  function openCreate() {
    editingFolder.value = null
    folderForm.value = { name: '', isPublic: 1 }
    createDialogVisible.value = true
  }

  function openEdit(folder) {
    if (!folder?.id) return
    editingFolder.value = folder
    folderForm.value = { name: folder.name || '', isPublic: Number(folder.isPublic) || 0 }
    createDialogVisible.value = true
  }

  async function saveFolder() {
    const name = folderForm.value.name?.trim()
    if (!name) return ElMessage.warning('请输入收藏夹名称')

    saving.value = true
    try {
      if (editingFolder.value?.id) {
        const res = await updateFavoriteFolder({
          folderId: editingFolder.value.id,
          name,
          isPublic: folderForm.value.isPublic,
        })
        if (res.code === 0) {
          ElMessage.success('已更新')
          createDialogVisible.value = false
          await refresh()
        }
      } else {
        const res = await createFavoriteFolder({ name, isPublic: folderForm.value.isPublic })
        if (res.code === 0) {
          ElMessage.success('已创建')
          createDialogVisible.value = false
          await refresh()
        }
      }
    } finally {
      saving.value = false
    }
  }

  async function removeFolder(folder) {
    if (!folder?.id) return
    if (Number(folder.isDefault) === 1) return
    await ElMessageBox.confirm(`确定删除收藏夹「${folder.name || ''}」吗？（夹内收藏也会一起移除）`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    const res = await deleteFavoriteFolder(folder.id)
    if (res.code === 0) {
      ElMessage.success('已删除')
      await refresh()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  }

  onMounted(() => {
    refresh()
  })

  return {
    DeleteIcon,
    EditIcon,
    LockIcon,
    PlusIcon,
    UnlockIcon,
    createDialogVisible,
    dialogTitle,
    editingFolder,
    folderForm,
    folders,
    loading,
    openCreate,
    openEdit,
    openFolder,
    refresh,
    removeFolder,
    saveFolder,
    saving,
  }
}

