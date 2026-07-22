import { ref, computed, nextTick } from 'vue'
import { generateCaptcha, checkCaptcha } from '@/api/captcha'
import { ElMessage } from 'element-plus'
import {
  Grid,
  DArrowRight,
  RefreshRight,
  ChatLineRound,
  WarningFilled,
  Loading,
} from '@element-plus/icons-vue'

// ─────────────────────── 常量 ───────────────────────────────────────
const BTN_W = 40
/** 松开时与目标 X 偏差容差（px），在此范围自动提交 */
const SNAP_TOLERANCE = 30
/** 前端 mode → 天爱 type 字符串（全大写） */
const MODE_TO_API_TYPE = {
  slider: 'SLIDER',
  click: 'WORD_IMAGE_CLICK',
}
/** 天爱 captchaType → 前端 mode */
const API_TYPE_TO_MODE = {
  SLIDER: 'slider',
  ROTATE: 'slider',
  CONCAT: 'slider',
  WORD_IMAGE_CLICK: 'click',
}

// ─────────────────────── 通用状态 ───────────────────────────────────
const visible = ref(false)
const submitting = ref(false)
const errorMsg = ref('')
const vo = ref(null)
const mode = ref('slider')
let resolvePromise = null
let rejectPromise = null
let settled = false

// ─────────────────────── 滑块状态 ───────────────────────────────────
const bgWrapRef = ref(null)
const bgImgRef = ref(null)
const tplRef = ref(null)
const dragX = ref(0)
const dragY = ref(0)
const bgW = ref(300)
const bgH = ref(180)
const tplW = ref(60)
/** 滑块最大可拖动距离 */
const maxDx = ref(200)
const dragging = ref(false)

let trackList = []
let startMs = 0
let _pointerId = null
let startClientX = 0
let originDragX = 0

const trackWidth = computed(() =>
  Math.max(0, Math.min(dragX.value + BTN_W / 2, maxDx.value + BTN_W)),
)

// ─────────────────────── 点击文字状态 ─────────────────────────────────
const clickImgRef = ref(null)
/**
 * 需点击的文字数量。
 * 天爱 WORD_IMAGE_CLICK: templateImage = 提示图；理想情况下 data 为校验定义数组，长度即点击次数。
 * 若 data 未下发（常为 null），天爱标准生成器默认 checkClickCount = 4，误用 3 会在第 3 次点击就自动提交并校验失败。
 */
const clickCount = computed(() => {
  const d = vo.value?.data
  if (Array.isArray(d) && d.length > 0) return d.length
  return 4
})
/** 已点击记录：{ px, py, x, y }  px/py=显示坐标, x/y=像素坐标（传后端） */
const clickDots = ref([])
let clickImgW = 0
let clickImgH = 0

// ─────────────────────── 工具函数 ────────────────────────────────────
function toCaptchaImgSrc(raw) {
  if (!raw) return ''
  const s = String(raw).trim()
  if (s.startsWith('data:') || s.startsWith('http') || s.startsWith('blob:')) return s
  return `data:image/png;base64,${s}`
}

const bgSrc = computed(() => toCaptchaImgSrc(vo.value?.backgroundImage))
const tplSrc = computed(() => toCaptchaImgSrc(vo.value?.templateImage))

function isApiSuccess(res) {
  if (!res) return false
  return res.code === 200 || res.code === 0
}

// ─────────────────────── 滑块方法 ────────────────────────────────────
function resetSlider() {
  dragX.value = 0
  dragY.value = 0
  dragging.value = false
  trackList = []
  startMs = 0
  _pointerId = null
  startClientX = 0
  originDragX = 0
  maxDx.value = 200
}

function applyTemplateOffset() {
  const d = vo.value?.data
  if (!d || typeof d !== 'object' || Array.isArray(d)) return
  const ty = d.jigsawTop ?? d.top ?? d.templateTop ?? d.y
  if (ty != null) dragY.value = Number(ty) || 0
}

/** 等图片 onload 后在 nextTick 里重算尺寸（确保 naturalWidth 可用） */
function onBgLoad() {
  nextTick(() => {
    const bgEl = bgImgRef.value
    if (bgEl) {
      bgW.value = bgEl.clientWidth || vo.value?.backgroundImageWidth || 300
      bgH.value = bgEl.clientHeight || vo.value?.backgroundImageHeight || 180
    }
    // 计算缩放后的模板宽度：模板需按背景图的缩放比例同步缩小
    const origBgW = vo.value?.backgroundImageWidth || bgW.value
    const origTplW = vo.value?.templateImageWidth || 60
    const scale = bgW.value / origBgW
    // scaledTplW: 模板图在当前容器里应该显示的像素宽
    const scaledTplW = Math.round(origTplW * scale)
    tplW.value = scaledTplW
    maxDx.value = Math.max(40, bgW.value - scaledTplW - 4)
  })
}

function pushTrack(type, x, y) {
  const t = startMs ? (Date.now() - startMs) / 1000 : 0
  trackList.push({ x: parseFloat(x.toFixed(1)), y: parseFloat(y.toFixed(1)), t, type })
}

function onHandlePointerDown(e) {
  if (!vo.value) return
  e.preventDefault()
  dragging.value = true
  _pointerId = e.pointerId
  startMs = Date.now()
  startClientX = e.clientX
  originDragX = dragX.value
  trackList = []
  pushTrack('down', dragX.value, dragY.value)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp, { once: true })
  try { e.target.setPointerCapture(e.pointerId) } catch { /**/ }
}

function onPointerMove(e) {
  if (!dragging.value || e.pointerId !== _pointerId) return
  const dx = e.clientX - startClientX
  dragX.value = Math.max(0, Math.min(maxDx.value, originDragX + dx))
  pushTrack('move', dragX.value, dragY.value)
}

async function onPointerUp(e) {
  if (e.pointerId !== _pointerId) return
  dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  pushTrack('up', dragX.value, dragY.value)
  // 自动提交：松开时总是尝试验证（让后端校验是否对准）
  await submitSlider()
}

function failAndClose(message) {
  if (message) ElMessage.error(message)
  settled = true
  submitting.value = false
  dragging.value = false
  vo.value = null
  errorMsg.value = ''
  resetSlider()
  resetClick()
  visible.value = false
  rejectPromise?.(new Error('failed'))
  rejectPromise = null
  resolvePromise = null
}

async function submitSlider() {
  if (!vo.value || submitting.value) return
  submitting.value = true
  try {
    const stopTime = Date.now()
    // 将显示像素坐标换算为原始图坐标（天爱后端按原始尺寸校验）
    const origBgW = vo.value.backgroundImageWidth || bgW.value
    const origBgH = vo.value.backgroundImageHeight || bgH.value
    const scaleX = origBgW / bgW.value
    const scaleY = origBgH / bgH.value
    const origLeft = Math.round(dragX.value * scaleX)
    const origTop = Math.round(dragY.value * scaleY)
    // trackList 坐标也换算
    const scaledTrack = trackList.map(t => ({
      x: parseFloat((t.x * scaleX).toFixed(1)),
      y: parseFloat((t.y * scaleY).toFixed(1)),
      t: t.t,
      type: t.type,
    }))
    const data = {
      bgImageWidth: origBgW,
      bgImageHeight: origBgH,
      templateImageWidth: vo.value.templateImageWidth ?? 60,
      templateImageHeight: vo.value.templateImageHeight ?? 60,
      startTime: startMs || stopTime,
      stopTime,
      left: origLeft,
      top: origTop,
      trackList: scaledTrack,
      data: vo.value.data,
    }
    let res
    try {
      res = await checkCaptcha({ id: vo.value.id, purpose: purposeRef.value, data })
    } catch (err) {
      failAndClose(err?.message || '验证失败，请重试')
      return
    }
    if (res.code !== 0 || !res.data?.captchaTicket) {
      failAndClose(res.message || '验证失败，请重试')
      return
    }
    resolveWithTicket(res.data.captchaTicket)
  } finally {
    submitting.value = false
  }
}

// parseClickWords 已不再需要，天爱的提示文字通过 templateImage 图片展示

function resetClick() {
  clickDots.value = []
}

function onClickBgLoad() {
  nextTick(() => {
    const el = clickImgRef.value?.querySelector('.cap-bg-img')
    if (el) {
      clickImgW = el.clientWidth || 300
      clickImgH = el.clientHeight || 180
    }
  })
}

async function onClickImage(e) {
  if (submitting.value) return
  if (clickDots.value.length >= clickCount.value) return

  const rect = e.currentTarget.getBoundingClientRect()
  const px = e.clientX - rect.left
  const py = e.clientY - rect.top
  const imgW = clickImgW || rect.width
  const imgH = clickImgH || rect.height

  // 换算成图片原始像素坐标
  const origW = vo.value?.backgroundImageWidth || imgW
  const origH = vo.value?.backgroundImageHeight || imgH
  const x = Math.round((px / imgW) * origW)
  const y = Math.round((py / imgH) * origH)

  clickDots.value.push({ px, py, x, y })

  // 点满自动提交
  if (clickDots.value.length >= clickCount.value) {
    await submitClickTrack()
  }
}

async function submitClickTrack() {
  if (!vo.value || submitting.value) return
  submitting.value = true
  try {
    const now = Date.now()
    // trackList 格式：每次点击是一个 Track 对象，type='click'，x/y 为图片像素坐标
    const tList = clickDots.value.map((dot, i) => ({
      x: parseFloat(dot.x.toFixed(1)),
      y: parseFloat(dot.y.toFixed(1)),
      t: parseFloat((i * 0.5).toFixed(1)),
      type: 'click',
    }))

    const data = {
      bgImageWidth: vo.value.backgroundImageWidth || clickImgW,
      bgImageHeight: vo.value.backgroundImageHeight || clickImgH,
      templateImageWidth: 0,
      templateImageHeight: 0,
      startTime: now - tList.length * 500,
      stopTime: now,
      left: 0,
      top: 0,
      trackList: tList,
      data: vo.value.data,
    }
    let res
    try {
      res = await checkCaptcha({ id: vo.value.id, purpose: purposeRef.value, data })
    } catch (err) {
      failAndClose(err?.message || '验证失败，请重试')
      return
    }
    if (res.code !== 0 || !res.data?.captchaTicket) {
      failAndClose(res.message || '验证失败，请重试')
      return
    }
    resolveWithTicket(res.data.captchaTicket)
  } finally {
    submitting.value = false
  }
}

// ─────────────────────── 通用流程 ────────────────────────────────────
const purposeRef = ref('')

function switchMode(newMode) {
  mode.value = newMode
  loadVo()
}

async function loadVo() {
  errorMsg.value = ''
  vo.value = null
  resetSlider()
  resetClick()
  try {
    const apiType = MODE_TO_API_TYPE[mode.value] || 'SLIDER'
    const res = await generateCaptcha({ type: apiType })
    if (!isApiSuccess(res)) {
      errorMsg.value = res?.msg || res?.message || '加载验证码失败'
      return
    }
    vo.value = res.data
    // 后端实际返回的类型可能与请求不同，根据 type 字段同步前端渲染模式
    // ImageCaptchaVO.type 就是生成时用的 captcha 类型字符串（如 "SLIDER"/"WORD_IMAGE_CLICK"）
    const retType = res.data?.type
    if (retType && API_TYPE_TO_MODE[retType]) {
      mode.value = API_TYPE_TO_MODE[retType]
    }
    if (mode.value === 'slider') {
      applyTemplateOffset()
    }
    // click 模式不需要额外初始化：clickCount 是 computed，tplSrc 是提示图
  } catch (err) {
    errorMsg.value = err?.message || '加载验证码失败'
  }
}

function resolveWithTicket(ticket) {
  settled = true
  rejectPromise = null
  resolvePromise?.(ticket)
  resolvePromise = null
  visible.value = false
}

function onDialogClosed() {
  if (settled) return
  settled = true
  rejectPromise?.(new Error('cancelled'))
  rejectPromise = null
  resolvePromise = null
}

function run(purpose) {
  purposeRef.value = purpose
  settled = false
  // 随机选择验证模式
  mode.value = Math.random() < 0.5 ? 'slider' : 'click'
  return new Promise((resolve, reject) => {
    resolvePromise = resolve
    rejectPromise = reject
    visible.value = true
    loadVo().catch(err => {
      ElMessage.error(err?.message || '加载失败')
      reject(err)
    })
  })
}

defineExpose({ run })
