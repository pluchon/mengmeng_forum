import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listArticleTags, suggestArticleTags, submitArticleTagFeedback } from '@/api/articleTag'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  boardId: { type: [Number, String], default: null },
  title: { type: String, default: '' },
  content: { type: String, default: '' },
  label: { type: String, default: '帖子标签' },
  max: { type: Number, default: 5 },
})

const emit = defineEmits(['update:modelValue'])

const pickerOpen = ref(false)
const loading = ref(false)
const aiLoading = ref(false)
const feedbackLoading = ref(false)
const feedbackName = ref('')
const availableTags = ref([])
const tagMap = ref({})

const selectedIds = computed({
  get: () => (Array.isArray(props.modelValue) ? props.modelValue : []),
  set: (v) => emit('update:modelValue', v),
})

const selectedTags = computed(() =>
  selectedIds.value.map((id) => tagMap.value[id]).filter(Boolean),
)

function isSelected(id) {
  return selectedIds.value.includes(id)
}

function removeTag(id) {
  selectedIds.value = selectedIds.value.filter((x) => x !== id)
}

function toggleTag(t) {
  if (isSelected(t.id)) {
    removeTag(t.id)
    return
  }
  if (selectedIds.value.length >= props.max) {
    ElMessage.warning(`最多 ${props.max} 个标签`)
    return
  }
  tagMap.value = { ...tagMap.value, [t.id]: t }
  selectedIds.value = [...selectedIds.value, t.id]
}

async function loadTags() {
  if (!props.boardId) {
    availableTags.value = []
    return
  }
  loading.value = true
  try {
    const res = await listArticleTags(props.boardId)
    const list = Array.isArray(res?.data) ? res.data : []
    availableTags.value = list
    const m = { ...tagMap.value }
    for (const t of list) {
      m[t.id] = t
    }
    tagMap.value = m
  } catch (e) {
    availableTags.value = []
    ElMessage.error(e?.message || '加载标签失败')
  } finally {
    loading.value = false
  }
}

function openPicker() {
  if (!props.boardId) {
    ElMessage.warning('请先选择发布版块')
    return
  }
  pickerOpen.value = true
  loadTags()
}

async function runSuggest() {
  if (!props.boardId) {
    ElMessage.warning('请先选择发布版块')
    return
  }
  aiLoading.value = true
  try {
    const res = await suggestArticleTags({
      boardId: props.boardId,
      title: props.title,
      content: props.content,
    })
    const list = Array.isArray(res?.data) ? res.data : []
    const ids = []
    const m = { ...tagMap.value }
    for (const t of list) {
      if (ids.length >= props.max) break
      m[t.id] = t
      ids.push(t.id)
    }
    tagMap.value = m
    selectedIds.value = ids
    ElMessage.success('已填入推荐标签，可继续调整')
  } catch {
    ElMessage.error('推荐失败')
  } finally {
    aiLoading.value = false
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
    const res = await submitArticleTagFeedback({ boardId: props.boardId, proposedName: name })
    const tagId = res?.data?.tagId
    await loadTags()
    if (tagId && selectedIds.value.length < props.max) {
      const hit = availableTags.value.find((t) => t.id === tagId)
      if (hit) toggleTag(hit)
    }
    feedbackName.value = ''
    ElMessage.success(res?.data?.message || '标签已通过审核')
  } catch (e) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    feedbackLoading.value = false
  }
}

watch(
  () => props.boardId,
  () => {
    if (pickerOpen.value) loadTags()
  },
)
