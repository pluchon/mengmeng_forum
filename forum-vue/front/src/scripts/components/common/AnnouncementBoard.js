import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, House, Document, ChatLineRound, Promotion, WarningFilled } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { getNoticeCenterList } from '@/api/notice'
import '@/assets/styles/home.css'

function parseBody(bodyJson) {
  try {
    const o = typeof bodyJson === 'string' ? JSON.parse(bodyJson) : bodyJson || {}
    const highlights = Array.isArray(o.highlights) ? o.highlights : []
    const cover = typeof o.coverImageUrl === 'string' ? o.coverImageUrl.trim() : ''
    return { cover, highlights }
  } catch {
    return { cover: '', highlights: [] }
  }
}

function normalizeHighlight(h, idx) {
  if (typeof h === 'string') {
    const types = ['danger', 'success', 'warning']
    return { label: '要点', text: h, tagType: types[idx % types.length] }
  }
  const label = h.label || '要点'
  const text = h.text || ''
  const color = (h.labelColor || '').toLowerCase()
  let tagType = 'primary'
  if (color.includes('f53') || color.includes('ff') || color === 'red')
    tagType = 'danger'
  else if (color.includes('00b') || color.includes('green'))
    tagType = 'success'
  else if (color.includes('ff7') || color.includes('orange'))
    tagType = 'warning'
  return { label, text, tagType }
}

function iconForKind(k) {
  switch (Number(k)) {
    case 0:
      return House
    case 1:
      return Promotion
    case 2:
      return WarningFilled
    case 3:
      return Bell
    case 4:
      return Document
    default:
      return ChatLineRound
  }
}

export function useAnnouncementBoard() {
  const visible = ref(false)
  const loading = ref(false)
  const notices = ref([])
  const activeTab = ref('')

  const current = computed(() => notices.value.find(n => String(n.id) === activeTab.value) || null)

  const bodyInfo = computed(() => {
    if (!current.value)
      return { cover: '', highlights: [] }
    return parseBody(current.value.bodyJson)
  })

  const featureRows = computed(() => {
    return bodyInfo.value.highlights.map((h, idx) => normalizeHighlight(h, idx))
  })

  const mdHtml = computed(() => {
    const raw = current.value?.contentMarkdown?.trim()
    if (!raw)
      return '<p class="announcement-md-empty">暂无正文</p>'
    try {
      return marked.parse(raw, { async: false })
    } catch {
      return '<p class="announcement-md-empty">正文解析失败</p>'
    }
  })

  const isHeroTemplate = computed(() => current.value?.templateId === 'welcome_hero_right')

  const coverSrc = computed(() => bodyInfo.value.cover?.trim() || '')

  const show = async () => {
    visible.value = true
    loading.value = true
    notices.value = []
    activeTab.value = ''
    try {
      const res = await getNoticeCenterList()
      const list = Array.isArray(res.data) ? res.data : []
      notices.value = list
      if (list.length)
        activeTab.value = String(list[0].id)
    } catch (e) {
      ElMessage.error(e?.message || '公告加载失败')
    } finally {
      loading.value = false
    }
  }

  return {
    Bell,
    activeTab,
    bodyInfo,
    coverSrc,
    current,
    featureRows,
    iconForKind,
    isHeroTemplate,
    loading,
    mdHtml,
    notices,
    show,
    visible,
  }
}
