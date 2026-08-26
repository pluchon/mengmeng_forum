import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import TopTitleDialog from '@/components/dialog/TopTitleDialog.vue'

const BANNER_ASPECT = 25 / 6
const OUTPUT_WIDTH = 1250
const OUTPUT_HEIGHT = 300

const emit = defineEmits(['confirm'])

const visible = ref(false)
const mode = ref('view')
const imageSrc = ref('')
const uploading = ref(false)
const viewportRef = ref(null)
const naturalWidth = ref(0)
const naturalHeight = ref(0)
const displayScale = ref(1)
const imageOffsetX = ref(0)
const imageOffsetY = ref(0)
const cropX = ref(0)
const cropY = ref(0)
const cropW = ref(0)
const cropH = ref(0)
const dragging = ref(false)

let objectUrl = ''
let dragStart = { x: 0, y: 0, cx: 0, cy: 0 }
let resizeObserver = null

const dialogTitle = computed(() => (mode.value === 'crop' ? '裁剪背景图' : '查看背景图'))
const showClose = computed(() => mode.value === 'view')
const showFooter = computed(() => mode.value === 'crop')

const imageDisplayStyle = computed(() => ({
  width: `${naturalWidth.value * displayScale.value}px`,
  height: `${naturalHeight.value * displayScale.value}px`,
  left: `${imageOffsetX.value}px`,
  top: `${imageOffsetY.value}px`,
}))

const cropBoxStyle = computed(() => ({
  width: `${cropW.value}px`,
  height: `${cropH.value}px`,
  left: `${cropX.value}px`,
  top: `${cropY.value}px`,
}))

watch(visible, (open) => {
  if (!open) {
    revokeObjectUrl()
    dragging.value = false
    detachResizeObserver()
    return
  }
  if (mode.value === 'crop') {
    nextTick(() => {
      layoutCrop()
      attachResizeObserver()
    })
  }
})

function attachResizeObserver() {
  detachResizeObserver()
  const viewport = viewportRef.value
  if (!viewport || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => layoutCrop())
  resizeObserver.observe(viewport)
}

function detachResizeObserver() {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
}

function revokeObjectUrl() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

function openView(url) {
  mode.value = 'view'
  revokeObjectUrl()
  imageSrc.value = String(url || '').trim()
  naturalWidth.value = 0
  naturalHeight.value = 0
  visible.value = Boolean(imageSrc.value)
}

function openCrop(file) {
  if (!file) return
  mode.value = 'crop'
  revokeObjectUrl()
  objectUrl = URL.createObjectURL(file)
  imageSrc.value = objectUrl
  visible.value = true
  nextTick(() => prepareCropImage())
}

function prepareCropImage() {
  if (!imageSrc.value) return
  const img = new Image()
  img.onload = () => {
    naturalWidth.value = img.naturalWidth
    naturalHeight.value = img.naturalHeight
    nextTick(() => layoutCrop())
  }
  img.onerror = () => {
    naturalWidth.value = 0
    naturalHeight.value = 0
  }
  img.src = imageSrc.value
}

function layoutCrop() {
  const viewport = viewportRef.value
  if (!viewport || !naturalWidth.value || !naturalHeight.value) return
  const vw = viewport.clientWidth
  const vh = viewport.clientHeight
  if (!vw || !vh) return

  displayScale.value = Math.min(vw / naturalWidth.value, vh / naturalHeight.value)
  const imgW = naturalWidth.value * displayScale.value
  const imgH = naturalHeight.value * displayScale.value
  imageOffsetX.value = (vw - imgW) / 2
  imageOffsetY.value = (vh - imgH) / 2

  let nextW = imgW
  let nextH = nextW / BANNER_ASPECT
  if (nextH > imgH) {
    nextH = imgH
    nextW = nextH * BANNER_ASPECT
  }
  cropW.value = nextW
  cropH.value = nextH
  cropX.value = imageOffsetX.value + (imgW - nextW) / 2
  cropY.value = imageOffsetY.value + (imgH - nextH) / 2
}

function clampCropBox() {
  const minX = imageOffsetX.value
  const minY = imageOffsetY.value
  const maxX = imageOffsetX.value + naturalWidth.value * displayScale.value - cropW.value
  const maxY = imageOffsetY.value + naturalHeight.value * displayScale.value - cropH.value
  cropX.value = Math.min(maxX, Math.max(minX, cropX.value))
  cropY.value = Math.min(maxY, Math.max(minY, cropY.value))
}

function onCropPointerDown(event) {
  if (mode.value !== 'crop') return
  dragging.value = true
  dragStart = {
    x: event.clientX,
    y: event.clientY,
    cx: cropX.value,
    cy: cropY.value,
  }
  event.currentTarget.setPointerCapture(event.pointerId)
}

function onCropPointerMove(event) {
  if (!dragging.value || mode.value !== 'crop') return
  cropX.value = dragStart.cx + (event.clientX - dragStart.x)
  cropY.value = dragStart.cy + (event.clientY - dragStart.y)
  clampCropBox()
}

function onCropPointerUp(event) {
  dragging.value = false
  try {
    event.currentTarget.releasePointerCapture(event.pointerId)
  } catch {
    // 忽略
  }
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })
}

async function buildCropBlob() {
  if (!imageSrc.value || !cropW.value || !cropH.value) return null
  const canvas = document.createElement('canvas')
  canvas.width = OUTPUT_WIDTH
  canvas.height = OUTPUT_HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) return null

  const sx = (cropX.value - imageOffsetX.value) / displayScale.value
  const sy = (cropY.value - imageOffsetY.value) / displayScale.value
  const sw = cropW.value / displayScale.value
  const sh = cropH.value / displayScale.value
  const img = await loadImage(imageSrc.value)
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT)
  return new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), 'image/jpeg', 0.92)
  })
}

async function confirmCrop() {
  if (uploading.value || mode.value !== 'crop') return
  uploading.value = true
  try {
    const blob = await buildCropBlob()
    if (!blob) return
    emit('confirm', blob)
    visible.value = false
  } finally {
    uploading.value = false
  }
}

function closeDialog() {
  visible.value = false
}

function onVisibleChange(val) {
  visible.value = val
}

onBeforeUnmount(() => {
  detachResizeObserver()
  revokeObjectUrl()
})

defineExpose({
  openView,
  openCrop,
  BANNER_ASPECT,
})
