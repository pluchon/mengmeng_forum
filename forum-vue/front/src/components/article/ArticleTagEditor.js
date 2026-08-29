import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import AppPagination from '@/components/common/AppPagination.vue'
import { ElMessage } from 'element-plus'
import { listArticleTags, submitArticleTagFeedback, suggestArticleTags } from '@/api/articleTag'
import { iconConfirm } from '@/utils/appDialog'

const TAG_PAGE_SIZE = 15
const TAG_COLORS = [
  { key: 'mint', label: '绿色', hex: '#34d399' },
  { key: 'sky', label: '天蓝', hex: '#38bdf8' },
  { key: 'rose', label: '粉红', hex: '#fb7185' },
  { key: 'amber', label: '琥珀', hex: '#fbbf24' },
  { key: 'violet', label: '紫色', hex: '#a78bfa' },
  { key: 'slate', label: '灰蓝', hex: '#94a3b8' },
  { key: 'orange', label: '橙色', hex: '#fb923c' },
  { key: 'teal', label: '青色', hex: '#2dd4bf' },
]

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  boardId: { type: [Number, String], default: null },
  title: { type: String, default: '' },
  content: { type: String, default: '' },
  editorMode: { type: String, default: 'rich' },
  label: { type: String, default: '帖子标签' },
  max: { type: Number, default: 5 },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'ai-generating'])

const pickerOpen = ref(false)
const loading = ref(false)
const aiLoading = ref(false)
const feedbackLoading = ref(false)
const feedbackName = ref('')
const feedbackColor = ref('mint')
const searchKeyword = ref('')
const availableTags = ref([])
const tagMap = ref({})
const tagPageNum = ref(1)
const tagPageSize = TAG_PAGE_SIZE
const tagTotal = ref(0)
let searchTimer = null
let loadSequence = 0

const selectedIds = computed({
  get: () => (Array.isArray(props.modelValue) ? props.modelValue : []),
  set: (value) => emit('update:modelValue', value),
})

const selectedTags = computed(() => (
  selectedIds.value.map((id) => tagMap.value[id]).filter(Boolean)
))

function isSelected(id) {
  return selectedIds.value.includes(id)
}

function removeTag(id) {
  selectedIds.value = selectedIds.value.filter((item) => item !== id)
}

function toggleTag(tag) {
  if (isSelected(tag.id)) {
    removeTag(tag.id)
    return
  }
  if (selectedIds.value.length >= props.max) {
    ElMessage.warning(`最多 ${props.max} 个标签`)
    return
  }
  tagMap.value = { ...tagMap.value, [tag.id]: tag }
  selectedIds.value = [...selectedIds.value, tag.id]
}

function mergeTagMap(tags) {
  const nextMap = { ...tagMap.value }
  for (const tag of tags) {
    nextMap[tag.id] = tag
  }
  tagMap.value = nextMap
}

async function loadTags(pageNum = tagPageNum.value) {
  if (!props.boardId) {
    availableTags.value = []
    tagTotal.value = 0
    return
  }
  const requestedPage = Math.max(1, Number(pageNum) || 1)
  const currentSequence = ++loadSequence
  loading.value = true
  try {
    const res = await listArticleTags(props.boardId, requestedPage, searchKeyword.value.trim())
    if (currentSequence !== loadSequence) return
    const page = res?.data || {}
    const list = Array.isArray(page.records) ? page.records : []
    availableTags.value = list
    tagPageNum.value = Number(page.pageNum) || requestedPage
    tagTotal.value = Number(page.total) || 0
    mergeTagMap(list)
  } catch (error) {
    if (currentSequence !== loadSequence) return
    availableTags.value = []
    tagTotal.value = 0
    ElMessage.error(error?.message || '加载标签失败')
  } finally {
    if (currentSequence === loadSequence) loading.value = false
  }
}

function openPicker() {
  if (!props.boardId) {
    ElMessage.warning('请先选择发布版块')
    return
  }
  tagPageNum.value = 1
  pickerOpen.value = true
  void loadTags(1)
}

function handleTagPageChange(pageNum) {
  tagPageNum.value = pageNum
  void loadTags(pageNum)
}

function handleSearchInput() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    tagPageNum.value = 1
    void loadTags(1)
  }, 320)
}

function selectFeedbackColor(colorKey) {
  feedbackColor.value = colorKey
}

async function runSuggest() {
  if (!props.boardId) {
    ElMessage.warning('请先选择发布版块')
    return
  }
  // 推荐结果是整体替换 selectedIds，手动挑好的标签会被覆盖掉，先说清楚
  if (selectedIds.value.length > 0) {
    const ok = await iconConfirm({
      title: '用 AI 推荐替换当前标签？',
      message: `将清空你已选的 ${selectedIds.value.length} 个标签，改用 AI 推荐的结果。`,
      confirmText: '替换',
      cancelText: '保留当前',
    }).catch(() => false)
    if (!ok) return
  }
  aiLoading.value = true
  emit('ai-generating', true)
  try {
    const res = await suggestArticleTags({
      boardId: props.boardId,
      title: props.title,
      content: props.content,
      editorMode: props.editorMode,
    })
    const list = Array.isArray(res?.data) ? res.data : []
    const ids = []
    const nextMap = { ...tagMap.value }
    for (const tag of list) {
      if (ids.length >= props.max) break
      nextMap[tag.id] = tag
      ids.push(tag.id)
    }
    tagMap.value = nextMap
    selectedIds.value = ids
    if (ids.length === 0) {
      ElMessage.warning('没有找到合适的已有标签，请自行添加标签')
      return
    }
    ElMessage.success(`已推荐 ${ids.length} 个相关标签，可继续调整`)
  } catch (error) {
    ElMessage.error(error?.message || '推荐失败')
  } finally {
    aiLoading.value = false
    emit('ai-generating', false)
  }
}

async function submitFeedback() {
  const name = feedbackName.value.trim()
  if (!name) {
    ElMessage.warning('请输入标签名')
    return
  }
  feedbackLoading.value = true
  try {
    const res = await submitArticleTagFeedback({
      boardId: props.boardId,
      proposedName: name,
      colorKey: feedbackColor.value,
    })
    const tagId = res?.data?.tagId
    tagPageNum.value = 1
    await loadTags(1)
    if (tagId && selectedIds.value.length < props.max) {
      const hit = availableTags.value.find((tag) => tag.id === tagId)
      if (hit) toggleTag(hit)
    }
    feedbackName.value = ''
    feedbackColor.value = 'mint'
    ElMessage.success(res?.data?.message || '标签已通过审核')
  } catch (error) {
    ElMessage.error(error?.message || '提交失败')
  } finally {
    feedbackLoading.value = false
  }
}

watch(
  () => props.boardId,
  () => {
    loadSequence += 1
    tagPageNum.value = 1
    if (pickerOpen.value) void loadTags(1)
  },
)

onBeforeUnmount(() => {
  if (searchTimer) window.clearTimeout(searchTimer)
})
