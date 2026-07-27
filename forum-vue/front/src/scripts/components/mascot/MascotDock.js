import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ZoomIn } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import {
  getMascotPublicModels,
  getMascotQuotaHint,
  getMascotRelatedRecommendations,
  streamMascotChat,
  setMascotModel,
  getCompanionSessions,
  getCompanionMessages,
  deleteCompanionSession,
} from '@/api/mascot'
import { aiPriceEstimate } from '@/api/ai'
import { DEFAULT_AVATAR } from '@/utils/constants'
import {
  MASCOT_IMAGE_QUALITY_OPTIONS,
  findImageQualityOption,
} from '@/constants/aiModels'
import { clientOssUrl } from '@/utils/clientOss'
import { pickThinkingPhrase, startThinkingRotation } from '@/utils/mascotThinking'
import { pickMascotIdlePhrase } from '@/utils/mascotIdleTips'
import { formatAiUsageLine, usageStatsFromApi } from '@/utils/aiUsageDisplay'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { marked } from 'marked'
import { sanitizeHtml } from '@/utils/security'

marked.setOptions({ gfm: true, breaks: true })

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 看板娘 AI 回复：Markdown → HTML（表格、列表、加粗等） */
function renderMascotMarkdown(content, stripInlineImages = false) {
  let raw = (content || '').trim()
  if (!raw) return ''
  if (stripInlineImages) {
    raw = raw.replace(/!\[[^\]]*]\([^)]+\)/g, '').trim()
  }
  try {
    return sanitizeHtml(marked.parse(raw, { async: false }))
  } catch {
    return `<p>${escapeHtml(raw)}</p>`
  }
}

function isSafeMascotImageUrl(url) {
  const s = String(url || '').trim()
  return s.startsWith('https://') && s.length <= 2048
}

const companionXiaomai = clientOssUrl('xiaomai.webp')
const companionMiku = clientOssUrl('miku.webp')

export function useMascotDock() {
  const OFFSET_KEY = 'mascot_dock_offset_v1'
  const SCALE_KEY = 'mascot_stage_scale_v2'
  const ANCHOR_KEY = 'mascot_stage_anchored_v2'
  const LLM_CHAT_KEY = 'mascot_llm_chat_v1'
  const LLM_WRITING_KEY = 'mascot_llm_writing_v3'
  const LLM_HELP_KEY = 'mascot_llm_help_v3'
  const IMAGE_QUALITY_KEY = 'mascot_image_quality_v1'
  const GUEST_MASCOT_CODE_KEY = 'mascot_guest_model_code_v1'
  const STAGE_BASE_W = 400
  const STAGE_BASE_H = 460
  const relatedDialogVisible = ref(false)
  const relatedDialogItems = ref([])

  const uiLabels = {
    ariaRoot: '看板娘',
    scaleTitle: '缩放显示',
    scaleHint: '拖动滑块调整看板娘显示大小',
    brandTitle: '小萌',
    statusOnline: '在线',
    statusOffline: '不在线',
    defaultNickname: '用户',
    sessionListTitle: '会话',
    newSession: '新建会话',
    guest: '未登录',
    appearanceEmpty: '暂无上架模型，请管理员在后台配置并上架。',
    applyAppearance: '选用此形象',
    today: '今天',
    openImageInNewTab: '在新标签打开',
    typing: '正在输入',
    regenerate: '重新生成',
    sessionEmpty: '暂无会话，点击 + 新建',
    untitledSession: '新会话',
    alreadyNewSession: '当前已是新会话，直接输入即可开始对话',
    deleteSession: '删除会话',
    chatEmptyHint: '暂无消息，在下方输入开始对话',
  }

  const LOCAL_SESSIONS_KEY_PREFIX = 'mascot_local_sessions_v3'
  const LEGACY_SESSIONS_KEY = 'mascot_local_sessions_v2'

  function localSessionsStorageKey() {
    const uid = userStore.id
    if (uid != null && uid !== '') {
      return `${LOCAL_SESSIONS_KEY_PREFIX}_${uid}`
    }
    return `${LOCAL_SESSIONS_KEY_PREFIX}_guest`
  }
  
  /** 文本模型由服务端按会员档位自动选择；前端不提供切换。 */
  const ALL_LLM_OPTIONS = [{ id: 'qwen-flash' }]
  const IMAGE_MODEL_OPTIONS = MASCOT_IMAGE_QUALITY_OPTIONS
  
  const userStore = useUserStore()
  const router = useRouter()
  const pointsWallet = usePointsWalletStore()
  const stageHost = ref(null)
  const stageUseFallback = ref(false)
  const scrollbarFs = ref(null)
  const oml2d = ref(null)
  
  const assistantOpen = ref(false)
  /** chat | drawing | appearance */
  const activeNav = ref('chat')
  const catalog = ref([])
  const activeCode = ref('lafei')
  const pendingCode = ref('lafei')
  const messages = ref([])
  const draft = ref('')
  const loading = ref(false)
  const imageGenerating = ref(false)
  const deletingSessionId = ref('')
  const sessionId = ref('')
  const localSessionsByMode = ref({
    chat: [],
    drawing: [],
  })
  const activeLocalSessionId = ref({
    chat: '',
    drawing: '',
  })
  const activeSkill = ref('chat')
  
  const selectedLlmChat = ref('qwen-flash')
  const imageQuality = ref('normal')
  const estimatePoints = ref(null)
  const estimateLoading = ref(false)
  const usePointsBilling = ref(false)
  const quotaHint = ref({ percent: 0, canUsePointsPay: false, quotaLabel: '' })

  const estimateHintText = computed(() => {
    if (estimateLoading.value) return '正在估算…'
    if (usePointsBilling.value && estimatePoints.value != null) {
      return `萌币扣费模式：预估约 ${estimatePoints.value} 积分/次`
    }
    if (isVip.value) {
      const p = quotaHint.value?.percent ?? 0
      const label = quotaHint.value?.quotaLabel || '会员额度'
      return p > 0 ? `${label} 已用 ${p}%（默认走额度，不扣萌币）` : '默认使用会员额度，不扣萌币'
    }
    return '默认使用每日免费次数，不扣萌币'
  })

  const showPointsPayButton = computed(() => {
    return isVip.value
      && activeNav.value !== 'drawing'
      && activeNav.value !== 'appearance'
      && Boolean(quotaHint.value?.canUsePointsPay)
  })
  const stageHovered = ref(false)
  let chatStreamAbort = null
  let stopThinkingRotation = null
  let idleTipsTimer = null
  let stageTipTimer = null
  const stageTipText = ref('')

  function clearStageCloudTip() {
    if (stageTipTimer) {
      clearTimeout(stageTipTimer)
      stageTipTimer = null
    }
    stageTipText.value = ''
    try {
      oml2d.value?.tipsMessage?.('', 0, 0)
    } catch {
      /* ignore */
    }
  }

  function showStageCloudTip(text, durationMs = 3000) {
    const msg = String(text || '').trim()
    if (!msg) {
      clearStageCloudTip()
      return
    }
    if (assistantOpen.value || stageUseFallback.value) return
    clearStageCloudTip()
    stageTipText.value = msg
    if (durationMs > 0) {
      stageTipTimer = setTimeout(() => {
        stageTipText.value = ''
        stageTipTimer = null
      }, durationMs)
    }
  }

  function stopMascotIdleTips() {
    if (idleTipsTimer) {
      clearInterval(idleTipsTimer)
      idleTipsTimer = null
    }
  }

  function startMascotIdleTips() {
    stopMascotIdleTips()
    const show = () => {
      if (assistantOpen.value || stageUseFallback.value) return
      showStageCloudTip(pickMascotIdlePhrase(), 4200)
    }
    setTimeout(show, 4000)
    idleTipsTimer = setInterval(show, 26000 + Math.floor(Math.random() * 14000))
  }

  function clearThinkingRotation() {
    if (stopThinkingRotation) {
      stopThinkingRotation()
      stopThinkingRotation = null
    }
  }
  
  const FLASH_LLM = ['qwen-flash']
  
  function llmStorageKey() {
    return LLM_CHAT_KEY
  }
  
  function currentLlmStorageKey() {
    return LLM_CHAT_KEY
  }
  
  const selectedLlm = computed({
    get() {
      return selectedLlmChat.value
    },
    set(v) {
      selectedLlmChat.value = v
    },
  })

  const activeImageOption = computed(() => findImageQualityOption(imageQuality.value) || IMAGE_MODEL_OPTIONS[0])

  const scalePopoverOpen = ref(false)
  
  const dragOffset = ref({ x: 0, y: 0 })
  const stageScale = ref(0.5)
  
  let stageGesture = false
  let stageMoved = false
  let stageStart = { px: 0, py: 0, ox: 0, oy: 0 }
  
  const inputPlaceholder = computed(() => {
    return '随心输入'
  })
  
  const vipTierNum = computed(() => {
    if (Number(userStore.isAdmin) === 1)
      return 2
    return Number(userStore.vipTier) || 0
  })

  const isVip = computed(() => {
    if (Number(userStore.isAdmin) === 1)
      return true
    const t = vipTierNum.value
    if (t <= 0)
      return false
    const expRaw = userStore.vipExpireAt
    if (!expRaw)
      return true
    const exp = new Date(expRaw).getTime()
    if (Number.isNaN(exp))
      return true
    return Date.now() <= exp
  })

  const imageModelOptions = computed(() => {
    const tier = vipTierNum.value
    return IMAGE_MODEL_OPTIONS.filter((o) => !o.vipOnly || tier >= 1)
  })
  
  const llmOptions = computed(() => {
    const tier = vipTierNum.value
    const list = ALL_LLM_OPTIONS.filter((o) => {
      if (o.maxOnly && tier < 2)
        return false
      if (o.vipOnly && tier < 1)
        return false
      return true
    })
    return list
  })
  
  const sessionListForNav = computed(() => {
    const nav = activeNav.value
    if (nav === 'appearance') return []
    return [...(localSessionsByMode.value[nav] || [])]
  })

  function newLocalSessionId() {
    return typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  }

  function migrateSessionsPayload(data) {
    if (!data || typeof data !== 'object') return data
    const byMode = data.byMode && typeof data.byMode === 'object' ? data.byMode : {}
    const active = data.active && typeof data.active === 'object' ? data.active : {}
    if (Array.isArray(byMode.chat)) {
      return {
        byMode: {
          chat: byMode.chat,
          drawing: Array.isArray(byMode.drawing) ? byMode.drawing : [],
        },
        active: {
          chat: active.chat || '',
          drawing: active.drawing || '',
        },
      }
    }
    const legacy = [
      ...(Array.isArray(byMode.writing) ? byMode.writing : []),
      ...(Array.isArray(byMode.help) ? byMode.help : []),
    ].sort((a, b) => (b.updateTime || 0) - (a.updateTime || 0))
    return {
      byMode: {
        chat: legacy,
        drawing: Array.isArray(byMode.drawing) ? byMode.drawing : [],
      },
      active: {
        chat: active.writing || active.help || active.chat || '',
        drawing: active.drawing || '',
      },
    }
  }

  function applySessionsPayload(data) {
    if (!data || typeof data !== 'object') return
    const migrated = migrateSessionsPayload(data)
    if (migrated.byMode) {
      localSessionsByMode.value = {
        chat: Array.isArray(migrated.byMode.chat) ? migrated.byMode.chat : [],
        drawing: Array.isArray(migrated.byMode.drawing) ? migrated.byMode.drawing : [],
      }
    }
    if (migrated.active) {
      activeLocalSessionId.value = {
        chat: migrated.active.chat || '',
        drawing: migrated.active.drawing || '',
      }
    }
  }

  function saveLocalSessionsToStorage() {
    try {
      const payload = JSON.stringify({
        byMode: localSessionsByMode.value,
        active: activeLocalSessionId.value,
      })
      localStorage.setItem(localSessionsStorageKey(), payload)
    } catch {
      /* ignore */
    }
  }

  function loadLocalSessionsFromStorage() {
    try {
      let raw = localStorage.getItem(localSessionsStorageKey())
      if (!raw) {
        raw = sessionStorage.getItem(LEGACY_SESSIONS_KEY)
        if (raw) {
          sessionStorage.removeItem(LEGACY_SESSIONS_KEY)
        }
      }
      if (!raw) return
      applySessionsPayload(JSON.parse(raw))
      saveLocalSessionsToStorage()
    } catch {
      /* ignore */
    }
  }

  function persistCurrentMessages() {
    const nav = activeNav.value
    if (nav === 'appearance') return
    const id = activeLocalSessionId.value[nav]
    if (!id) return
    const list = [...(localSessionsByMode.value[nav] || [])]
    const idx = list.findIndex((s) => String(s.id) === String(id))
    if (idx < 0) return
    const firstUser = messages.value.find((m) => m.role === 'user' && m.type !== 'image')
    const title = (firstUser?.content || '').trim().slice(0, 28) || uiLabels.untitledSession
    const prevLen = (list[idx].messages || []).length
    const nextLen = messages.value.length
    list[idx] = {
      ...list[idx],
      messages: messages.value.map((m) => ({ ...m })),
      title,
      updateTime: nextLen > prevLen ? Date.now() : (list[idx].updateTime || Date.now()),
    }
    localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: list }
    saveLocalSessionsToStorage()
  }

  function ensureActiveSession(nav) {
    const n = nav || activeNav.value
    if (n === 'appearance') return ''
    let list = [...(localSessionsByMode.value[n] || [])]
    let id = activeLocalSessionId.value[n]
    if (!id || !list.some((s) => s.id === id)) {
      if (list.length) {
        id = list[0].id
      } else {
        id = newLocalSessionId()
        list.unshift({ id, title: uiLabels.untitledSession, messages: [], updateTime: Date.now() })
        localSessionsByMode.value = { ...localSessionsByMode.value, [n]: list }
        saveLocalSessionsToStorage()
      }
      activeLocalSessionId.value = { ...activeLocalSessionId.value, [n]: id }
    }
    sessionId.value = id
    return id
  }

  function cacheSessionMessages(nav, id, msgs) {
    const list = [...(localSessionsByMode.value[nav] || [])]
    const idx = list.findIndex((s) => String(s.id) === String(id))
    if (idx < 0) return
    list[idx] = {
      ...list[idx],
      messages: msgs.map((m) => ({ ...m })),
    }
    localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: list }
    saveLocalSessionsToStorage()
  }

  async function selectLocalSession(id, persistBeforeSelect = true) {
    if (persistBeforeSelect) {
      persistCurrentMessages()
    }
    const nav = activeNav.value
    const sess = (localSessionsByMode.value[nav] || []).find((s) => String(s.id) === String(id))
    if (!sess) return
    activeLocalSessionId.value = { ...activeLocalSessionId.value, [nav]: sess.id }
    sessionId.value = sess.id
    if (userStore.isLoggedIn && /^\d+$/.test(String(id))) {
      try {
        const res = await getCompanionMessages(id)
        if (res.code === 0 && Array.isArray(res.data)) {
          messages.value = mapVoToMessages(res.data)
          cacheSessionMessages(nav, id, messages.value)
          scrollFsToBottom()
          return
        }
      } catch {
        /* fallback local */
      }
    }
    messages.value = (sess.messages || []).map((m) => ({ ...m }))
    scrollFsToBottom()
  }

  async function deleteSession(session) {
    const id = String(session?.id || '')
    const nav = activeNav.value
    if (!id || nav === 'appearance' || deletingSessionId.value) return
    try {
      await ElMessageBox.confirm(
        '删除后会话记录无法恢复，确认继续？',
        '删除会话',
        {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          confirmButtonClass: 'mascot-session-delete-confirm',
        },
      )
    } catch {
      return
    }

    deletingSessionId.value = id
    try {
      if (userStore.isLoggedIn && /^\d+$/.test(id)) {
        const res = await deleteCompanionSession(id)
        if (res.code !== 0) {
          throw new Error(res.message || '删除失败')
        }
      }
      const currentList = [...(localSessionsByMode.value[nav] || [])]
      const removedIndex = currentList.findIndex((item) => String(item.id) === id)
      const nextList = currentList.filter((item) => String(item.id) !== id)
      const deletingActive = String(activeLocalSessionId.value[nav]) === id
      localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: nextList }

      if (deletingActive) {
        const nextSession = nextList[Math.min(Math.max(removedIndex, 0), nextList.length - 1)]
        if (nextSession) {
          activeLocalSessionId.value = { ...activeLocalSessionId.value, [nav]: nextSession.id }
          sessionId.value = nextSession.id
          await selectLocalSession(nextSession.id, false)
        } else {
          const newId = newLocalSessionId()
          const newSession = {
            id: newId,
            title: uiLabels.untitledSession,
            messages: [],
            updateTime: Date.now(),
          }
          localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: [newSession] }
          activeLocalSessionId.value = { ...activeLocalSessionId.value, [nav]: newId }
          sessionId.value = newId
          messages.value = []
          draft.value = ''
        }
      }
      saveLocalSessionsToStorage()
      ElMessage.success('会话已删除')
    } catch (error) {
      ElMessage.error(error?.message || '删除失败')
    } finally {
      deletingSessionId.value = ''
    }
  }
  
  function mapVoToMessages(rows) {
    return (rows || []).map((m) => {
      const searchImageUrl = m.searchImageUrl || ''
      return {
        role: m.role === 'assistant' ? 'assistant' : 'user',
        content: m.content || '',
        type: m.type === 'image' ? 'image' : 'text',
        url: m.url || '',
        searchImageUrl: isSafeMascotImageUrl(searchImageUrl) ? searchImageUrl : '',
        stripInlineImages: isSafeMascotImageUrl(searchImageUrl),
        at: m.at ? new Date(m.at).getTime() : Date.now(),
      }
    })
  }

  function isLatestRegeneratableAssistant(index) {
    if (loading.value) return false
    const lastIdx = messages.value.length - 1
    if (index !== lastIdx) return false
    const m = messages.value[index]
    return m?.role === 'assistant' && m?.type !== 'image' && !m?.streaming
  }
  
  async function syncServerSessions(nav) {
    if (!userStore.isLoggedIn || nav === 'appearance' || nav === 'drawing') return
    try {
      const res = await getCompanionSessions('chat')
      if (res.code !== 0 || !Array.isArray(res.data) || !res.data.length) return
      const local = [...(localSessionsByMode.value.chat || [])]
      const byId = new Map(local.map((s) => [String(s.id), s]))
      const merged = [...local]
      for (const s of res.data) {
        const id = String(s.id)
        const title = (s.title || '').trim() || uiLabels.untitledSession
        const updateTime = s.updateTime ? new Date(s.updateTime).getTime() : Date.now()
        if (byId.has(id)) {
          const old = byId.get(id)
          byId.set(id, { ...old, title, updateTime })
          const idx = merged.findIndex((x) => String(x.id) === id)
          if (idx >= 0) merged[idx] = byId.get(id)
        } else {
          const row = { id, title, messages: [], updateTime }
          byId.set(id, row)
          merged.push(row)
        }
      }
      localSessionsByMode.value = { ...localSessionsByMode.value, chat: merged }
      saveLocalSessionsToStorage()
    } catch {
      /* ignore */
    }
  }

  async function loadMessagesForNav(nav) {
    if (nav === 'appearance') {
      messages.value = []
      return
    }
    ensureActiveSession(nav)
    const id = activeLocalSessionId.value[nav]
    if (userStore.isLoggedIn && /^\d+$/.test(String(id))) {
      try {
        const res = await getCompanionMessages(id)
        if (res.code === 0 && Array.isArray(res.data)) {
          messages.value = mapVoToMessages(res.data)
          cacheSessionMessages(nav, id, messages.value)
          scrollFsToBottom()
          return
        }
      } catch {
        /* fallback local */
      }
    }
    const sess = (localSessionsByMode.value[nav] || []).find((s) => String(s.id) === String(id))
    messages.value = sess ? (sess.messages || []).map((m) => ({ ...m })) : []
    scrollFsToBottom()
  }

  function applyServerSessionId(meta) {
    if (!userStore.isLoggedIn || !meta?.sessionId) return
    const sid = String(meta.sessionId)
    if (!/^\d+$/.test(sid)) return
    const nav = activeNav.value
    if (nav === 'appearance') return
    sessionId.value = sid
    activeLocalSessionId.value = { ...activeLocalSessionId.value, [nav]: sid }
    const list = [...(localSessionsByMode.value[nav] || [])]
    const idx = list.findIndex((s) => String(s.id) === sid)
    if (idx < 0) {
      const firstUser = messages.value.find((m) => m.role === 'user' && m.type !== 'image')
      list.push({
        id: sid,
        title: (firstUser?.content || '').trim().slice(0, 28) || uiLabels.untitledSession,
        messages: [],
        updateTime: Date.now(),
      })
    }
    localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: list }
    saveLocalSessionsToStorage()
  }

  function dismissRelatedSearchOffer(message) {
    if (!message?.relatedSearchOffer) return
    message.relatedSearchOffer = null
    persistCurrentMessages()
  }

  async function acceptRelatedSearchOffer(message) {
    const offer = message?.relatedSearchOffer
    if (!offer || offer.loading || !userStore.isLoggedIn) return
    const numericSessionId = Number(sessionId.value)
    if (!Number.isInteger(numericSessionId) || numericSessionId <= 0) {
      ElMessage.warning('会话尚未准备好，请稍后再试')
      return
    }
    offer.loading = true
    try {
      const res = await getMascotRelatedRecommendations({
        sessionId: numericSessionId,
        query: offer.query,
      })
      if (res.code !== 0) {
        ElMessage.error(res.message || '相关帖子检索失败')
        return
      }
      message.relatedSearchOffer = null
      const items = Array.isArray(res.data?.items) ? res.data.items : []
      if (items.length) {
        messages.value.push({
          role: 'assistant',
          type: 'related-result',
          content: `检索到 ${items.length} 条相关帖子`,
          relatedItems: items,
          recommendationId: res.data?.id,
          at: Date.now(),
        })
      } else {
        messages.value.push({
          role: 'assistant',
          type: 'text',
          content: '部落里暂时还没人发过这个话题，你想当第一个吗？',
          at: Date.now(),
        })
      }
      persistCurrentMessages()
      scrollFsToBottom()
    } catch {
      ElMessage.error('相关帖子检索失败')
    } finally {
      if (message.relatedSearchOffer) {
        message.relatedSearchOffer.loading = false
      }
    }
  }

  function openRelatedRecommendation(items) {
    relatedDialogItems.value = Array.isArray(items) ? items : []
    relatedDialogVisible.value = true
  }

  function openRelatedArticle(articleId) {
    if (!articleId) return
    relatedDialogVisible.value = false
    router.push({ name: 'articleDetail', params: { id: articleId } })
  }
  
  function formatSessionTime(t) {
    if (!t) return ''
    const d = new Date(t)
    if (Number.isNaN(d.getTime())) return ''
    return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  
  function formatMsgTime(ts) {
    if (!ts) return ''
    const d = new Date(ts)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  function notifyAiBilling(payload) {
    if (!payload) return
    if (payload.billingMode === 'points' && Number(payload.pointsCost) > 0) {
      ElMessage.success(`已扣 ${payload.pointsCost} 萌币`)
      pointsWallet.refresh?.()
    }
  }

  async function refreshQuotaHint() {
    if (!userStore.isLoggedIn || !isVip.value || activeNav.value === 'drawing') {
      quotaHint.value = { percent: 0, canUsePointsPay: false, quotaLabel: '' }
      return
    }
    try {
      const res = await getMascotQuotaHint(selectedLlm.value)
      quotaHint.value = res?.data || { percent: 0, canUsePointsPay: false, quotaLabel: '' }
    } catch {
      quotaHint.value = { percent: 0, canUsePointsPay: false, quotaLabel: '' }
    }
  }

  async function togglePointsPay() {
    if (usePointsBilling.value) {
      usePointsBilling.value = false
      return
    }
    try {
      await ElMessageBox.confirm(
        '当前模型会员额度即将用尽。开启后将按实际 token 消耗萌币积分，费用可能较高，请谨慎开启。确认继续？',
        '使用萌币积分',
        { type: 'warning', confirmButtonText: '确认开启', cancelButtonText: '取消' },
      )
      usePointsBilling.value = true
      refreshEstimate()
    } catch {
      /* cancelled */
    }
  }
  
  async function refreshEstimate() {
    if (!userStore.isLoggedIn || activeNav.value === 'appearance') {
      estimatePoints.value = null
      return
    }
    estimateLoading.value = true
    try {
      const skill = activeNav.value === 'drawing' ? 'drawing' : 'chat'
      const res = await aiPriceEstimate({
        skill,
        route: selectedLlm.value,
        quality: imageQuality.value,
      })
      estimatePoints.value = res?.data?.points ?? null
    }
    catch {
      estimatePoints.value = null
    }
    finally {
      estimateLoading.value = false
    }
  }
  
  function isCurrentSessionEmpty() {
    return messages.value.length === 0
  }

  function startNewSession() {
    const nav = activeNav.value
    if (nav === 'appearance') return
    if (isCurrentSessionEmpty()) {
      ElMessage.info(uiLabels.alreadyNewSession)
      return
    }
    persistCurrentMessages()
    const id = newLocalSessionId()
    const sess = { id, title: uiLabels.untitledSession, messages: [], updateTime: Date.now() }
    const list = [sess, ...(localSessionsByMode.value[nav] || [])]
    localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: list }
    activeLocalSessionId.value = { ...activeLocalSessionId.value, [nav]: id }
    sessionId.value = id
    messages.value = []
    draft.value = ''
    saveLocalSessionsToStorage()
  }
  
  const companionAvatarSrc = computed(() => {
    const code = (activeCode.value || '').toLowerCase()
    if (code === 'xiaomai')
      return companionXiaomai
    if (code === 'snow_miku' || code.includes('miku'))
      return companionMiku
    return companionMiku
  })
  
  const ringVipTier = computed(() => {
    if (!userStore.isLoggedIn || !isVip.value)
      return 0
    const t = Number(userStore.vipTier) || 0
    if (Number(userStore.isAdmin) === 1)
      return Math.max(1, t || 1)
    return t > 0 ? t : 1
  })
  
  function anchorStageToViewportBottomRight() {
    dragOffset.value = { x: 0, y: 0 }
  }

  function clampDragOffset() {
    if (typeof window === 'undefined') return
    const wrapW = stageSize.value.w
    const wrapH = stageSize.value.h
    const margin = 12
    const minX = -(window.innerWidth - wrapW - margin * 2)
    const minY = -(window.innerHeight - wrapH - margin * 2)
    dragOffset.value = {
      x: Math.min(0, Math.max(minX, dragOffset.value.x)),
      y: Math.min(0, Math.max(minY, dragOffset.value.y)),
    }
  }

  function loadSavedOffset() {
    try {
      const anchored = sessionStorage.getItem(ANCHOR_KEY)
      if (!anchored) {
        anchorStageToViewportBottomRight()
        sessionStorage.setItem(ANCHOR_KEY, '1')
      } else {
        const raw = sessionStorage.getItem(OFFSET_KEY)
        if (raw) {
          const o = JSON.parse(raw)
          if (typeof o.x === 'number' && typeof o.y === 'number') {
            dragOffset.value = { x: o.x, y: o.y }
          }
        }
      }
      const rawS = sessionStorage.getItem(SCALE_KEY)
      if (rawS) {
        const s = parseFloat(rawS)
        if (!Number.isNaN(s) && s >= 0.35 && s <= 1.45) {
          stageScale.value = s
        }
      }
      const chat = localStorage.getItem(LLM_CHAT_KEY)
      const w = localStorage.getItem(LLM_WRITING_KEY)
      const h = localStorage.getItem(LLM_HELP_KEY)
      const legacy = localStorage.getItem('mascot_llm_provider_v1')
      const legacyMap = { qwen: 'qwen-flash', openai: 'qwen-flash' }
      const leg = legacy && legacyMap[legacy] ? legacyMap[legacy] : ''
      const pick = chat || w || h || leg
      if (pick && ALL_LLM_OPTIONS.some(x => x.id === pick))
        selectedLlmChat.value = pick
      else
        selectedLlmChat.value = 'qwen-flash'
      const q = localStorage.getItem(IMAGE_QUALITY_KEY)
      if (q === 'normal' || q === 'premium')
        imageQuality.value = q
    }
    catch {
      /* ignore */
    }
    clampDragOffset()
  }
  
  function saveOffset() {
    try {
      sessionStorage.setItem(OFFSET_KEY, JSON.stringify(dragOffset.value))
    } catch {
      /* ignore */
    }
  }
  
  function saveScale() {
    try {
      sessionStorage.setItem(SCALE_KEY, String(stageScale.value))
    } catch {
      /* ignore */
    }
  }
  
  function saveLlmPrefs() {
    try {
      localStorage.setItem(LLM_CHAT_KEY, selectedLlmChat.value)
      localStorage.setItem(IMAGE_QUALITY_KEY, imageQuality.value)
    }
    catch {
      /* ignore */
    }
  }
  
  watch([selectedLlmChat, imageQuality], () => saveLlmPrefs())
  
  watch(llmOptions, (opts) => {
    const cur = selectedLlm.value
    if (!opts.some(x => x.id === cur)) {
      selectedLlmChat.value = 'qwen-flash'
      saveLlmPrefs()
    }
  }, { immediate: true })

  watch(imageModelOptions, (opts) => {
    if (!opts.some((x) => x.id === imageQuality.value)) {
      imageQuality.value = opts[0]?.id || 'normal'
      saveLlmPrefs()
    }
  }, { immediate: true })
  
  watch([activeNav, selectedLlm, imageQuality, () => userStore.isLoggedIn], () => {
    if (assistantOpen.value) {
      refreshEstimate()
      refreshQuotaHint()
    }
    if (!showPointsPayButton.value) {
      usePointsBilling.value = false
    }
  }, { immediate: false })

  watch(stageScale, () => {
    applyStageScaleToLib()
    clampDragOffset()
  })
  
  const stageSize = computed(() => ({
    w: Math.round(STAGE_BASE_W * stageScale.value),
    h: Math.round(STAGE_BASE_H * stageScale.value),
  }))

  const stageWrapStyle = computed(() => ({
    width: `${stageSize.value.w}px`,
    height: `${stageSize.value.h}px`,
  }))

  const stageHostStyle = computed(() => ({
    width: `${stageSize.value.w}px`,
    height: `${stageSize.value.h}px`,
  }))
  
  const rootStyle = computed(() => ({
    transform: `translate(${dragOffset.value.x}px, ${dragOffset.value.y}px)`,
  }))

  watch(assistantOpen, (open) => {
    if (open) {
      stopMascotIdleTips()
      clearStageCloudTip()
    } else if (!stageUseFallback.value) {
      startMascotIdleTips()
    }
  })
  
  function applyStageScaleToLib() {
    const { w, h } = stageSize.value
    try {
      oml2d.value?.setStageStyle?.({ width: w, height: h })
    } catch {
      /* ignore */
    }
    const idx = catalog.value.findIndex((m) => m.code === activeCode.value)
    if (idx >= 0) applyModelMetricsForIndex(idx)
  }
  
  function onScaleSliderChange() {
    saveScale()
    applyStageScaleToLib()
    clampDragOffset()
    saveOffset()
  }
  
  function onStageLeave() {
    if (!scalePopoverOpen.value)
      stageHovered.value = false
  }
  
  function onStagePointerDown(e) {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    e.preventDefault()
    stageGesture = true
    stageMoved = false
    stageStart = {
      px: e.clientX,
      py: e.clientY,
      ox: dragOffset.value.x,
      oy: dragOffset.value.y,
    }
    window.addEventListener('pointermove', onStagePointerMove)
    window.addEventListener('pointerup', onStagePointerUp)
    window.addEventListener('pointercancel', onStagePointerUp)
    try {
      e.currentTarget?.setPointerCapture?.(e.pointerId)
    } catch {
      /* ignore */
    }
  }
  
  function onStagePointerMove(e) {
    if (!stageGesture) return
    const dx = e.clientX - stageStart.px
    const dy = e.clientY - stageStart.py
    if (Math.hypot(dx, dy) > 7)
      stageMoved = true
    if (stageMoved) {
      dragOffset.value = {
        x: stageStart.ox + dx,
        y: stageStart.oy + dy,
      }
    }
  }
  
  function onStagePointerUp() {
    if (!stageGesture) return
    stageGesture = false
    window.removeEventListener('pointermove', onStagePointerMove)
    window.removeEventListener('pointerup', onStagePointerUp)
    window.removeEventListener('pointercancel', onStagePointerUp)
    if (stageMoved) {
      clampDragOffset()
      saveOffset()
    }
    else {
      if (!userStore.isLoggedIn) {
        void ensureLoggedIn('与看板娘互动需要登录')
        return
      }
      assistantOpen.value = !assistantOpen.value
    }
    stageMoved = false
  }
  
  function live2dAssetUrl(rel) {
    const raw = (import.meta.env.BASE_URL || '/').replace(/\/+$/, '')
    const prefix = raw ? `${raw}/live2d-assets` : '/live2d-assets'
    return `${prefix}/${rel.split('/').filter(Boolean).map((s) => encodeURIComponent(s)).join('/')}`
  }
  
  function modelStageMetrics(m) {
    const s = stageScale.value
    const needsBoost = m.code === 'xiaomai'
    const baseScale = Number(m.modelScale) || 0.1
    let scale = needsBoost
      ? Math.min(0.35, Math.max(baseScale * 1.45, baseScale + 0.04))
      : baseScale
    scale *= s
    const posY = (needsBoost ? Math.max(0, (m.posY ?? 72) - 10) : (m.posY ?? 72)) * s
    let posX = (m.posX ?? 0) * s
    if (m.code === 'snow_miku')
      posX -= 90 * s
    return {
      scale,
      position: [posX, posY],
    }
  }

  function buildModelsPayload() {
    return catalog.value.map((m) => {
      const { scale, position } = modelStageMetrics(m)
      return {
        name: m.code,
        path: live2dAssetUrl(m.modelRelPath),
        scale,
        position,
        stageStyle: { width: stageSize.value.w, height: stageSize.value.h },
      }
    })
  }

  let oml2dLoadChain = Promise.resolve()

  function runOml2dLoad(task) {
    const next = oml2dLoadChain.then(() => task())
    oml2dLoadChain = next.catch(() => {})
    return next
  }

  function seedOml2dModelIndex(idx) {
    try {
      localStorage.setItem('OML2D_MODEL_INDEX', String(Math.max(0, idx)))
      localStorage.setItem('OML2D_MODEL_CLOTHES_INDEX', '0')
    }
    catch {
      /* Edge 跟踪防护等场景可能禁用 localStorage */
    }
  }

  function applyModelMetricsForIndex(idx) {
    const payload = buildModelsPayload()[idx]
    if (!payload || !oml2d.value) return
    try {
      oml2d.value.setModelScale?.(payload.scale)
      oml2d.value.setModelPosition?.(payload.position)
    }
    catch {
      /* ignore */
    }
  }

  function waitOml2dLoad(inst, timeoutMs = 25000) {
    return new Promise((resolve, reject) => {
      if (!inst?.onLoad) {
        resolve()
        return
      }
      let settled = false
      const timer = setTimeout(() => {
        if (settled) return
        settled = true
        reject(new Error('oml2d load timeout'))
      }, timeoutMs)
      inst.onLoad((status) => {
        if (settled) return
        if (status === 'success') {
          settled = true
          clearTimeout(timer)
          resolve()
        }
        else if (status === 'fail') {
          settled = true
          clearTimeout(timer)
          reject(new Error('oml2d load fail'))
        }
      })
    })
  }

  async function switchMascotModelByCode(code) {
    return runOml2dLoad(async () => {
      const idx = catalog.value.findIndex((m) => m.code === code)
      if (idx < 0 || !oml2d.value?.loadModelByIndex) return
      if (stageUseFallback.value) {
        activeCode.value = code
        return
      }
      const cur = Number(oml2d.value.modelIndex)
      if (Number.isFinite(cur) && cur === idx) {
        applyStageScaleToLib()
        applyModelMetricsForIndex(idx)
        return
      }
      await oml2d.value.loadModelByIndex(idx)
      applyStageScaleToLib()
      applyModelMetricsForIndex(idx)
    })
  }
  
  async function fetchCatalog() {
    try {
      const res = await getMascotPublicModels()
      catalog.value = Array.isArray(res?.data) ? res.data : []
    }
    catch {
      catalog.value = []
    }
  }
  
  function resolveInitialCode() {
    const list = catalog.value
    if (!list.length)
      return ''
    const mid = userStore.mascotModelId
    if (mid != null && mid !== '') {
      const hit = list.find((r) => String(r.id) === String(mid))
      if (hit)
        return hit.code
    }
    const g = localStorage.getItem(GUEST_MASCOT_CODE_KEY)
    if (g && list.some((r) => r.code === g))
      return g
    return list[0].code
  }
  
  function navToSkill(nav) {
    const map = {
      chat: 'chat',
      drawing: 'drawing',
      appearance: 'chat',
    }
    activeSkill.value = map[nav] || 'chat'
  }
  
  async function selectNav(nav) {
    if (activeNav.value !== 'appearance' && activeNav.value !== nav) {
      persistCurrentMessages()
    }
    activeNav.value = nav
    if (nav === 'appearance') {
      pendingCode.value = activeCode.value || resolveInitialCode() || (catalog.value[0]?.code ?? '')
      return
    }
    navToSkill(nav)
    draft.value = ''
    if (userStore.isLoggedIn) {
      await syncServerSessions(nav)
    }
    await loadMessagesForNav(nav)
    refreshEstimate()
    refreshQuotaHint()
  }
  
  async function onPreviewPick(code) {
    pendingCode.value = code
    if (stageUseFallback.value) {
      activeCode.value = code
      return
    }
    try {
      await switchMascotModelByCode(code)
      activeCode.value = code
    } catch {
      /* ignore preview failure */
    }
  }
  
  async function applyAppearance() {
    const m = catalog.value.find((x) => x.code === pendingCode.value)
    if (!m) {
      ElMessage.warning('请选择形象')
      return
    }
    activeCode.value = m.code
    if (userStore.isLoggedIn) {
      try {
        await setMascotModel(m.id)
        await userStore.fetchUserInfo()
      }
      catch {
        ElMessage.error('保存失败')
        return
      }
    }
    else {
      localStorage.setItem(GUEST_MASCOT_CODE_KEY, m.code)
    }
    if (stageUseFallback.value) {
      ElMessage.success('形象已更新')
      return
    }
    try {
      await switchMascotModelByCode(m.code)
      ElMessage.success('形象已更新')
    }
    catch {
      ElMessage.warning('形象已保存，舞台加载失败')
    }
  }

  async function switchMascot(code) {
    if (!code || code === activeCode.value) return
    await onPreviewPick(code)
    await applyAppearance()
  }
  
  function isWebGLAvailable() {
    try {
      const canvas = document.createElement('canvas')
      const attrs = { stencil: true, failIfMajorPerformanceCaveat: false }
      const gl =
        canvas.getContext('webgl2', attrs)
        || canvas.getContext('webgl', attrs)
        || canvas.getContext('experimental-webgl', attrs)
      if (!gl) return false
      const hasStencil = !!(gl.getContextAttributes()?.stencil)
      const lose = gl.getExtension('WEBGL_lose_context')
      lose?.loseContext()
      return hasStencil
    }
    catch {
      return false
    }
  }

  function clearOml2dStageHost() {
    oml2d.value = null
    if (!stageHost.value) return
    try {
      stageHost.value.innerHTML = ''
      document
        .querySelectorAll('#oml2d-stage, #oml2d-canvas, #oml2d-tips, #oml2d-statusBar')
        .forEach((node) => node.remove())
    }
    catch {
      /* ignore */
    }
  }

  function enableStageFallback(reason, { notify = true } = {}) {
    stageUseFallback.value = true
    clearOml2dStageHost()
    console.warn('mascot stage fallback', reason)
    if (notify) {
      ElMessage.warning('当前环境不支持 Live2D（WebGL），已使用静态形象显示')
    }
  }

  // 主舞台 #oml2d-stage 挂在页面右下角

  async function initOml2dStage() {
    await fetchCatalog()
    await nextTick()
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    if (!stageHost.value) return
    if (!catalog.value.length) {
      ElMessage.warning('暂无上架模型，请管理员在后台配置并上架')
      return
    }
    const code = resolveInitialCode() || catalog.value[0].code
    let idx = catalog.value.findIndex((m) => m.code === code)
    if (idx < 0) idx = 0
    activeCode.value = catalog.value[idx].code
    pendingCode.value = activeCode.value
    seedOml2dModelIndex(idx)

    if (!isWebGLAvailable()) {
      enableStageFallback('webgl unavailable')
      return
    }

    const models = buildModelsPayload()
    try {
      stageUseFallback.value = false
      clearOml2dStageHost()
      const { loadOml2d } = await import('oh-my-live2d')
      oml2d.value = loadOml2d({
        parentElement: stageHost.value,
        sayHello: false,
        menus: { disable: true },
        statusBar: { disable: true },
        models,
        initialStatus: 'active',
      })
      await waitOml2dLoad(oml2d.value)
      applyStageScaleToLib()
      applyModelMetricsForIndex(idx)
      startMascotIdleTips()
    }
    catch (e) {
      enableStageFallback(e)
    }
  }
  
  function ensureSessionId() {
    if (!sessionId.value) {
      const k = 'mascot_session_id'
      let v = sessionStorage.getItem(k)
      if (!v) {
        v = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : String(Date.now())
        sessionStorage.setItem(k, v)
      }
      sessionId.value = v
    }
  }
  
  function scrollFsToBottom() {
    nextTick(() => {
      try {
        scrollbarFs.value?.setScrollTop?.(1e9)
      } catch {
        /* ignore */
      }
    })
  }
  
  watch(messages, () => scrollFsToBottom(), { deep: true })
  
  async function onAssistantOpened() {
    stopMascotIdleTips()
    clearStageCloudTip()
    if (userStore.isLoggedIn) {
      pointsWallet.refresh()
    }
    loadLocalSessionsFromStorage()
    if (activeNav.value !== 'appearance') {
      await syncServerSessions(activeNav.value)
      await loadMessagesForNav(activeNav.value)
    }
    scrollFsToBottom()
    refreshEstimate()
    refreshQuotaHint()
  }
  
  function onSkillForSend() {
    navToSkill(activeNav.value)
  }
  
  function buildChatHistory() {
    const rows = messages.value
      .filter((m) => m.type !== 'image')
      .map((m) => ({ role: m.role, content: m.content }))
    if (rows.length && rows[rows.length - 1].role === 'user') {
      return rows.slice(0, -1)
    }
    return rows
  }

  async function regenerateAssistant(index) {
    const i = Number(index)
    if (!Number.isFinite(i) || i < 0 || messages.value[i]?.role !== 'assistant') return
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      return
    }
    let userIdx = i - 1
    while (userIdx >= 0 && messages.value[userIdx].role !== 'user') userIdx -= 1
    if (userIdx < 0) return
    const userText = messages.value[userIdx].content
    messages.value = messages.value.slice(0, i)
    persistCurrentMessages()
    await sendInternal(userText, { skipPushUser: true })
  }

  async function sendInternal(text, { skipPushUser = false } = {}) {
    onSkillForSend()
    ensureActiveSession(activeNav.value)
    const sid = sessionId.value
    loading.value = true
    const now = Date.now()
    if (!skipPushUser) {
      messages.value.push({ role: 'user', content: text, type: 'text', at: now })
      persistCurrentMessages()
    }

    const history = buildChatHistory()

    const skill = 'chat'
    let streamHadError = false
    let assistantIdx = -1

    try {
      if (chatStreamAbort) {
        chatStreamAbort()
        chatStreamAbort = null
      }
      clearThinkingRotation()
      assistantIdx = messages.value.length
      const llmForThinking = selectedLlm.value
      messages.value.push({
        role: 'assistant',
        content: '',
        type: 'text',
        at: Date.now(),
        streaming: true,
        thinkingText: pickThinkingPhrase(llmForThinking),
        searchImageUrl: '',
      })
      stopThinkingRotation = startThinkingRotation(llmForThinking, (text) => {
        const row = messages.value[assistantIdx]
        if (row?.streaming && !(row.content || '').length) {
          row.thinkingText = text
        }
      })
      await new Promise((resolve, reject) => {
        chatStreamAbort = streamMascotChat(
          {
            message: text,
            sessionId: sid,
            mascotModelCode: activeCode.value,
            llmProvider: selectedLlm.value,
            skill,
            imageQuality: imageQuality.value,
            history,
            ephemeral: !userStore.isLoggedIn,
            clientDatetime: new Date().toISOString(),
            usePointsBilling: usePointsBilling.value,
          },
          {
            onChunk(piece) {
              const row = messages.value[assistantIdx]
              if (!row) return
              if (row.thinkingText) row.thinkingText = ''
              clearThinkingRotation()
              row.content = (row.content || '') + piece
              scrollFsToBottom()
            },
            onMeta(meta) {
              applyServerSessionId(meta)
              const row = messages.value[assistantIdx]
              if (meta?.status === 'preparing' && row?.streaming && !(row.content || '').length) {
                row.thinkingText = '正在整理资料…'
              }
              if (meta?.imageGenerating) {
                imageGenerating.value = true
                if (row?.streaming && !(row.content || '').length) {
                  row.thinkingText = '正在绘制画面…'
                }
              }
              if (meta?.imageUrl && isSafeMascotImageUrl(meta.imageUrl)) {
                if (assistantIdx >= 0) {
                  messages.value.splice(assistantIdx, 1)
                }
                messages.value.push({
                  role: 'assistant',
                  type: 'image',
                  url: meta.imageUrl,
                  at: Date.now(),
                  usageStats: usageStatsFromApi(meta),
                })
                assistantIdx = -1
                persistCurrentMessages()
                scrollFsToBottom()
              }
              if (meta?.relatedSearchOffer && meta?.relatedSearchQuery && row) {
                row.relatedSearchOffer = {
                  query: String(meta.relatedSearchQuery).slice(0, 500),
                  loading: false,
                }
              }
              if (meta?.searchImageUrl && row && isSafeMascotImageUrl(meta.searchImageUrl)) {
                row.searchImageUrl = meta.searchImageUrl
                row.stripInlineImages = true
              }
              const stats = usageStatsFromApi(meta)
              if (stats && row) row.usageStats = stats
              notifyAiBilling(meta)
            },
            onDone() {
              clearThinkingRotation()
              const row = messages.value[assistantIdx]
              if (row) {
                row.streaming = false
                row.thinkingText = ''
                if (!streamHadError && !row.content?.trim()) row.content = '…'
              }
              chatStreamAbort = null
              refreshEstimate()
              persistCurrentMessages()
              if (!streamHadError) {
                showStageCloudTip('回复好了，点我查看～', 2800)
                resolve()
              }
            },
            onError(msg) {
              streamHadError = true
              clearThinkingRotation()
              chatStreamAbort = null
              const row = messages.value[assistantIdx]
              if (row) {
                row.streaming = false
                row.thinkingText = ''
                if (!row.content?.trim()) {
                  messages.value.splice(assistantIdx, 1)
                } else {
                  row.streamInterrupted = true
                  persistCurrentMessages()
                }
              }
              if (msg) ElMessage.error(String(msg))
              reject(new Error(msg || 'stream failed'))
            },
          },
        )
      })
    } catch (e) {
      if (!streamHadError) {
        if (!skipPushUser) messages.value.pop()
        else if (messages.value.length && messages.value[messages.value.length - 1]?.role === 'assistant') {
          messages.value.pop()
        }
      } else {
        persistCurrentMessages()
      }
      throw e
    } finally {
      loading.value = false
      imageGenerating.value = false
      if (assistantIdx >= 0) {
        const row = messages.value[assistantIdx]
        if (row?.streaming) {
          row.streaming = false
          row.thinkingText = ''
          clearThinkingRotation()
        }
      }
    }
  }

  async function send() {
    const text = draft.value.trim()
    if (!text) return
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      return
    }
    if (usePointsBilling.value && estimatePoints.value != null
        && pointsWallet.balance < estimatePoints.value) {
      ElMessage.warning('萌币余额不足，请先充值或赚取积分')
      return
    }
    draft.value = ''
    try {
      await sendInternal(text)
    } catch {
      draft.value = text
    }
  }
  
  watch(
    () => userStore.mascotModelId,
    async (mid) => {
      if (!oml2d.value?.loadModelByIndex || !catalog.value.length || mid == null || mid === '')
        return
      const hit = catalog.value.find((r) => String(r.id) === String(mid))
      if (hit && hit.code !== activeCode.value) {
        activeCode.value = hit.code
        pendingCode.value = hit.code
        try {
          await switchMascotModelByCode(hit.code)
        }
        catch {
          /* ignore */
        }
      }
    },
  )
  
  watch(
    () => userStore.id,
    async (id, prev) => {
      if (id && id !== prev) {
        loadLocalSessionsFromStorage()
        if (assistantOpen.value && activeNav.value !== 'appearance') {
          await syncServerSessions(activeNav.value)
          await loadMessagesForNav(activeNav.value)
        }
      }
    },
  )

  onMounted(async () => {
    loadLocalSessionsFromStorage()
    loadSavedOffset()
    await nextTick()
    await initOml2dStage()
    clampDragOffset()
    saveOffset()
  })
  
  onBeforeUnmount(() => {
    stopMascotIdleTips()
    clearStageCloudTip()
    clearThinkingRotation()
    if (chatStreamAbort) {
      chatStreamAbort()
      chatStreamAbort = null
    }
    onStagePointerUp()
    clearOml2dStageHost()
  })

  function hideMascotSearchImage(msg) {
    if (msg) msg.searchImageUrl = ''
  }

  return {
    DEFAULT_AVATAR,
    ALL_LLM_OPTIONS,
    IMAGE_MODEL_OPTIONS,
    activeImageOption,
    imageModelOptions,
    FLASH_LLM,
    GUEST_MASCOT_CODE_KEY,
    IMAGE_QUALITY_KEY,
    LLM_CHAT_KEY,
    LLM_HELP_KEY,
    LLM_WRITING_KEY,
    OFFSET_KEY,
    SCALE_KEY,
    STAGE_BASE_H,
    STAGE_BASE_W,
    activeCode,
    activeNav,
    activeSkill,
    acceptRelatedSearchOffer,
    applyAppearance,
    applyStageScaleToLib,
    assistantOpen,
    buildModelsPayload,
    catalog,
    startNewSession,
    switchMascot,
    companionAvatarSrc,
    currentLlmStorageKey,
    draft,
    deleteSession,
    deletingSessionId,
    dismissRelatedSearchOffer,
    dragOffset,
    ensureSessionId,
    estimateHintText,
    estimateLoading,
    estimatePoints,
    showPointsPayButton,
    usePointsBilling,
    togglePointsPay,
    fetchCatalog,
    formatAiUsageLine,
    formatMsgTime,
    formatSessionTime,
    hideMascotSearchImage,
    isLatestRegeneratableAssistant,
    imageQuality,
    imageGenerating,
    initOml2dStage,
    inputPlaceholder,
    isVip,
    live2dAssetUrl,
    llmOptions,
    llmStorageKey,
    loadMessagesForNav,
    loadSavedOffset,
    loading,
    mapVoToMessages,
    messages,
    navToSkill,
    oml2d,
    onAssistantOpened,
    onPreviewPick,
    openRelatedArticle,
    openRelatedRecommendation,
    onScaleSliderChange,
    onSkillForSend,
    onStageLeave,
    onStagePointerDown,
    onStagePointerMove,
    onStagePointerUp,
    regenerateAssistant,
    renderMascotMarkdown,
    pendingCode,
    pointsWallet,
    refreshEstimate,
    resolveInitialCode,
    ringVipTier,
    rootStyle,
    relatedDialogItems,
    relatedDialogVisible,
    saveLlmPrefs,
    saveOffset,
    saveScale,
    scalePopoverOpen,
    scrollFsToBottom,
    scrollbarFs,
    selectLocalSession,
    selectNav,
    selectedLlm,
    selectedLlmChat,
    send,
    sessionId,
    sessionListForNav,
    stageHost,
    stageHovered,
    stageHostStyle,
    stageScale,
    stageTipText,
    stageUseFallback,
    stageWrapStyle,
    uiLabels,
    userStore,
  }
}
