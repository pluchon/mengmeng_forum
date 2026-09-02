import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { aiPolish } from '@/api/ai'
import { sanitizeHtml } from '@/utils/security'

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
  textOnly: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['apply', 'generating'])

const userStore = useUserStore()
const loading = ref(false)
let activeRequestId = ''

function createClientRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `polish-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function stripCodeFence(text) {
  let normalized = (text || '').trim()
  if (normalized.startsWith('```')) {
    normalized = normalized.replace(/^```[\w-]*\n?/, '').replace(/\n?```\s*$/, '').trim()
  }
  return normalized
}

async function runPolish() {
  if (blockIfMuted(userStore)) return
  const content = String(props.content || '').trim()
  if (!content) {
    ElMessage.warning('请先写下内容，再进行润色')
    return
  }
  loading.value = true
  emit('generating', true)
  const requestId = createClientRequestId()
  activeRequestId = requestId
  try {
    const res = await aiPolish({
      title: props.title?.trim() || '',
      content,
      editorMode: props.editorMode,
      clientRequestId: requestId,
    })
    if (activeRequestId !== requestId) return
    const text = stripCodeFence(res.data?.content || res.data?.text || '')
    if (!text) {
      ElMessage.warning('模型未返回有效正文')
      return
    }
    const normalized = props.editorMode === 'rich' ? sanitizeHtml(text) : text
    if (!normalized) {
      ElMessage.warning('润色结果未通过格式校验，已保留原文')
      return
    }
    emit('apply', normalized)
    ElMessage.success('已完成润色，可继续编辑')
  } catch {
    // 失败原因由响应拦截器统一提示，这里不再叠加原始 HTTP 错误
  } finally {
    if (activeRequestId === requestId) {
      loading.value = false
      emit('generating', false)
    }
  }
}
