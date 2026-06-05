<template>
  <div class="article-tag-editor">
    <div class="article-tag-editor__head">
      <span class="article-tag-editor__label">{{ label }}</span>
      <button type="button" class="article-tag-editor__add" @click="openPicker">
        + 添加标签
      </button>
      <button
        v-if="boardId"
        type="button"
        class="article-tag-editor__ai"
        :disabled="aiLoading"
        @click="runSuggest"
      >
        {{ aiLoading ? '推荐中…' : 'AI 推荐' }}
      </button>
    </div>
    <div v-if="selectedTags.length" class="article-tag-editor__chips">
      <span
        v-for="t in selectedTags"
        :key="t.id"
        class="article-tag-chip"
        :class="`article-tag-chip--${t.colorKey || 'sky'}`"
      >
        {{ t.name }}
        <button type="button" class="article-tag-chip__x" aria-label="移除" @click="removeTag(t.id)">×</button>
      </span>
    </div>
    <p v-else class="article-tag-editor__hint">最多选择 5 个标签，便于他人发现你的帖子</p>

    <el-dialog v-model="pickerOpen" title="选择标签" width="420px" append-to-body>
      <div v-loading="loading" class="article-tag-picker">
        <button
          v-for="t in availableTags"
          :key="t.id"
          type="button"
          class="article-tag-chip article-tag-chip--pick"
          :class="[
            `article-tag-chip--${t.colorKey || 'sky'}`,
            { 'is-on': isSelected(t.id) },
          ]"
          :disabled="!isSelected(t.id) && selectedIds.length >= max"
          @click="toggleTag(t)"
        >
          {{ t.name }}
        </button>
      </div>
      <div class="article-tag-feedback">
        <span>没有合适的？</span>
        <el-input
          v-model="feedbackName"
          size="small"
          maxlength="12"
          placeholder="输入新标签名"
          class="article-tag-feedback__input"
        />
        <el-button size="small" :loading="feedbackLoading" @click="submitFeedback">
          提交反馈
        </el-button>
      </div>
      <template #footer>
        <el-button @click="pickerOpen = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
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
</script>

<style scoped>
.article-tag-editor {
  margin-bottom: 12px;
}
.article-tag-editor__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.article-tag-editor__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.article-tag-editor__add,
.article-tag-editor__ai {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-blank);
  cursor: pointer;
}
.article-tag-editor__ai {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}
.article-tag-editor__hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.article-tag-editor__chips,
.article-tag-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.article-tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  border: none;
  line-height: 1.4;
}
.article-tag-chip--pick {
  cursor: pointer;
  opacity: 0.85;
}
.article-tag-chip--pick.is-on {
  outline: 2px solid var(--el-color-primary);
  opacity: 1;
}
.article-tag-chip__x {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
  opacity: 0.7;
}
.article-tag-chip--sky { background: #e0f2fe; color: #0369a1; }
.article-tag-chip--rose { background: #ffe4e6; color: #be123c; }
.article-tag-chip--amber { background: #fef3c7; color: #b45309; }
.article-tag-chip--mint { background: #d1fae5; color: #047857; }
.article-tag-chip--violet { background: #ede9fe; color: #6d28d9; }
.article-tag-chip--slate { background: #f1f5f9; color: #475569; }
.article-tag-chip--orange { background: #ffedd5; color: #c2410c; }
.article-tag-chip--teal { background: #ccfbf1; color: #0f766e; }
.article-tag-feedback {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.article-tag-feedback__input {
  width: 140px;
}
</style>
