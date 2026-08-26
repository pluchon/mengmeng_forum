import { nextTick, onMounted, onUnmounted, ref } from 'vue'

defineProps({
  // 标题 webp 地址
  src: { type: String, required: true },
  // 图片 alt / 无障碍文案
  alt: { type: String, required: true },
  // webp 失败时的加粗居中文字
  fallbackText: { type: String, required: true },
})

const emit = defineEmits(['ready'])

const failed = ref(false)
const imgRef = ref(null)
let settled = false
let timeoutTimer = null

function markReady() {
  if (settled) return
  settled = true
  if (timeoutTimer != null) {
    window.clearTimeout(timeoutTimer)
    timeoutTimer = null
  }
  emit('ready')
}

function handleLoad() {
  markReady()
}

function handleError() {
  failed.value = true
  markReady()
}

onMounted(async () => {
  timeoutTimer = window.setTimeout(() => {
    if (!settled) {
      handleError()
    }
  }, 12000)

  await nextTick()
  const el = imgRef.value
  if (!el) return
  // 浏览器缓存命中时可能不会再次触发 load
  if (el.complete) {
    if (el.naturalWidth > 0) {
      handleLoad()
    } else {
      handleError()
    }
  }
})

onUnmounted(() => {
  if (timeoutTimer != null) {
    window.clearTimeout(timeoutTimer)
    timeoutTimer = null
  }
})
