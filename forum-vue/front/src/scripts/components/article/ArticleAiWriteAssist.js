import { ref } from 'vue'
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
})

const emit = defineEmits(['apply'])

const userStore = useUserStore()
const panelOpen = ref(false)
const loading = ref(false)
const prompt = ref('')
function onPanelShow() {}

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
  loading.value = true
  try {
    const res = await aiWrite({
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
