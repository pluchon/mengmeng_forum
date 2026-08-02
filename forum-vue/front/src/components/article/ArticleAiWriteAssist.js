import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { aiPolish } from '@/api/ai'

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

const emit = defineEmits(['apply', 'generating'])

const userStore = useUserStore()
const loading = ref(false)

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
  try {
    const res = await aiPolish({
      title: props.title?.trim() || '',
      content,
      editorMode: props.editorMode,
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
    ElMessage.success('已完成润色，可继续编辑')
  } catch (error) {
    ElMessage.error(error?.message || 'AI 写作请求失败')
  } finally {
    loading.value = false
    emit('generating', false)
  }
}
