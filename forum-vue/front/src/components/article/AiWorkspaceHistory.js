import { ref, watch } from 'vue'
import { apiErrorCode } from '@/utils/apiData'
import { ElMessage } from 'element-plus'
import { getAiWorkspaceVersions, selectAiWorkspaceVersion } from '@/api/ai'

const props = defineProps({
  workspaceId: {
    type: Number,
    default: null,
  },
})

const emit = defineEmits(['selected'])

const versions = ref([])
const loading = ref(false)
const selectingId = ref(null)
const error = ref('')
const noPermission = ref(false)

watch(
  () => props.workspaceId,
  () => loadVersions(),
  { immediate: true },
)

async function loadVersions() {
  versions.value = []
  error.value = ''
  noPermission.value = false
  if (!props.workspaceId) return
  loading.value = true
  try {
    const res = await getAiWorkspaceVersions(props.workspaceId)
    if (res.code === 0) {
      versions.value = Array.isArray(res.data) ? res.data : []
      return
    }
    error.value = res.message || '版本加载失败'
  } catch (requestError) {
    const code = apiErrorCode(requestError)
    noPermission.value = code === 1003 || code === 1106
    error.value = noPermission.value ? '' : (requestError?.message || '版本加载失败')
  } finally {
    loading.value = false
  }
}

async function selectVersion(version) {
  if (!props.workspaceId || !version?.id || selectingId.value) return
  selectingId.value = version.id
  try {
    const res = await selectAiWorkspaceVersion(props.workspaceId, version.id)
    if (res.code !== 0) {
      ElMessage.error(res.message || '版本选择失败')
      return
    }
    versions.value = versions.value.map((item) => ({
      ...item,
      selected: item.id === version.id,
    }))
    emit('selected', version)
  } catch {
    // 拦截器已弹出真实原因，这里不再重复提示
  } finally {
    selectingId.value = null
  }
}
