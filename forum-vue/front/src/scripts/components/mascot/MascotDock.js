import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import {
  getMascotQuotaHint,
  getMascotRelatedRecommendations,
  listMascotRelatedRecommendations,
  streamMascotChat,
  getCompanionSessions,
  getCompanionMessages,
  deleteCompanionSession,
  renameCompanionSession,
  getCompanionContextWindow,
  compressCompanionContext,
  getMascotMemory,
  editMascotMemory,
} from '@/api/mascot'
import { getVipQuota } from '@/api/vip'
import { DEFAULT_AVATAR } from '@/utils/constants'
import {
  MASCOT_IMAGE_QUALITY_OPTIONS,
  findImageQualityOption,
} from '@/constants/aiModels'
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

// 看板娘 AI 回复：Markdown → HTML 表格、列表、加粗等
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

function streamStatusText(status) {
  const labels = {
    preparing: '正在准备对话…',
    routing: '正在理解你的问题…',
    supervising: '正在分析请求…',
    planning: '正在想要不要查资料…',
    using_tools: '正在查资料…',
    composing: '正在组织回复…',
    drawing: '正在准备绘图…',
  }
  const key = String(status || '').trim()
  if (!key) return ''
  return labels[key] || '正在处理…'
}

function streamSpriteState(status) {
  const normalized = String(status || '').trim()
  if (['supervising', 'planning', 'using_tools'].includes(normalized)) return 'review'
  if (normalized) return 'running'
  return ''
}

const MASCOT_CODE = 'xiaomeng'
const SPRITE_WIDTH = 96
const SPRITE_EDGE_MARGIN = 16
const SPRITE_SPEED_PX_PER_SECOND = 32
const SPRITE_MIN_TRAVEL = 80
const SPRITE_MAX_TRAVEL = 180
const MASCOT_IDLE_TIP_TEXT = '可以点击我，和我来聊聊天哦'
const IDLE_TIP_FIRST_DELAY_MS = 90_000
const IDLE_TIP_MIN_INTERVAL_MS = 180_000
const IDLE_TIP_MAX_INTERVAL_MS = 300_000
const IDLE_TIP_TRIGGER_WEIGHT = 0.12
const SPRITE_IDLE_MIN_MS = 18_000
const SPRITE_IDLE_MAX_MS = 36_000
const SPRITE_PATROL_CHANCE = 0.28

export function useMascotDock() {
  const LLM_CHAT_KEY = 'mascot_llm_chat_v1'
  const LLM_WRITING_KEY = 'mascot_llm_writing_v3'
  const LLM_HELP_KEY = 'mascot_llm_help_v3'
  const IMAGE_QUALITY_KEY = 'mascot_image_quality_v1'
  const relatedDialogVisible = ref(false)
  const relatedDialogItems = ref([])
  const searchGalleryVisible = ref(false)
  const searchGalleryItems = ref([])

  const uiLabels = {
    ariaRoot: '看板娘',
    brandTitle: '小萌',
    statusOnline: '在线',
    statusOffline: '不在线',
    defaultNickname: '用户',
    sessionListTitle: '会话',
    newSession: '新建会话',
    guest: '未登录',
    today: '今天',
    openImageInNewTab: '在新标签打开',
    typing: '正在输入',
    regenerate: '重新生成',
    sessionEmpty: '暂无会话，点击 + 新建',
    untitledSession: '新会话',
    alreadyNewSession: '当前已是新会话，直接输入即可开始对话',
    deleteSession: '删除会话',
    renameSession: '编辑会话名称',
    chatEmptyHint: '你还没有和AI开始聊呢喵~',
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
  
  // 文本模型由服务端按会员档位自动选择；前端不提供切换
  const ALL_LLM_OPTIONS = [{ id: 'qwen-flash' }]
  const IMAGE_MODEL_OPTIONS = MASCOT_IMAGE_QUALITY_OPTIONS
  
  const userStore = useUserStore()
  const router = useRouter()
  const pointsWallet = usePointsWalletStore()
  const scrollbarFs = ref(null)
  
  const assistantOpen = ref(false)
  // chat | drawing
  const activeNav = ref('chat')
  const messages = ref([])
  const draft = ref('')
  const loading = ref(false)
  const imageGenerating = ref(false)
  const deletingSessionId = ref('')
  const renamingSessionId = ref('')
  const renameDraft = ref('')
  const renameInputRef = ref(null)
  const renameSubmitting = ref(false)
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
  const quotaHint = ref({ percent: 0, canUsePointsPay: false, quotaLabel: '' })
  const quotaPanel = ref(null)
  const contextWindow = ref({ usedTokens: 0, maxTokens: 128000, canCompress: false })
  const contextCompressing = ref(false)
  const memoryDialogVisible = ref(false)
  const memorySummary = ref('')
  const memoryFacts = ref([])
  const memoryEditDraft = ref('')
  const memorySaving = ref(false)
  // 摘要/事实/修改指令上限：兼顾弹窗可读性与模型输出稳定
  const MEMORY_SUMMARY_MAX = 240
  const MEMORY_FACTS_MAX = 10
  const MEMORY_FACT_ITEM_MAX = 40
  const MEMORY_EDIT_MAX = 200

  const displayMemorySummary = computed(() =>
    String(memorySummary.value || '').trim().slice(0, MEMORY_SUMMARY_MAX),
  )
  const displayMemoryFacts = computed(() => {
    const rows = Array.isArray(memoryFacts.value) ? memoryFacts.value : []
    const out = []
    for (const item of rows) {
      const text = String(item || '').trim().slice(0, MEMORY_FACT_ITEM_MAX)
      if (!text || out.includes(text)) continue
      out.push(text)
      if (out.length >= MEMORY_FACTS_MAX) break
    }
    return out
  })

  const estimateHintText = computed(() => {
    if (userStore.isLoggedIn) {
      const p = quotaHint.value?.percent ?? 0
      const label = quotaHint.value?.quotaLabel || '通用额度'
      return p > 0 ? `${label} 已用 ${p}%` : '使用方案额度'
    }
    return '登录后使用免费方案额度'
  })
  const quotaRows = computed(() => {
    const panel = quotaPanel.value || {}
    const qwenLimit = Number(panel.qwenBudgetMicros) || 0
    const qwenUsed = Math.min(qwenLimit, Number(panel.qwenUsedMicros) || 0)
    const qwenRemaining = Math.max(0, Number(panel.qwenRemainingMicros ?? (qwenLimit - qwenUsed)) || 0)
    const wanLimit = Number(panel.wanImageLimit) || 0
    const wanUsed = Math.min(wanLimit, Number(panel.wanImageUsed) || 0)
    const wanRemaining = Math.max(0, Number(panel.wanImageRemaining ?? (wanLimit - wanUsed)) || 0)
    const remainingPercent = (remaining, limit) => (
      limit > 0 ? Math.max(0, Math.min(100, Math.round(remaining * 100 / limit))) : 0
    )
    return [
      {
        key: 'qwen', label: '通用', tone: 'qwen',
        text: `${remainingPercent(qwenRemaining, qwenLimit)}%`,
        remainingPercent: remainingPercent(qwenRemaining, qwenLimit),
        exhausted: qwenLimit > 0 && qwenRemaining <= 0,
      },
      {
        key: 'wan', label: '生图', tone: 'wan',
        text: `${remainingPercent(wanRemaining, wanLimit)}%`,
        remainingPercent: remainingPercent(wanRemaining, wanLimit),
        exhausted: wanLimit > 0 && wanRemaining <= 0,
      },
    ]
  })
  // 额度用尽即失败，不再回退扣币；这里只用于面板提示
  const quotaExhausted = computed(() => {
    if (!userStore.isLoggedIn) return false
    const key = activeNav.value === 'drawing' ? 'wan' : 'qwen'
    return quotaRows.value.find((row) => row.key === key)?.exhausted === true
  })
  const spriteHovered = ref(false)
  const spriteReady = ref(false)
  const spriteX = ref(SPRITE_EDGE_MARGIN)
  const patrolDirection = ref('')
  const agentSpriteState = ref('')
  const reactionSpriteState = ref('')
  const pageHidden = ref(false)
  const spriteState = computed(() => {
    // 打开面板保持正常待机(idle)；仅 AI 作答时由 agentSpriteState 进入思考/忙碌态
    if (reactionSpriteState.value) return reactionSpriteState.value
    if (agentSpriteState.value) return agentSpriteState.value
    if (patrolDirection.value) return patrolDirection.value
    return 'idle'
  })
  const spritePaused = computed(() => spriteHovered.value || pageHidden.value)
  let chatStreamAbort = null
  // 流式期间用户可能切走或新建会话，messages 会被整体换掉。
  // 这个计数每换一次 +1，流式回调据此判断自己这一轮还属不属于当前视图；
  // 不属于就只丢弃界面写入——服务端照常落库，切回来会重新加载到。
  const messagesEpoch = ref(0)
  // 当前这条流是发在哪个视图上的；-1 表示没有在途的流
  const streamEpoch = ref(-1)
  // 「正在思考」那三个点原来只看 loading，而 loading 是全局的：
  // 切走会话后新会话里没有 streaming 的消息，条件反而更成立，
  // 于是三个点画到了不相干的会话里。只有流确实属于当前视图才画。
  const streamInCurrentView = computed(
    () => streamEpoch.value >= 0 && streamEpoch.value === messagesEpoch.value,
  )
  let stopThinkingRotation = null
  let idleTipsTimer = null
  let idleFirstTipTimer = null
  let stageTipTimer = null
  let patrolDelayTimer = null
  let patrolAnimationFrame = 0
  let patrolTargetX = SPRITE_EDGE_MARGIN
  let patrolLastTimestamp = 0
  const stageTipText = ref('')

  function clearStageCloudTip() {
    if (stageTipTimer) {
      clearTimeout(stageTipTimer)
      stageTipTimer = null
    }
    stageTipText.value = ''
  }

  function showStageCloudTip(text, durationMs = 3000) {
    const msg = String(text || '').trim()
    if (!msg) {
      clearStageCloudTip()
      return
    }
    if (assistantOpen.value || loading.value || spriteState.value !== 'idle') return
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
    if (idleFirstTipTimer) {
      clearTimeout(idleFirstTipTimer)
      idleFirstTipTimer = null
    }
    if (idleTipsTimer) {
      clearInterval(idleTipsTimer)
      clearTimeout(idleTipsTimer)
      idleTipsTimer = null
    }
  }

  function startMascotIdleTips() {
    stopMascotIdleTips()
    const randomDelay = () => {
      return IDLE_TIP_MIN_INTERVAL_MS + Math.floor(Math.random() * Math.max(1, IDLE_TIP_MAX_INTERVAL_MS - IDLE_TIP_MIN_INTERVAL_MS + 1))
    }

    const tick = () => {
      if (assistantOpen.value || loading.value || spriteState.value !== 'idle') {
        idleTipsTimer = setTimeout(tick, randomDelay())
        return
      }
      if (Math.random() <= IDLE_TIP_TRIGGER_WEIGHT) {
        showStageCloudTip(MASCOT_IDLE_TIP_TEXT, 4200)
      }
      idleTipsTimer = setTimeout(tick, randomDelay())
    }

    idleFirstTipTimer = setTimeout(tick, IDLE_TIP_FIRST_DELAY_MS)
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
    }
  }

  /**
   * 存盘用的消息快照。
   *
   * <p>直接存 messages.value 会把「还在流的那条占位气泡」一起存下去：
   * 它 content 为空、streaming 为 true、thinkingText 是三个点。存进去之后
   * 这个会话每次打开都挂着一个永远转圈的气泡——切会话时最容易撞上，
   * 因为 startNewSession 正是在清空前调的存盘。
   *
   * <p>没出内容的直接丢掉；出了内容的保留，但把流式标记摘掉。
   */
  function messagesForPersist(rows) {
    return (rows || []).filter((m) => !(m?.streaming && !String(m?.content || '').trim()))
      .map((m) => {
        if (!m?.streaming && !m?.thinkingText) return { ...m }
        return { ...m, streaming: false, thinkingText: '' }
      })
  }

  function persistCurrentMessages() {
    const nav = activeNav.value
    const id = activeLocalSessionId.value[nav]
    if (!id) return
    const list = [...(localSessionsByMode.value[nav] || [])]
    const idx = list.findIndex((s) => String(s.id) === String(id))
    if (idx < 0) return
    const firstUser = messages.value.find((m) => m.role === 'user' && m.type !== 'image')
    const title = (firstUser?.content || '').trim().slice(0, 28) || uiLabels.untitledSession
    const persistable = messagesForPersist(messages.value)
    const prevLen = (list[idx].messages || []).length
    const nextLen = persistable.length
    list[idx] = {
      ...list[idx],
      messages: persistable,
      title,
      updateTime: nextLen > prevLen ? Date.now() : (list[idx].updateTime || Date.now()),
    }
    localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: list }
    saveLocalSessionsToStorage()
  }

  function ensureActiveSession(nav) {
    const n = nav || activeNav.value
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
    messagesEpoch.value += 1
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
          messages.value = mergeLocalMessageExtras(mapVoToMessages(res.data), messagesForPersist(sess.messages))
          await restoreRelatedRecommendations(id)
          restoreAskWizardFromMessages()
          cacheSessionMessages(nav, id, messages.value)
          scrollFsToBottom()
          await refreshContextWindow()
          return
        }
      } catch {
      }
    }
    // localStorage 里可能还留着这个 bug 修好之前存下的占位气泡
    messages.value = messagesForPersist(sess.messages)
    await restoreRelatedRecommendations(id)
    restoreAskWizardFromMessages()
    scrollFsToBottom()
    await refreshContextWindow()
  }

  function startRenameSession(session) {
    const id = String(session?.id || '')
    const nav = activeNav.value
    if (!id || renamingSessionId.value || deletingSessionId.value) return
    renamingSessionId.value = id
    renameDraft.value = String(session.title || '').trim()
    nextTick(() => {
      focusRenameInput()
    })
  }

  function focusRenameInput() {
    const input = Array.isArray(renameInputRef.value)
      ? renameInputRef.value[0]
      : renameInputRef.value
    input?.focus()
    input?.select()
  }

  function cancelRenameSession() {
    if (renameSubmitting.value) return
    renamingSessionId.value = ''
    renameDraft.value = ''
  }

  async function commitRenameSession(session) {
    const id = String(session?.id || '')
    const nav = activeNav.value
    if (!id || String(renamingSessionId.value) !== id || renameSubmitting.value) return
    const title = String(renameDraft.value || '').trim()
    if (!title) {
      ElMessage.warning('会话名称不能为空')
      cancelRenameSession()
      return
    }
    if (title === String(session.title || '').trim()) {
      cancelRenameSession()
      return
    }

    renameSubmitting.value = true
    try {
      if (userStore.isLoggedIn && /^\d+$/.test(id)) {
        const res = await renameCompanionSession(id, { title })
        if (res.code !== 0) {
          throw new Error(res.message || '修改失败')
        }
      }
      const sessions = [...(localSessionsByMode.value[nav] || [])]
      const index = sessions.findIndex((item) => String(item.id) === id)
      if (index >= 0) {
        sessions[index] = { ...sessions[index], title, updateTime: Date.now() }
        localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: sessions }
        saveLocalSessionsToStorage()
      }
      ElMessage.success('会话名称已更新')
      renamingSessionId.value = ''
      renameDraft.value = ''
    } catch (error) {
      ElMessage.error(error?.message || '修改失败')
      nextTick(focusRenameInput)
    } finally {
      renameSubmitting.value = false
    }
  }

  async function deleteSession(session) {
    messagesEpoch.value += 1
    const id = String(session?.id || '')
    const nav = activeNav.value
    if (!id || deletingSessionId.value) return
    try {
      await confirmDialog(
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
      const imageGallery = Array.isArray(m.imageGallery)
        ? m.imageGallery.filter((item) => isSafeMascotImageUrl(item?.url)).slice(0, 5)
        : []
      return {
        messageId: m.id || null,
        role: m.role === 'assistant' ? 'assistant' : (m.type === 'context_summary' ? 'system' : 'user'),
        content: m.content || '',
        type: m.type === 'image' || m.type === 'context_summary' ? m.type : 'text',
        url: m.url || '',
        imageGallery,
        stripInlineImages: imageGallery.length > 0,
        at: m.at ? new Date(m.at).getTime() : Date.now(),
      }
    })
  }

  // 服务端消息无用量字段；用本地缓存按 messageId / url / 位置合并，保证重新进入仍显示
  function mergeLocalMessageExtras(serverRows, localRows) {
    const locals = Array.isArray(localRows) ? localRows : []
    const byId = new Map()
    const byUrl = new Map()
    locals.forEach((item) => {
      if (item?.messageId != null && item.messageId !== '') {
        byId.set(String(item.messageId), item)
      }
      if (item?.type === 'image' && item?.url) {
        byUrl.set(String(item.url), item)
      }
    })
    let localAssistantIdx = 0
    const localAssistants = locals.filter((item) => item?.role === 'assistant')
    return (serverRows || []).map((row) => {
      let local = null
      if (row.messageId != null && row.messageId !== '') {
        local = byId.get(String(row.messageId)) || null
      }
      if (!local && row.type === 'image' && row.url) {
        local = byUrl.get(String(row.url)) || null
      }
      if (!local && row.role === 'assistant') {
        local = localAssistants[localAssistantIdx] || null
        localAssistantIdx += 1
      } else if (row.role === 'assistant') {
        localAssistantIdx += 1
      }
      if (!local) return row
      const next = { ...row }
      if (local.usageStats && !next.usageStats) next.usageStats = local.usageStats
      if (local.relatedSearchOffer && !next.relatedSearchOffer) {
        next.relatedSearchOffer = { ...local.relatedSearchOffer, loading: false }
      }
      if (local.askConfirmOffer && !next.askConfirmOffer) {
        next.askConfirmOffer = { ...local.askConfirmOffer }
      }
      return next
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
    if (!userStore.isLoggedIn || nav === 'drawing') return
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
    }
  }

  async function loadMessagesForNav(nav) {
    messagesEpoch.value += 1
    ensureActiveSession(nav)
    const id = activeLocalSessionId.value[nav]
    const localSess = (localSessionsByMode.value[nav] || []).find((s) => String(s.id) === String(id))
    const localMsgs = localSess ? (localSess.messages || []) : []
    if (userStore.isLoggedIn && /^\d+$/.test(String(id))) {
      try {
        const res = await getCompanionMessages(id)
        if (res.code === 0 && Array.isArray(res.data)) {
          messages.value = mergeLocalMessageExtras(mapVoToMessages(res.data), messagesForPersist(localMsgs))
          await restoreRelatedRecommendations(id)
          restoreAskWizardFromMessages()
          cacheSessionMessages(nav, id, messages.value)
          scrollFsToBottom()
          return
        }
      } catch {
      }
    }
    messages.value = messagesForPersist(localMsgs)
    await restoreRelatedRecommendations(id)
    restoreAskWizardFromMessages()
    scrollFsToBottom()
  }

  function restoreAskWizardFromMessages() {
    askWizard.value = null
    for (let i = messages.value.length - 1; i >= 0; i -= 1) {
      const message = messages.value[i]
      if (message?.askConfirmOffer?.questions?.length) {
        openAskWizard(message, message.askConfirmOffer)
        return
      }
      // 旧缓存：单题 drawConfirmOffer
      if (message?.drawConfirmOffer?.options?.length) {
        openAskWizard(message, {
          purpose: 'draw',
          questions: [{
            id: 'q1',
            question: message.drawConfirmOffer.question || '想画哪个主题？',
            options: message.drawConfirmOffer.options,
          }],
        })
        return
      }
    }
  }

  async function restoreRelatedRecommendations(id) {
    if (!userStore.isLoggedIn || !/^\d+$/.test(String(id))) return
    try {
      const res = await listMascotRelatedRecommendations(id)
      if (res.code !== 0 || !Array.isArray(res.data)) return
      const restoredIds = new Set(
        messages.value
          .filter((message) => message.type === 'related-result' && message.recommendationId)
          .map((message) => String(message.recommendationId)),
      )
      const restoredQueries = new Set(
        messages.value
          .filter((message) => message.type === 'related-result')
          .map((message) => normalizeRelatedQuery(message.relatedQuery))
          .filter(Boolean),
      )
      for (const recommendation of res.data) {
        const recommendationId = String(recommendation.id)
        const relatedQuery = normalizeRelatedQuery(recommendation.query)
        if (restoredIds.has(recommendationId) || (relatedQuery && restoredQueries.has(relatedQuery))) {
          continue
        }
        restoredIds.add(recommendationId)
        if (relatedQuery) restoredQueries.add(relatedQuery)
        const items = Array.isArray(recommendation.items) ? recommendation.items : []
        if (!items.length) continue
        insertRelatedResult({
          role: 'assistant',
          type: 'related-result',
          content: `检索到 ${items.length} 条相关帖子`,
          relatedItems: items,
          relatedQuery,
          recommendationId: recommendation.id,
          sourceMessageId: recommendation.sourceMessageId || null,
          at: recommendation.createTime ? new Date(recommendation.createTime).getTime() : Date.now(),
        })
      }
    } catch {
      // 已加载会话消息时，推荐恢复失败不影响对话
    }
  }

  function insertRelatedResult(result) {
    const sourceMessageId = result?.sourceMessageId == null ? '' : String(result.sourceMessageId)
    const sourceIndex = sourceMessageId
      ? messages.value.findIndex((message) => String(message.messageId || '') === sourceMessageId)
      : -1
    if (sourceIndex >= 0) {
      messages.value.splice(sourceIndex + 1, 0, result)
      return
    }
    const resultTime = Number(result?.at || 0)
    const laterIndex = messages.value.findIndex((message) => Number(message.at || 0) > resultTime)
    if (laterIndex >= 0) {
      messages.value.splice(laterIndex, 0, result)
      return
    }
    messages.value.push(result)
  }

  function applyServerSessionId(meta) {
    if (!userStore.isLoggedIn || !meta?.sessionId) return
    const sid = String(meta.sessionId)
    if (!/^\d+$/.test(sid)) return
    const nav = activeNav.value
    const previousId = String(sessionId.value || activeLocalSessionId.value[nav] || '')
    // 本地会话首次落库时把临时 id 换成服务端 id。
    // 已经是数字 id 却对不上，说明用户中途切走了——再覆盖会把人拽回旧会话，
    // 还会把当前显示的消息拷进旧会话的本地记录。
    if (/^\d+$/.test(previousId) && previousId !== sid) return
    sessionId.value = sid
    activeLocalSessionId.value = { ...activeLocalSessionId.value, [nav]: sid }
    const list = [...(localSessionsByMode.value[nav] || [])]
    const serverIndex = list.findIndex((session) => String(session.id) === sid)
    const provisionalIndex = /^\d+$/.test(previousId)
      ? -1
      : list.findIndex((session) => String(session.id) === previousId)
    const firstUser = messages.value.find((message) => message.role === 'user' && message.type !== 'image')
    const currentTitle = (firstUser?.content || '').trim().slice(0, 28) || uiLabels.untitledSession
    if (serverIndex >= 0) {
      list[serverIndex] = {
        ...list[serverIndex],
        title: list[serverIndex].title || currentTitle,
        messages: messagesForPersist(messages.value),
        updateTime: Date.now(),
      }
      if (provisionalIndex >= 0 && provisionalIndex !== serverIndex) {
        list.splice(provisionalIndex, 1)
      }
    } else if (provisionalIndex >= 0) {
      list[provisionalIndex] = {
        ...list[provisionalIndex],
        id: sid,
        title: currentTitle,
        messages: messages.value.map((message) => ({ ...message })),
        updateTime: Date.now(),
      }
    } else {
      list.unshift({
        id: sid,
        title: currentTitle,
        messages: messages.value.map((message) => ({ ...message })),
        updateTime: Date.now(),
      })
    }
    localSessionsByMode.value = { ...localSessionsByMode.value, [nav]: list }
    saveLocalSessionsToStorage()
  }

  const ASK_LETTERS = ['A', 'B', 'C', 'D']
  /** 分步 Ask 向导：挂在最新 askConfirmOffer 上，答完再一次性发给模型 */
  const askWizard = ref(null)
  // { message, purpose, questions, index, answers: [{label,value}|null], customText, submitting }

  function normalizeAskQuestions(rawQuestions) {
    if (!Array.isArray(rawQuestions)) return []
    return rawQuestions.slice(0, 5).map((item, index) => {
      const options = (Array.isArray(item?.options) ? item.options : [])
        .map((opt) => {
          const label = String(opt?.label || '').trim().slice(0, 40)
          if (!label || /自定义|其他|其它|都不是/.test(label)) return null
          const value = String(opt?.value || opt?.prompt || label).trim().slice(0, 800)
          if (!label || !value) return null
          return { label, value }
        })
        .filter(Boolean)
        .slice(0, 4)
        .map((opt, oi) => ({ ...opt, letter: ASK_LETTERS[oi] }))
      if (options.length < 2) return null
      return {
        id: String(item?.id || `q${index + 1}`).slice(0, 32),
        question: String(item?.question || '').trim().slice(0, 120),
        options,
      }
    }).filter((item) => item && item.question)
  }

  function openAskWizard(message, offer) {
    const questions = normalizeAskQuestions(offer?.questions)
    if (!questions.length) return
    askWizard.value = {
      message,
      purpose: String(offer?.purpose || 'clarify'),
      questions,
      index: 0,
      answers: questions.map(() => null),
      customText: '',
      submitting: false,
    }
  }

  const activeAsk = computed(() => {
    const wizard = askWizard.value
    if (!wizard?.questions?.length) return null
    // 流式未结束不展示，避免抢在正文前面
    if (wizard.message?.streaming) return null
    const q = wizard.questions[wizard.index]
    if (!q) return null
    return {
      ...wizard,
      current: q,
      total: wizard.questions.length,
      step: wizard.index + 1,
      isFirst: wizard.index <= 0,
      isLast: wizard.index >= wizard.questions.length - 1,
    }
  })

  function clearAskOffersAndWizard() {
    let changed = false
    messages.value.forEach((item) => {
      if (item?.askConfirmOffer || item?.drawConfirmOffer) {
        item.askConfirmOffer = null
        item.drawConfirmOffer = null
        changed = true
      }
    })
    askWizard.value = null
    if (changed) persistCurrentMessages()
  }

  function dismissActiveAsk() {
    clearAskOffersAndWizard()
  }

  function askGoBack() {
    const wizard = askWizard.value
    if (!wizard || wizard.submitting || loading.value || wizard.index <= 0) return
    wizard.index -= 1
    const prev = wizard.answers[wizard.index]
    wizard.customText = prev?.custom ? String(prev.value || '') : ''
  }

  function buildAskConfirmMessage(wizard) {
    const lines = ['【用户澄清回答】']
    wizard.questions.forEach((q, i) => {
      const ans = wizard.answers[i]
      if (!ans) return
      lines.push(`Q${i + 1}：${q.question}`)
      lines.push(`选择：${ans.label}`)
      if (ans.value && ans.value !== ans.label) {
        lines.push(`说明：${ans.value}`)
      }
      lines.push('')
    })
    lines.push('请根据以上澄清继续处理我之前的请求，不要再重复同样的问题。')
    return lines.join('\n').trim()
  }

  async function commitAskAnswer(answer) {
    const wizard = askWizard.value
    if (!wizard || wizard.submitting || loading.value || !answer) return
    wizard.answers[wizard.index] = answer
    wizard.customText = ''
    if (wizard.index < wizard.questions.length - 1) {
      wizard.index += 1
      const next = wizard.answers[wizard.index]
      wizard.customText = next?.custom ? String(next.value || '') : ''
      return
    }
    wizard.submitting = true
    const snapshot = {
      purpose: wizard.purpose,
      questions: wizard.questions.map((q) => ({
        id: q.id,
        question: q.question,
        options: q.options.map(({ label, value }) => ({ label, value })),
      })),
      answers: wizard.answers.map((a) => (a ? { ...a } : null)),
      message: wizard.message,
    }
    const text = buildAskConfirmMessage(wizard)
    clearAskOffersAndWizard()
    try {
      await sendInternal(text)
    } catch {
      if (snapshot.message) {
        snapshot.message.askConfirmOffer = {
          purpose: snapshot.purpose,
          questions: snapshot.questions,
        }
      }
      askWizard.value = {
        message: snapshot.message,
        purpose: snapshot.purpose,
        questions: normalizeAskQuestions(snapshot.questions),
        index: Math.max(0, snapshot.questions.length - 1),
        answers: snapshot.answers,
        customText: '',
        submitting: false,
      }
      persistCurrentMessages()
    }
  }

  function pickAskOption(option) {
    const label = String(option?.label || '').trim()
    const value = String(option?.value || '').trim()
    if (!label || !value) return
    commitAskAnswer({ label, value, custom: false })
  }

  function submitAskCustom() {
    const wizard = askWizard.value
    const custom = String(wizard?.customText || '').trim()
    if (!wizard || !custom) return
    commitAskAnswer({ label: '补充说明', value: custom, custom: true })
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
    const sourceMessageId = Number(message.messageId)
    if (!Number.isInteger(numericSessionId) || numericSessionId <= 0) {
      ElMessage.warning('会话尚未准备好，请稍后再试')
      return
    }
    if (!Number.isInteger(sourceMessageId) || sourceMessageId <= 0) {
      ElMessage.warning('消息仍在保存，请稍后再试')
      return
    }
    offer.loading = true
    try {
      const res = await getMascotRelatedRecommendations({
        sessionId: numericSessionId,
        sourceMessageId,
        query: offer.query,
      })
      if (res.code !== 0) {
        ElMessage.error(res.message || '相关帖子检索失败')
        return
      }
      message.relatedSearchOffer = null
      const items = Array.isArray(res.data?.items) ? res.data.items : []
      const relatedQuery = normalizeRelatedQuery(res.data?.query || offer.query)
      const hasRelatedResult = messages.value.some((item) => (
        item.type === 'related-result'
        && String(item.sourceMessageId || '') === String(sourceMessageId)
      ))
      if (items.length && !hasRelatedResult) {
        insertRelatedResult({
          role: 'assistant',
          type: 'related-result',
          content: `检索到 ${items.length} 条相关帖子`,
          relatedItems: items,
          relatedQuery,
          recommendationId: res.data?.id,
          sourceMessageId: res.data?.sourceMessageId || sourceMessageId,
          at: message.at || Date.now(),
        })
      } else if (!hasRelatedResult) {
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

  function normalizeRelatedQuery(query) {
    return String(query || '').trim().replace(/\s+/g, ' ')
  }

  function openRelatedRecommendation(items) {
    relatedDialogItems.value = Array.isArray(items) ? items : []
    relatedDialogVisible.value = true
  }

  function openSearchGallery(items) {
    searchGalleryItems.value = Array.isArray(items) ? items : []
    searchGalleryVisible.value = searchGalleryItems.value.length > 0
  }

  async function refreshContextWindow({ autoCompress = true } = {}) {
    const id = String(sessionId.value || '')
    if (!userStore.isLoggedIn || !/^\d+$/.test(id)) {
      contextWindow.value = { usedTokens: 0, maxTokens: 128000, canCompress: false }
      return
    }
    try {
      const res = await getCompanionContextWindow(id)
      contextWindow.value = res.code === 0 && res.data
        ? res.data
        : { usedTokens: 0, maxTokens: 128000, canCompress: false }
    } catch {
      contextWindow.value = { usedTokens: 0, maxTokens: 128000, canCompress: false }
    }
    if (autoCompress && contextWindow.value.canCompress
        && contextWindow.value.usedTokens >= contextWindow.value.maxTokens) {
      await compressContext({ automatic: true })
    }
  }

  async function compressContext({ automatic = false } = {}) {
    const id = String(sessionId.value || '')
    // AI 作答中禁止压缩，避免与流式会话抢状态
    if (!/^\d+$/.test(id) || contextCompressing.value || loading.value) return
    contextCompressing.value = true
    try {
      const res = await compressCompanionContext(id)
      if (res.code !== 0) throw new Error(res.message || '上下文压缩失败')
      if (res.data && typeof res.data === 'object') {
        contextWindow.value = res.data
      }
      await loadMessagesForNav(activeNav.value)
      // 压缩后始终从服务端回读，避免只刷新消息列表而上下文进度条仍是旧值
      await refreshContextWindow({ autoCompress: false })
      if (!automatic) ElMessage.success('上下文已压缩')
    } catch (error) {
      ElMessage.error(error?.message || '上下文压缩失败')
    } finally {
      contextCompressing.value = false
    }
  }

  async function refreshMascotMemory() {
    if (!userStore.isLoggedIn) {
      memorySummary.value = ''
      memoryFacts.value = []
      return
    }
    try {
      const res = await getMascotMemory()
      const data = res?.data || {}
      memorySummary.value = String(data.summary || '').trim().slice(0, MEMORY_SUMMARY_MAX)
      const facts = Array.isArray(data.facts) ? data.facts : []
      memoryFacts.value = facts
        .map((item) => String(item || '').trim().slice(0, MEMORY_FACT_ITEM_MAX))
        .filter(Boolean)
        .slice(0, MEMORY_FACTS_MAX)
    } catch {
      memorySummary.value = ''
      memoryFacts.value = []
    }
  }

  async function openMemoryDialog() {
    if (!await ensureLoggedIn({ message: '登录后才能查看记忆' })) return
    memoryEditDraft.value = ''
    memoryDialogVisible.value = true
    await refreshMascotMemory()
  }

  async function submitMemoryEdit() {
    const instruction = String(memoryEditDraft.value || '').trim()
    if (!instruction || memorySaving.value) return
    if (instruction.length > MEMORY_EDIT_MAX) {
      ElMessage.warning(`修改说明不能超过 ${MEMORY_EDIT_MAX} 字`)
      return
    }
    memorySaving.value = true
    try {
      const res = await editMascotMemory({ instruction })
      const data = res?.data || {}
      memorySummary.value = String(data.summary || '').trim().slice(0, MEMORY_SUMMARY_MAX)
      const facts = Array.isArray(data.facts) ? data.facts : []
      memoryFacts.value = facts
        .map((item) => String(item || '').trim().slice(0, MEMORY_FACT_ITEM_MAX))
        .filter(Boolean)
        .slice(0, MEMORY_FACTS_MAX)
      memoryEditDraft.value = ''
      ElMessage.success('记忆已更新')
    } catch (error) {
      ElMessage.error(error?.message || '记忆更新失败')
    } finally {
      memorySaving.value = false
    }
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
  
  function formatMessageDay(ts) {
    const date = new Date(ts)
    if (Number.isNaN(date.getTime())) return ''
    const now = new Date()
    const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
    const startMessage = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
    const dayDiff = Math.floor((startToday - startMessage) / 86_400_000)
    if (dayDiff === 0) return '今天'
    if (dayDiff === 1) return '昨天'
    if (dayDiff === 2) return '前天'
    if (date.getFullYear() !== now.getFullYear()) return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }

  function shouldShowDateDivider(rows, index) {
    if (!rows[index]?.at) return false
    if (index === 0) return true
    const current = new Date(rows[index].at)
    const previous = new Date(rows[index - 1]?.at)
    return current.toDateString() !== previous.toDateString()
  }

  async function refreshQuotaHint() {
    void refreshQuotaPanel()
    if (!userStore.isLoggedIn || activeNav.value === 'drawing') {
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

  async function refreshQuotaPanel() {
    if (!userStore.isLoggedIn) {
      quotaPanel.value = null
      return
    }
    try {
      const res = await getVipQuota()
      quotaPanel.value = res?.data || null
    } catch {
      quotaPanel.value = null
    }
  }

  function isCurrentSessionEmpty() {
    return messages.value.length === 0
  }

  function startNewSession() {
    const nav = activeNav.value
    if (isCurrentSessionEmpty()) {
      ElMessage.info(uiLabels.alreadyNewSession)
      return
    }
    // 确认真要新建了再作废在途的流，别在「已经是新会话」那条分支上白白打断
    messagesEpoch.value += 1
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
  
  const companionAvatarSrc = DEFAULT_AVATAR
  
  function loadSavedPreferences() {
    try {
      const chat = localStorage.getItem(LLM_CHAT_KEY)
      const w = localStorage.getItem(LLM_WRITING_KEY)
      const h = localStorage.getItem(LLM_HELP_KEY)
      const legacy = localStorage.getItem('mascot_llm_provider_v1')
      const legacyMap = { qwen: 'qwen-flash' }
      const leg = legacy && legacyMap[legacy] ? legacyMap[legacy] : ''
      const pick = chat || w || h || leg
      if (pick && ALL_LLM_OPTIONS.some(x => x.id === pick))
        selectedLlmChat.value = pick
      else
        selectedLlmChat.value = 'qwen-flash'
      const q = localStorage.getItem(IMAGE_QUALITY_KEY)
      if (q === 'normal')
        imageQuality.value = q
    }
    catch {
    }
  }
  
  function saveLlmPrefs() {
    try {
      localStorage.setItem(LLM_CHAT_KEY, selectedLlmChat.value)
      localStorage.setItem(IMAGE_QUALITY_KEY, imageQuality.value)
    }
    catch {
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
      refreshQuotaHint()
    }
  }, { immediate: false })

  watch(assistantOpen, (open) => {
    if (open) {
      stopMascotIdleTips()
      clearStageCloudTip()
      cancelPatrol()
    }
    else {
      startMascotIdleTips()
      schedulePatrol()
    }
  })

  function viewportMaxX() {
    if (typeof window === 'undefined') return SPRITE_EDGE_MARGIN
    return Math.max(SPRITE_EDGE_MARGIN, window.innerWidth - SPRITE_WIDTH - SPRITE_EDGE_MARGIN)
  }

  function clampSpriteX() {
    spriteX.value = Math.min(viewportMaxX(), Math.max(SPRITE_EDGE_MARGIN, spriteX.value))
  }

  function randomBetween(min, max) {
    return min + Math.random() * Math.max(0, max - min)
  }

  function canPatrol() {
    return spriteReady.value
      && !assistantOpen.value
      && !loading.value
      && !agentSpriteState.value
      && !reactionSpriteState.value
      && !pageHidden.value
  }

  function clearPatrolDelay() {
    if (patrolDelayTimer) {
      clearTimeout(patrolDelayTimer)
      patrolDelayTimer = null
    }
  }

  function cancelPatrol() {
    clearPatrolDelay()
    if (patrolAnimationFrame) {
      cancelAnimationFrame(patrolAnimationFrame)
      patrolAnimationFrame = 0
    }
    patrolDirection.value = ''
    patrolLastTimestamp = 0
  }

  function schedulePatrol(delayMs = randomBetween(SPRITE_IDLE_MIN_MS, SPRITE_IDLE_MAX_MS)) {
    cancelPatrol()
    if (!canPatrol()) return
    patrolDelayTimer = setTimeout(beginPatrol, delayMs)
  }

  function beginPatrol() {
    patrolDelayTimer = null
    if (!canPatrol()) return
    if (Math.random() > SPRITE_PATROL_CHANCE) {
      schedulePatrol()
      return
    }
    const minX = SPRITE_EDGE_MARGIN
    const maxX = viewportMaxX()
    if (maxX <= minX) return
    const distance = Math.min(maxX - minX, randomBetween(SPRITE_MIN_TRAVEL, SPRITE_MAX_TRAVEL))
    let direction = Math.random() < 0.5 ? -1 : 1
    if (spriteX.value - distance < minX) direction = 1
    if (spriteX.value + distance > maxX) direction = -1
    patrolTargetX = Math.min(maxX, Math.max(minX, spriteX.value + direction * distance))
    if (Math.abs(patrolTargetX - spriteX.value) < 1) {
      schedulePatrol()
      return
    }
    patrolDirection.value = direction > 0 ? 'running-right' : 'running-left'
    clearStageCloudTip()
    patrolLastTimestamp = 0
    patrolAnimationFrame = requestAnimationFrame(stepPatrol)
  }

  function stepPatrol(timestamp) {
    if (!canPatrol()) {
      cancelPatrol()
      return
    }
    if (spriteHovered.value) {
      patrolLastTimestamp = timestamp
      patrolAnimationFrame = requestAnimationFrame(stepPatrol)
      return
    }
    if (!patrolLastTimestamp) patrolLastTimestamp = timestamp
    const elapsedSeconds = Math.min(0.05, (timestamp - patrolLastTimestamp) / 1000)
    patrolLastTimestamp = timestamp
    const direction = patrolTargetX >= spriteX.value ? 1 : -1
    const nextX = spriteX.value + direction * SPRITE_SPEED_PX_PER_SECOND * elapsedSeconds
    const arrived = direction > 0 ? nextX >= patrolTargetX : nextX <= patrolTargetX
    spriteX.value = arrived ? patrolTargetX : nextX
    if (arrived) {
      patrolAnimationFrame = 0
      patrolDirection.value = ''
      patrolLastTimestamp = 0
      schedulePatrol()
      return
    }
    patrolAnimationFrame = requestAnimationFrame(stepPatrol)
  }

  function playSpriteReaction(state) {
    cancelPatrol()
    agentSpriteState.value = ''
    reactionSpriteState.value = state
  }

  function setAgentSpriteState(state) {
    if (reactionSpriteState.value === 'failed') return
    cancelPatrol()
    reactionSpriteState.value = ''
    agentSpriteState.value = state
  }

  function clearAgentSpriteState() {
    agentSpriteState.value = ''
  }

  function onSpriteAnimationComplete(state) {
    if (reactionSpriteState.value !== state) return
    reactionSpriteState.value = ''
    if (!assistantOpen.value) schedulePatrol()
  }

  function onSpriteHoverChange(hovered) {
    spriteHovered.value = Boolean(hovered)
  }

  function onSpriteReady() {
    spriteReady.value = true
    spriteX.value = viewportMaxX()
    clampSpriteX()
    startMascotIdleTips()
    schedulePatrol()
  }

  async function openAssistantFromSprite() {
    if (!await ensureLoggedIn('与看板娘互动需要登录')) return
    assistantOpen.value = true
  }

  function onViewportResize() {
    clampSpriteX()
    if (!assistantOpen.value && !loading.value) schedulePatrol()
  }

  function onVisibilityChange() {
    pageHidden.value = document.hidden
    if (pageHidden.value) {
      cancelPatrol()
    }
    else if (!assistantOpen.value && !loading.value) {
      schedulePatrol()
    }
  }
  
  function navToSkill(nav) {
    const map = {
      chat: 'chat',
      drawing: 'drawing',
    }
    activeSkill.value = map[nav] || 'chat'
  }
  
  async function selectNav(nav) {
    if (activeNav.value !== nav) {
      persistCurrentMessages()
    }
    activeNav.value = nav
    navToSkill(nav)
    draft.value = ''
    if (userStore.isLoggedIn) {
      await syncServerSessions(nav)
    }
    await loadMessagesForNav(nav)
    refreshQuotaHint()
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
    await syncServerSessions(activeNav.value)
    await loadMessagesForNav(activeNav.value)
    scrollFsToBottom()
    refreshQuotaHint()
    await refreshContextWindow()
  }
  
  function onSkillForSend() {
    navToSkill(activeNav.value)
  }
  
  function buildChatHistory() {
    const rows = messages.value
      .filter((m) => m.type !== 'image' && m.type !== 'context_summary' && m.type !== 'related-result')
      .map((m) => ({ role: m.role, content: m.content }))
    if (rows.length && rows[rows.length - 1].role === 'user') {
      return rows.slice(0, -1)
    }
    return rows
  }

  async function regenerateAssistant(index) {
    messagesEpoch.value += 1
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
    setAgentSpriteState('running')
    // 用户另发消息时收起未完成的 Ask 面板，避免卡在输入框上方
    if (!skipPushUser) {
      messages.value.forEach((item) => {
        if (item?.askConfirmOffer) item.askConfirmOffer = null
        if (item?.drawConfirmOffer) item.drawConfirmOffer = null
      })
      askWizard.value = null
    }
    const now = Date.now()
    if (!skipPushUser) {
      messages.value.push({ role: 'user', content: text, type: 'text', at: now })
      persistCurrentMessages()
    }

    const history = buildChatHistory()
    // 本轮流所属的视图快照；切走后回调只做收尾、不再碰 messages
    const epoch = messagesEpoch.value
    const isStale = () => epoch !== messagesEpoch.value
    streamEpoch.value = epoch

    const skill = activeNav.value === 'drawing' ? 'drawing' : 'chat'
    let streamHadError = false
    let streamProducedImage = false
    let assistantIdx = -1

    try {
      if (chatStreamAbort) {
        chatStreamAbort()
        chatStreamAbort = null
      }
      clearThinkingRotation()
      assistantIdx = messages.value.length
      messages.value.push({
        role: 'assistant',
        content: '',
        type: 'text',
        at: Date.now(),
        streaming: true,
        thinkingText: streamStatusText('preparing'),
        imageGallery: [],
      })
      await new Promise((resolve, reject) => {
        chatStreamAbort = streamMascotChat(
          {
            message: text,
            sessionId: sid,
            mascotModelCode: MASCOT_CODE,
            skill,
            history,
            ephemeral: !userStore.isLoggedIn,
            clientDatetime: new Date().toISOString(),
            // 单次请求幂等键：重试 / 重新生成须换新 id，避免计费撞车
            clientRequestId: (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
              ? crypto.randomUUID().replace(/-/g, '')
              : `${Date.now()}${Math.random().toString(16).slice(2)}`,
          },
          {
            onChunk(piece) {
              if (isStale()) return
              const row = messages.value[assistantIdx]
              if (!row) return
              if (row.thinkingText) row.thinkingText = ''
              clearThinkingRotation()
              setAgentSpriteState('running')
              row.content = (row.content || '') + piece
              scrollFsToBottom()
            },
            onMeta(meta) {
              if (isStale()) return
              applyServerSessionId(meta)
              const row = messages.value[assistantIdx]
              const statusText = streamStatusText(meta?.status)
              const nextSpriteState = streamSpriteState(meta?.status)
              if (nextSpriteState) setAgentSpriteState(nextSpriteState)
              if (statusText && row?.streaming && !(row.content || '').length) {
                clearThinkingRotation()
                row.thinkingText = statusText
              }
              if (meta?.imageGenerating) {
                imageGenerating.value = true
                setAgentSpriteState('running')
                if (row?.streaming && !(row.content || '').length) {
                  row.thinkingText = '正在绘制画面…'
                }
              }
              if (meta?.imageUrl && isSafeMascotImageUrl(meta.imageUrl)) {
                streamProducedImage = true
                // 工具可组合：保留同一轮的文字回答，再追加 Java 已授权并生成的图片
                messages.value.push({
                  role: 'assistant',
                  type: 'image',
                  url: meta.imageUrl,
                  at: Date.now(),
                  usageStats: usageStatsFromApi(meta),
                })
                persistCurrentMessages()
                scrollFsToBottom()
              }
              if (meta?.relatedSearchOffer && meta?.relatedSearchQuery && row) {
                row.relatedSearchOffer = {
                  query: String(meta.relatedSearchQuery).slice(0, 500),
                  loading: false,
                }
              }
              if (meta?.askConfirmOffer && row) {
                const questions = normalizeAskQuestions(meta.askConfirmOffer.questions)
                if (questions.length) {
                  row.askConfirmOffer = {
                    purpose: String(meta.askConfirmOffer.purpose || 'clarify'),
                    questions: questions.map((q) => ({
                      id: q.id,
                      question: q.question,
                      options: q.options.map(({ label, value }) => ({ label, value })),
                    })),
                  }
                  // 等本轮文字流式结束后再弹出，避免抢在回答前面
                }
              } else if (meta?.drawConfirmOffer && row) {
                // 兼容旧 SSE：单题生图确认
                const legacy = meta.drawConfirmOffer
                const ask = {
                  purpose: 'draw',
                  questions: [{
                    id: 'q1',
                    question: String(legacy.question || '想画哪个主题？'),
                    options: Array.isArray(legacy.options) ? legacy.options : [],
                  }],
                }
                const questions = normalizeAskQuestions(ask.questions)
                if (questions.length) {
                  row.askConfirmOffer = {
                    purpose: 'draw',
                    questions: questions.map((q) => ({
                      id: q.id,
                      question: q.question,
                      options: q.options.map(({ label, value }) => ({ label, value })),
                    })),
                  }
                }
              }
              if (meta?.assistantMessageId && row) {
                row.messageId = meta.assistantMessageId
              }
              if (Array.isArray(meta?.searchImageGallery) && row) {
                row.imageGallery = meta.searchImageGallery
                  .filter((item) => isSafeMascotImageUrl(item?.url))
                  .slice(0, 5)
                row.stripInlineImages = row.imageGallery.length > 0
              }
              const stats = usageStatsFromApi(meta)
              if (stats && row) row.usageStats = stats
              // 生图单步失败：回复已经流出来了，只提示图片没生成
              if (meta?.imageError) {
                ElMessage.warning(String(meta.imageError))
              }
            },
            onDone() {
              clearThinkingRotation()
              if (isStale()) {
                chatStreamAbort = null
                resolve()
                return
              }
              const row = messages.value[assistantIdx]
              if (row) {
                row.streaming = false
                row.thinkingText = ''
                if (!streamHadError && !row.content?.trim()) row.content = '…'
                if (!streamHadError && row.askConfirmOffer?.questions?.length) {
                  openAskWizard(row, row.askConfirmOffer)
                }
              }
              chatStreamAbort = null
              refreshContextWindow()
              // 这一轮已经花掉额度，左下角要跟上，否则得关掉面板重开才更新
              refreshQuotaHint()
              persistCurrentMessages()
              if (!streamHadError) {
                clearAgentSpriteState()
                playSpriteReaction(streamProducedImage ? 'jumping' : 'waving')
                showStageCloudTip('回复好了，点我查看～', 2800)
                resolve()
              }
            },
            onError(msg) {
              streamHadError = true
              playSpriteReaction('failed')
              clearThinkingRotation()
              chatStreamAbort = null
              if (isStale()) {
                reject(new Error(msg || 'stream failed'))
                return
              }
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
      if (isStale()) throw e
      if (!streamHadError) {
        playSpriteReaction('failed')
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
      streamEpoch.value = -1
      clearAgentSpriteState()
      if (assistantIdx >= 0 && !isStale()) {
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
    if (contextCompressing.value) return
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      return
    }
    draft.value = ''
    // 失败要把草稿还回去，但用户中途切了会话就别还——那是另一个会话的输入框
    const epoch = messagesEpoch.value
    try {
      await sendInternal(text)
    } catch {
      if (epoch === messagesEpoch.value) draft.value = text
    }
  }
  
  watch(
    () => userStore.id,
    async (id, prev) => {
      if (id && id !== prev) {
        loadLocalSessionsFromStorage()
        if (assistantOpen.value) {
          await syncServerSessions(activeNav.value)
          await loadMessagesForNav(activeNav.value)
        }
      }
    },
  )

  onMounted(() => {
    loadLocalSessionsFromStorage()
    loadSavedPreferences()
    pageHidden.value = document.hidden
    window.addEventListener('resize', onViewportResize)
    document.addEventListener('visibilitychange', onVisibilityChange)
  })
  
  onBeforeUnmount(() => {
    stopMascotIdleTips()
    clearStageCloudTip()
    clearThinkingRotation()
    if (chatStreamAbort) {
      chatStreamAbort()
      chatStreamAbort = null
    }
    cancelPatrol()
    window.removeEventListener('resize', onViewportResize)
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return {
    acceptRelatedSearchOffer,
    activeAsk,
    askGoBack,
    askWizard,
    assistantOpen,
    cancelRenameSession,
    companionAvatarSrc,
    commitRenameSession,
    compressContext,
    contextCompressing,
    contextWindow,
    deleteSession,
    deletingSessionId,
    dismissActiveAsk,
    dismissRelatedSearchOffer,
    draft,
    pickAskOption,
    streamInCurrentView,
    submitAskCustom,
    formatAiUsageLine,
    formatMessageDay,
    formatSessionTime,
    imageGenerating,
    imageModelOptions,
    imageQuality,
    inputPlaceholder,
    isVip,
    isLatestRegeneratableAssistant,
    loading,
    memoryDialogVisible,
    memoryEditDraft,
    memoryFacts,
    memorySaving,
    memorySummary,
    MEMORY_EDIT_MAX,
    MEMORY_FACTS_MAX,
    MEMORY_SUMMARY_MAX,
    displayMemoryFacts,
    displayMemorySummary,
    messages,
    onAssistantOpened,
    onSpriteAnimationComplete,
    onSpriteHoverChange,
    onSpriteReady,
    openAssistantFromSprite,
    openMemoryDialog,
    openRelatedArticle,
    openRelatedRecommendation,
    openSearchGallery,
    quotaExhausted,
    quotaRows,
    regenerateAssistant,
    relatedDialogItems,
    relatedDialogVisible,
    renameDraft,
    renameInputRef,
    renameSubmitting,
    renamingSessionId,
    renderMascotMarkdown,
    searchGalleryItems,
    searchGalleryVisible,
    scrollbarFs,
    selectLocalSession,
    send,
    sessionId,
    sessionListForNav,
    shouldShowDateDivider,
    spritePaused,
    spriteState,
    spriteX,
    stageTipText,
    startRenameSession,
    startNewSession,
    submitMemoryEdit,
    uiLabels,
    userStore,
  }
}
