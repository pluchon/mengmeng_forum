<template>
  <div class="article-ai-write">
    <el-popover
      v-model:visible="panelOpen"
      placement="bottom-end"
      :width="360"
      trigger="click"
      popper-class="article-ai-write-popper"
      @show="onPanelShow"
    >
      <template #reference>
        <button type="button" class="article-ai-write-trigger" :disabled="loading">
          <el-icon class="article-ai-write-trigger-icon"><MagicStick /></el-icon>
          AI 写作
        </button>
      </template>

      <div class="article-ai-write-panel">
        <p class="article-ai-write-title">AI 辅助写作</p>
        <p class="article-ai-write-hint">
          将按当前{{ editorMode === 'markdown' ? ' Markdown ' : '富文本' }}模式生成正文并填入编辑器
        </p>

        <label class="article-ai-write-label">模型</label>
        <el-select v-model="selectedRoute" class="article-ai-write-select" size="small">
          <el-option
            v-for="opt in llmOptions"
            :key="opt.id"
            :label="opt.label"
            :value="opt.id"
          >
            <div class="article-ai-write-opt">
              <img :src="opt.icon" alt="" class="article-ai-write-opt-icon" />
              <span>{{ opt.label }}</span>
              <span class="article-ai-write-opt-hint">{{ opt.hint }}</span>
            </div>
          </el-option>
        </el-select>

        <label class="article-ai-write-label">写作要求</label>
        <el-input
          v-model="prompt"
          type="textarea"
          :rows="4"
          maxlength="800"
          show-word-limit
          placeholder="例如：写一篇关于 Spring Boot 自动配置原理的教程，分章节、语气友好"
        />

        <div class="article-ai-write-actions">
          <el-button size="small" @click="panelOpen = false">取消</el-button>
          <el-button type="primary" size="small" :loading="loading" @click="runWrite">
            生成并填入
          </el-button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { aiWrite } from '@/api/ai'
import {
  MASCOT_TEXT_LLM_OPTIONS,
  llmRouteToWriteKind,
} from '@/constants/aiModels'

const props = defineProps({
  editorMode: {
    type: String,
    default: 'rich',
  },
  title: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['apply'])

const userStore = useUserStore()
const panelOpen = ref(false)
const loading = ref(false)
const prompt = ref('')
const selectedRoute = ref('qwen-flash')

const vipTierNum = computed(() => {
  if (Number(userStore.isAdmin) === 1) return 2
  return Number(userStore.vipTier) || 0
})

const llmOptions = computed(() => {
  const tier = vipTierNum.value
  return MASCOT_TEXT_LLM_OPTIONS.filter((o) => {
    if (o.maxOnly && tier < 2) return false
    if (o.vipOnly && tier < 1) return false
    return true
  })
})

function onPanelShow() {
  if (!llmOptions.value.some((o) => o.id === selectedRoute.value)) {
    selectedRoute.value = llmOptions.value[0]?.id || 'qwen-flash'
  }
}

function buildSystemPrompt() {
  const titlePart = props.title?.trim()
    ? `帖子标题：${props.title.trim()}。`
    : ''
  if (props.editorMode === 'markdown') {
    return `${titlePart}你是论坛写作助手。根据用户要求撰写帖子正文，只输出 Markdown 正文（可用标题、列表、加粗等），不要输出代码围栏，不要前言后记解释。`
  }
  return `${titlePart}你是论坛写作助手。根据用户要求撰写帖子正文，只输出可直接粘贴进富文本编辑器的 HTML 片段（使用 p、h2、h3、ul、li、strong、em 等标签），不要 Markdown，不要完整 html 文档，不要代码围栏，不要解释。`
}

function stripCodeFence(text) {
  let t = (text || '').trim()
  if (t.startsWith('```')) {
    t = t.replace(/^```[\w-]*\n?/, '').replace(/\n?```\s*$/, '').trim()
  }
  return t
}

async function runWrite() {
  if (blockIfMuted(userStore)) return
  const userPrompt = prompt.value.trim()
  if (!userPrompt) {
    ElMessage.warning('请先填写写作要求')
    return
  }
  const kind = llmRouteToWriteKind(selectedRoute.value)
  if (!kind) {
    ElMessage.warning('请选择有效模型')
    return
  }
  loading.value = true
  try {
    const res = await aiWrite({
      kind,
      messages: [
        { role: 'system', content: buildSystemPrompt() },
        { role: 'user', content: userPrompt },
      ],
    })
    if (res.code !== 0) {
      ElMessage.error(res.message || 'AI 写作失败')
      return
    }
    const text = stripCodeFence(res.data?.content || res.data?.text || '')
    if (!text) {
      ElMessage.warning('模型未返回有效正文')
      return
    }
    emit('apply', text)
    panelOpen.value = false
    ElMessage.success('已填入正文，可继续编辑')
  } catch (e) {
    ElMessage.error(e?.message || 'AI 写作请求失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.article-ai-write-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 13px;
  color: var(--editor-accent, #e91e8c);
  background: rgba(233, 30, 140, 0.08);
  border: 1px solid rgba(233, 30, 140, 0.35);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.article-ai-write-trigger:hover:not(:disabled) {
  background: rgba(233, 30, 140, 0.14);
  border-color: rgba(233, 30, 140, 0.55);
}
.article-ai-write-trigger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.article-ai-write-trigger-icon {
  font-size: 14px;
}
.article-ai-write-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.article-ai-write-title {
  margin: 0;
  font-weight: 600;
  font-size: 15px;
}
.article-ai-write-hint {
  margin: 0 0 4px;
  font-size: 12px;
  color: #888;
  line-height: 1.4;
}
.article-ai-write-label {
  font-size: 12px;
  color: #666;
}
.article-ai-write-select {
  width: 100%;
}
.article-ai-write-opt {
  display: flex;
  align-items: center;
  gap: 8px;
}
.article-ai-write-opt-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.article-ai-write-opt-hint {
  margin-left: auto;
  font-size: 11px;
  color: #aaa;
}
.article-ai-write-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
