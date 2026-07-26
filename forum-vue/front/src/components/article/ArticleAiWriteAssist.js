import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { aiWrite } from '@/api/ai'

const props = defineProps({
  editorMode: {
    type: String,
    default: 'rich',
  },
  title: {
    type: String,
    default: '',
  },
  content: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['apply', 'workspaceReady', 'generating'])

const userStore = useUserStore()
const panelOpen = ref(false)
const loading = ref(false)
const prompt = ref('')
function onPanelShow() {}
const plainContent = computed(() => String(props.content || '').replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim())
const isPolish = computed(() => plainContent.value.length > 20)
const panelTitle = computed(() => (isPolish.value ? 'AI 润色' : 'AI 灵感'))

function buildSystemPrompt() {
  const titlePart = props.title?.trim() ? `帖子标题：${props.title.trim()}。` : ''
  const naturalStyle = '使用自然、克制、像真人分享的中文表达。禁止输出机械化套话、emoji、颜文字、图标列表、编号列表、Markdown 代码围栏、前言或解释。'
  if (props.editorMode === 'markdown') {
    return `${titlePart}你是论坛写作助手。${naturalStyle}只输出可直接粘贴的 Markdown 正文；除非用户正文天然需要，不要使用标题或列表。`
  }
  return `${titlePart}你是论坛写作助手。${naturalStyle}只输出可直接粘贴进富文本编辑器的 HTML 片段，以 p 为主；不要 Markdown、完整 html 文档或代码围栏。`
}

function stripCodeFence(text) {
  let normalized = (text || '').trim()
  if (normalized.startsWith('```')) {
    normalized = normalized.replace(/^```[\w-]*\n?/, '').replace(/\n?```\s*$/, '').trim()
  }
  return normalized
}

async function runWrite() {
  if (blockIfMuted(userStore)) return
  const userPrompt = prompt.value.trim()
  if (!userPrompt) {
    ElMessage.warning('请先填写写作要求')
    return
  }
  loading.value = true
  emit('generating', true)
  try {
    const task = isPolish.value
      ? `当前正文：${plainContent.value}\n修改方向：${userPrompt}\n请保留原意，直接输出润色后的完整正文。`
      : `写作主题或角度：${userPrompt}\n请直接输出一段可发布的帖子正文。`
    const res = await aiWrite({
      messages: [
        { role: 'system', content: buildSystemPrompt() },
        { role: 'user', content: task },
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
    if (res.data?.workspaceId) {
      emit('workspaceReady', {
        workspaceId: Number(res.data.workspaceId),
        versionId: Number(res.data.workspaceVersionId) || null,
      })
    }
    panelOpen.value = false
    ElMessage.success('已填入正文，可继续编辑')
  } catch (error) {
    ElMessage.error(error?.message || 'AI 写作请求失败')
  } finally {
    loading.value = false
    emit('generating', false)
  }
}
