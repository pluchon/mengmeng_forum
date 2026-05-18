import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ZoomIn,
  EditPen,
  Picture,
  Reading,
  QuestionFilled,
  UserFilled,
  MagicStick,
  Refresh,
  CopyDocument,
  Sunny,
  List,
  Brush,
  Avatar,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { usePointsWalletStore } from '@/stores/pointsWallet'
import { useMascotUiStore } from '@/stores/mascotUi'
import {
  getCompanionMessages,
  getCompanionSessions,
  getMascotPublicModels,
  postMascotChat,
  setMascotModel,
} from '@/api/mascot'
import { aiImage, aiPriceEstimate } from '@/api/ai'
import { DEFAULT_AVATAR } from '@/utils/constants'
import iconDeepseek from '@/assets/svg/deepseek-color.svg'
import iconQwen from '@/assets/svg/qwen-color.svg'
import iconGemini from '@/assets/svg/gemini-color.svg'
import companionXiaomai from '@/assets/images/xiaomai.png'
import companionMiku from '@/assets/images/miku.png'

export function useMascotDock() {
  const OFFSET_KEY = 'mascot_dock_offset_v1'
  const SCALE_KEY = 'mascot_stage_scale_v1'
  const LLM_WRITING_KEY = 'mascot_llm_writing_v3'
  const LLM_HELP_KEY = 'mascot_llm_help_v3'
  const IMAGE_QUALITY_KEY = 'mascot_image_quality_v1'
  const GUEST_MASCOT_CODE_KEY = 'mascot_guest_model_code_v1'
  const STAGE_BASE_W = 400
  const STAGE_BASE_H = 460

  const uiLabels = {
    ariaRoot: '看板娘',
    scaleTitle: '缩放显示',
    scaleHint: '拖动滑块调整看板娘显示大小',
    brandTitle: '陪伴助手',
    statusOnline: '在线 · 陪伴助手',
    defaultNickname: '用户',
    historyBtn: '历史记录',
    guest: '未登录',
    appearanceEmpty: '暂无上架模型，请管理员在后台配置并上架。',
    applyAppearance: '选用此形象',
    today: '今天',
    openImageInNewTab: '在新标签打开',
    typing: '正在输入',
    quickChipsGroup: '快捷指令',
    historyEmpty: '当前功能暂无历史会话',
    untitledSession: '未命名会话',
  }
  
  /** 与 ai-server mascot_graph 路由一致；深度档 VIP 可用 */
  const ALL_LLM_OPTIONS = [
    { id: 'qwen-flash', label: '通义千问', hint: 'qwen3.6-flash', icon: iconQwen, vipOnly: false },
    { id: 'deepseek-flash', label: 'DeepSeek', hint: 'deepseek-v4-flash', icon: iconDeepseek, vipOnly: false },
    { id: 'gemini-flash', label: 'Gemini', hint: 'gemini-3-flash', icon: iconGemini, vipOnly: false },
    { id: 'qwen-deep', label: '通义千问 · 深度', hint: 'qwen3.6-max-preview', icon: iconQwen, vipOnly: true },
    { id: 'deepseek-deep', label: 'DeepSeek · 深度', hint: 'deepseek-v4-pro', icon: iconDeepseek, vipOnly: true },
    { id: 'gemini-deep', label: 'Gemini · 深度', hint: 'gemini-3.1-pro', icon: iconGemini, vipOnly: true },
  ]
  
  const userStore = useUserStore()
  const pointsWallet = usePointsWalletStore()
  const mascotUi = useMascotUiStore()
  const stageHost = ref(null)
  const stageUseFallback = ref(false)
  const scrollbarFs = ref(null)
  const oml2d = ref(null)
  
  const assistantOpen = ref(false)
  /** writing | drawing | reading | help | appearance */
  const activeNav = ref('writing')
  const catalog = ref([])
  const activeCode = ref('lafei')
  const pendingCode = ref('lafei')
  const messages = ref([])
  const draft = ref('')
  const loading = ref(false)
  const sessionId = ref('')
  const skillSessionIds = ref({
    writing: '',
    help: '',
    drawing: '',
    reading: '',
  })
  const historyDrawerOpen = ref(false)
  const historyLoading = ref(false)
  const historySessions = ref([])
  const activeSkill = ref('chat')
  
  const selectedLlmWriting = ref('qwen-flash')
  const selectedLlmHelp = ref('qwen-flash')
  const imageQuality = ref('normal')
  const estimatePoints = ref(null)
  const estimateLoading = ref(false)
  const stageHovered = ref(false)
  
  const modeTabs = [
    { id: 'writing', label: '写作', icon: EditPen },
    { id: 'drawing', label: '画图', icon: Picture },
    { id: 'reading', label: '伴读', icon: Reading },
    { id: 'help', label: '站点帮助', icon: QuestionFilled },
    { id: 'appearance', label: '形象选择', icon: UserFilled },
  ]
  
  const FLASH_LLM = ['qwen-flash', 'deepseek-flash', 'gemini-flash']
  
  function llmStorageKey() {
    return activeNav.value === 'help' ? LLM_HELP_KEY : LLM_WRITING_KEY
  }
  
  function currentLlmStorageKey() {
    return activeNav.value === 'help' ? LLM_HELP_KEY : LLM_WRITING_KEY
  }
  
  const selectedLlm = computed({
    get() {
      return activeNav.value === 'help' ? selectedLlmHelp.value : selectedLlmWriting.value
    },
    set(v) {
      if (activeNav.value === 'help')
        selectedLlmHelp.value = v
      else
        selectedLlmWriting.value = v
    },
  })
  const scalePopoverOpen = ref(false)
  
  const dragOffset = ref({ x: 0, y: 0 })
  const stageScale = ref(1)
  
  let stageGesture = false
  let stageMoved = false
  let stageStart = { px: 0, py: 0, ox: 0, oy: 0 }
  
  const inputPlaceholder = computed(() => {
    switch (activeNav.value) {
      case 'writing':
        return '示范：我想让你帮我写一段论坛发帖草稿，主题是：春节活动安利'
      case 'drawing':
        return '示范：描述你想生成的图片，例如：赛博朋克风格的猫咪头像'
      case 'reading':
        return '伴读功能开发中，敬请期待'
      case 'help':
        return '示范：请用简短条目介绍一下本论坛的常用功能和使用技巧'
      default:
        return '说点什么…'
    }
  })
  
  const isVip = computed(() => {
    if (Number(userStore.isAdmin) === 1)
      return true
    const t = Number(userStore.vipTier) || 0
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
  
  const llmOptions = computed(() => {
    const list = ALL_LLM_OPTIONS.filter(o => !o.vipOnly || isVip.value)
    if (activeNav.value === 'help')
      return list.filter(o => FLASH_LLM.includes(o.id))
    return list
  })
  
  const quickChips = computed(() => {
    if (activeNav.value === 'writing') {
      return [
        { label: '帮我优化', text: '请帮我优化上面这段内容的表达', icon: MagicStick },
        { label: '重新生成', text: '请根据上文重新生成一版', icon: Refresh },
        { label: '复制全文', text: '请把完整正文再输出一遍，方便我复制', icon: CopyDocument },
        { label: '换个方向', text: '请换一个写作角度或风格再写', icon: Sunny },
        { label: '加分点列表', text: '请改成条理清晰的分点列表', icon: List },
      ]
    }
    if (activeNav.value === 'help') {
      return [
        { label: '如何发帖', text: '论坛发帖流程是怎样的？', icon: EditPen },
        { label: '积分规则', text: '积分怎么获得和消耗？', icon: QuestionFilled },
        { label: 'VIP 权益', text: 'VIP 有哪些权益？', icon: UserFilled },
        { label: '版规摘要', text: '请简要说明社区版规要点', icon: List },
      ]
    }
    if (activeNav.value === 'drawing') {
      return [
        { label: '写实风格', text: '写实摄影风格，', icon: Picture },
        { label: '动漫插画', text: '日系动漫插画风格，', icon: Brush },
        { label: 'Q版头像', text: '可爱 Q 版头像，', icon: Avatar },
        { label: '换一张', text: '换一张不同构图的：', icon: Refresh },
      ]
    }
    return []
  })
  
  
  function sessionKeyForNav(nav) {
    const n = nav || activeNav.value
    if (n === 'appearance') return ''
    return n
  }
  
  function getSessionForNav(nav) {
    const k = sessionKeyForNav(nav)
    return k ? (skillSessionIds.value[k] || '') : ''
  }
  
  function setSessionForNav(nav, id) {
    const k = sessionKeyForNav(nav)
    if (!k) return
    skillSessionIds.value = { ...skillSessionIds.value, [k]: id ? String(id) : '' }
    sessionId.value = id ? String(id) : ''
  }
  
  function mapVoToMessages(rows) {
    return (rows || []).map((m) => ({
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content || '',
      type: m.type === 'image' ? 'image' : 'text',
      url: m.url || '',
      at: m.at ? new Date(m.at).getTime() : Date.now(),
    }))
  }
  
  async function loadMessagesForNav(nav) {
    const sid = getSessionForNav(nav)
    if (!userStore.isLoggedIn || !sid || nav === 'appearance') {
      messages.value = []
      return
    }
    try {
      const res = await getCompanionMessages(sid)
      messages.value = mapVoToMessages(res?.data || [])
    } catch {
      messages.value = []
    }
    scrollFsToBottom()
  }
  
  const historyDrawerTitle = computed(() => {
    const tab = modeTabs.find(t => t.id === activeNav.value)
    return `历史记录 · ${tab?.label || activeNav.value}`
  })
  
  function formatSessionTime(t) {
    if (!t) return ''
    const d = new Date(t)
    if (Number.isNaN(d.getTime())) return ''
    return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  
  async function openHistoryDrawer() {
    if (!userStore.isLoggedIn || activeNav.value === 'appearance') return
    historyDrawerOpen.value = true
    historyLoading.value = true
    try {
      const res = await getCompanionSessions(activeNav.value)
      historySessions.value = Array.isArray(res?.data) ? res.data : []
    } catch {
      historySessions.value = []
    } finally {
      historyLoading.value = false
    }
  }
  
  async function loadHistorySession(id) {
    setSessionForNav(activeNav.value, id)
    historyDrawerOpen.value = false
    await loadMessagesForNav(activeNav.value)
  }
  
  function formatMsgTime(ts) {
    if (!ts) return ''
    const d = new Date(ts)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  
  async function refreshEstimate() {
    if (!userStore.isLoggedIn || activeNav.value === 'appearance' || activeNav.value === 'reading') {
      estimatePoints.value = null
      return
    }
    estimateLoading.value = true
    try {
      const skill = activeNav.value === 'drawing' ? 'drawing' : (activeNav.value === 'help' ? 'help' : 'writing')
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
  
  function clearMessages() {
    messages.value = []
    setSessionForNav(activeNav.value, '')
  }

  function startNewSession() {
    clearMessages()
    draft.value = ''
  }
  
  function applyChip(chip) {
    const text = typeof chip === 'string' ? chip : (chip?.text || chip?.label || '')
    if (!text) return
    draft.value = text
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
  
  function loadSavedOffset() {
    try {
      const raw = sessionStorage.getItem(OFFSET_KEY)
      if (raw) {
        const o = JSON.parse(raw)
        if (typeof o.x === 'number' && typeof o.y === 'number') {
          dragOffset.value = { x: o.x, y: o.y }
        }
      }
      const rawS = sessionStorage.getItem(SCALE_KEY)
      if (rawS) {
        const s = parseFloat(rawS)
        if (!Number.isNaN(s) && s >= 0.55 && s <= 1.45) {
          stageScale.value = s
        }
      }
      const w = localStorage.getItem(LLM_WRITING_KEY)
      const h = localStorage.getItem(LLM_HELP_KEY)
      const legacy = localStorage.getItem('mascot_llm_provider_v1')
      const legacyMap = { qwen: 'qwen-flash', deepseek: 'deepseek-flash', gemini: 'gemini-flash' }
      const leg = legacy && legacyMap[legacy] ? legacyMap[legacy] : ''
      if (w && ALL_LLM_OPTIONS.some(x => x.id === w))
        selectedLlmWriting.value = w
      else if (leg)
        selectedLlmWriting.value = leg
      if (h && FLASH_LLM.includes(h))
        selectedLlmHelp.value = h
      else
        selectedLlmHelp.value = 'qwen-flash'
      const q = localStorage.getItem(IMAGE_QUALITY_KEY)
      if (q === 'normal' || q === 'premium')
        imageQuality.value = q
    }
    catch {
      /* ignore */
    }
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
      localStorage.setItem(LLM_WRITING_KEY, selectedLlmWriting.value)
      localStorage.setItem(LLM_HELP_KEY, selectedLlmHelp.value)
      localStorage.setItem(IMAGE_QUALITY_KEY, imageQuality.value)
    }
    catch {
      /* ignore */
    }
  }
  
  watch([selectedLlmWriting, selectedLlmHelp, imageQuality], () => saveLlmPrefs())
  
  watch(llmOptions, (opts) => {
    const cur = selectedLlm.value
    if (!opts.some(x => x.id === cur)) {
      if (activeNav.value === 'help')
        selectedLlmHelp.value = 'qwen-flash'
      else
        selectedLlmWriting.value = 'qwen-flash'
      saveLlmPrefs()
    }
  }, { immediate: true })
  
  watch([activeNav, selectedLlm, imageQuality, () => userStore.isLoggedIn], () => {
    if (assistantOpen.value)
      refreshEstimate()
  }, { immediate: false })
  
  const stageWrapStyle = computed(() => ({
    width: `${Math.round(STAGE_BASE_W * stageScale.value)}px`,
    height: `${Math.round(STAGE_BASE_H * stageScale.value)}px`,
  }))
  
  const rootStyle = computed(() => ({
    transform: `translate(${dragOffset.value.x}px, ${dragOffset.value.y}px)`,
  }))
  
  function applyStageScaleToLib() {
    try {
      oml2d.value?.setStageStyle?.({
        width: Math.round(STAGE_BASE_W * stageScale.value),
        height: Math.round(STAGE_BASE_H * stageScale.value),
      })
    } catch {
      /* ignore */
    }
  }
  
  function onScaleSliderChange() {
    saveScale()
    applyStageScaleToLib()
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
      saveOffset()
    }
    else {
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
    const needsBoost = m.code === 'xiaomai'
    const baseScale = Number(m.modelScale) || 0.1
    const scale = needsBoost
      ? Math.min(0.35, Math.max(baseScale * 1.45, baseScale + 0.04))
      : baseScale
    const posY = needsBoost ? Math.max(0, (m.posY ?? 72) - 10) : (m.posY ?? 72)
    let posX = m.posX ?? 0
    if (m.code === 'snow_miku')
      posX -= 48
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
        stageStyle: { width: STAGE_BASE_W, height: STAGE_BASE_H },
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
      writing: 'writing',
      drawing: 'drawing',
      reading: 'reading',
      help: 'help',
      appearance: 'chat',
    }
    activeSkill.value = map[nav] || 'chat'
  }
  
  function selectNav(nav) {
    activeNav.value = nav
    if (nav === 'appearance') {
      pendingCode.value = activeCode.value || resolveInitialCode() || (catalog.value[0]?.code ?? '')
      return
    }
    navToSkill(nav)
    draft.value = ''
    sessionId.value = getSessionForNav(nav)
    loadMessagesForNav(nav)
    refreshEstimate()
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
        dockedPosition: 'right',
        sayHello: false,
        menus: { disable: true },
        statusBar: { disable: true },
        models,
        initialStatus: 'active',
      })
      await waitOml2dLoad(oml2d.value)
      applyStageScaleToLib()
      applyModelMetricsForIndex(idx)
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
  
  function onAssistantOpened() {
    sessionId.value = getSessionForNav(activeNav.value)
    if (userStore.isLoggedIn) {
      pointsWallet.refresh()
      if (activeNav.value !== 'appearance')
        loadMessagesForNav(activeNav.value)
    }
    scrollFsToBottom()
    refreshEstimate()
  }
  
  function onSkillForSend() {
    navToSkill(activeNav.value)
  }
  
  async function send() {
    const text = draft.value.trim()
    if (!text) return
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      return
    }
    onSkillForSend()
    if (activeNav.value === 'reading') {
      ElMessage.info('伴读功能开发中，敬请期待')
      return
    }
    if (activeNav.value !== 'help' && !isVip.value) {
      ElMessage.warning('写作与画图需 VIP；站点帮助所有登录用户可用')
      return
    }
  
    if (estimatePoints.value != null && pointsWallet.balance < estimatePoints.value) {
      ElMessage.warning('积分余额不足，请先充值或赚取积分')
      return
    }
    const sid = sessionId.value || getSessionForNav(activeNav.value)
    loading.value = true
    const now = Date.now()
    messages.value.push({ role: 'user', content: text, type: 'text', at: now })
    draft.value = ''
  
    const history = messages.value.slice(0, -1).map((m) => ({
      role: m.role,
      content: m.content,
    }))
  
    const skill = activeNav.value === 'help' ? 'help' : (activeNav.value === 'writing' ? 'writing' : 'writing')
  
    try {
      if (activeNav.value === 'drawing') {
        const q = imageQuality.value === 'premium' && isVip.value ? 'premium' : 'normal'
        const res = await aiImage({ prompt: text, quality: q, sessionId: sessionId.value || getSessionForNav('drawing') })
        const data = res.data || {}
        const url = data.url || data.payload?.url
        if (!url) {
          throw new Error('image url missing')
        }
        messages.value.push({ role: 'assistant', type: 'image', url, at: Date.now() })
        if (data.sessionId) setSessionForNav('drawing', data.sessionId)
        if (data.pointsCost != null) {
          ElMessage.success(`已扣 ${data.pointsCost} 积分`)
        }
        const inst = oml2d.value
        if (inst?.tipsMessage) {
          inst.tipsMessage('生成完成', 3000, 1)
        }
        refreshEstimate()
        return
      }
  
      const res = await postMascotChat({
        message: text,
        sessionId: sid,
        mascotModelCode: activeCode.value,
        llmProvider: selectedLlm.value,
        skill,
        history,
      })
      const data = res.data || {}
      const reply = data.reply ?? ''
      messages.value.push({ role: 'assistant', content: reply, type: 'text', at: Date.now() })
      if (data.sessionId) setSessionForNav(activeNav.value, data.sessionId)
      if (data.pointsCost != null) {
        ElMessage.success(`已扣 ${data.pointsCost} 积分`)
      }
      refreshEstimate()
  
      const inst = oml2d.value
      if (inst?.tipsMessage && reply) {
        inst.tipsMessage(reply.length > 100 ? `${reply.slice(0, 100)}…` : reply, 4500, 1)
      }
    } catch (e) {
      messages.value.pop()
      draft.value = text
    } finally {
      loading.value = false
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
  
  onMounted(async () => {
    loadSavedOffset()
    await nextTick()
    await initOml2dStage()
  })
  
  onBeforeUnmount(() => {
    onStagePointerUp()
    clearOml2dStageHost()
  })

  return {
    DEFAULT_AVATAR,
    ALL_LLM_OPTIONS,
    FLASH_LLM,
    GUEST_MASCOT_CODE_KEY,
    IMAGE_QUALITY_KEY,
    LLM_HELP_KEY,
    LLM_WRITING_KEY,
    OFFSET_KEY,
    SCALE_KEY,
    STAGE_BASE_H,
    STAGE_BASE_W,
    activeCode,
    activeNav,
    activeSkill,
    applyAppearance,
    applyChip,
    applyStageScaleToLib,
    assistantOpen,
    buildModelsPayload,
    catalog,
    clearMessages,
    startNewSession,
    companionAvatarSrc,
    currentLlmStorageKey,
    draft,
    dragOffset,
    ensureSessionId,
    estimateLoading,
    estimatePoints,
    fetchCatalog,
    formatMsgTime,
    formatSessionTime,
    getSessionForNav,
    historyDrawerOpen,
    historyDrawerTitle,
    historyLoading,
    historySessions,
    imageQuality,
    initOml2dStage,
    inputPlaceholder,
    isVip,
    live2dAssetUrl,
    llmOptions,
    llmStorageKey,
    loadHistorySession,
    loadMessagesForNav,
    loadSavedOffset,
    loading,
    mapVoToMessages,
    mascotUi,
    messages,
    modeTabs,
    navToSkill,
    oml2d,
    onAssistantOpened,
    onPreviewPick,
    onScaleSliderChange,
    onSkillForSend,
    onStageLeave,
    onStagePointerDown,
    onStagePointerMove,
    onStagePointerUp,
    openHistoryDrawer,
    pendingCode,
    pointsWallet,
    quickChips,
    refreshEstimate,
    resolveInitialCode,
    ringVipTier,
    rootStyle,
    saveLlmPrefs,
    saveOffset,
    saveScale,
    scalePopoverOpen,
    scrollFsToBottom,
    scrollbarFs,
    selectNav,
    selectedLlm,
    selectedLlmHelp,
    selectedLlmWriting,
    send,
    sessionId,
    sessionKeyForNav,
    setSessionForNav,
    skillSessionIds,
    stageHost,
    stageHovered,
    stageScale,
    stageUseFallback,
    stageWrapStyle,
    uiLabels,
    userStore,
  }
}
