import { ElMessage } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { ref, watch } from 'vue'
import { listMusicMoodTagOptions } from '@/api/article'
import AppPagination from '@/components/common/AppPagination.vue'

// 与后端 Constant.MUSIC_MOOD_FILTER_MAX 对齐。再多的话 OR 召回等于没筛，
// 命中数排序的区分度也会被稀释
const MAX_MOODS = 5
// 一页 15 个，三行五列，翻页器位置不随内容跳
const PAGE_SIZE = 15

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  selected: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'apply'])

const draft = ref([])
const keyword = ref('')
const options = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageTotal = ref(1)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    // 打开时才从已保存的选择复制一份草稿，取消不影响已生效的筛选
    draft.value = [...(props.selected || [])]
    keyword.value = ''
    pageNum.value = 1
    await load()
  },
)

async function load() {
  loading.value = true
  try {
    const res = await listMusicMoodTagOptions({
      keyword: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    const page = res?.data
    options.value = Array.isArray(page?.records) ? page.records : []
    pageNum.value = Number(page?.pageNum) || pageNum.value
    pageTotal.value = Math.max(1, Number(page?.pages) || 1)
  } catch {
    options.value = []
    pageTotal.value = 1
  } finally {
    loading.value = false
  }
}

// 换关键词要回第一页，否则会停在旧条件下不存在的页码上
function onKeywordChange() {
  pageNum.value = 1
  return load()
}

function onPageChange(page) {
  pageNum.value = page
  return load()
}

function toggle(name) {
  const idx = draft.value.indexOf(name)
  if (idx >= 0) {
    draft.value = draft.value.filter((item) => item !== name)
    return
  }
  if (draft.value.length >= MAX_MOODS) {
    ElMessage.warning(`最多选择 ${MAX_MOODS} 个标签`)
    return
  }
  draft.value = [...draft.value, name]
}

function clearAll() {
  draft.value = []
}

function save() {
  emit('apply', [...draft.value])
  close()
}

function close() {
  emit('update:modelValue', false)
}
