import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { isEmojiShopMediaUrl } from '@/utils/chatMedia'
import CommentShopEmojiPopover from '@/components/article/CommentShopEmojiPopover.vue'

const REPLY_MEDIA_TYPE_IMAGE = 1
const REPLY_MEDIA_TYPE_SHOP_EMOJI = 2
const SCROLL_STEP = 116

const props = defineProps({
  mediaList: { type: Array, default: () => [] },
})

const emit = defineEmits(['open-shop'])

const trackRef = ref(null)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
let resizeObserver = null

const imagePreviewUrls = computed(() =>
  (props.mediaList || [])
    .filter((item) => isImageItem(item))
    .map((item) => item.mediaUrl),
)

const imageIndexMap = computed(() => {
  const map = {}
  let imageIdx = 0
  ;(props.mediaList || []).forEach((item, idx) => {
    if (isImageItem(item)) {
      map[idx] = imageIdx
      imageIdx += 1
    }
  })
  return map
})

function isImageItem(item) {
  return Number(item?.mediaType) === REPLY_MEDIA_TYPE_IMAGE && !isEmojiShopMediaUrl(item?.mediaUrl)
}

function updateScrollState() {
  const el = trackRef.value
  if (!el) {
    canScrollLeft.value = false
    canScrollRight.value = false
    return
  }
  const overflow = el.scrollWidth > el.clientWidth + 2
  canScrollLeft.value = overflow && el.scrollLeft > 2
  canScrollRight.value = overflow && el.scrollLeft + el.clientWidth < el.scrollWidth - 2
}

function scrollByDir(dir) {
  const el = trackRef.value
  if (!el) return
  el.scrollBy({ left: dir * SCROLL_STEP, behavior: 'smooth' })
}

function bindObserver() {
  resizeObserver?.disconnect()
  const el = trackRef.value
  if (!el || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => updateScrollState())
  resizeObserver.observe(el)
}

onMounted(async () => {
  await nextTick()
  updateScrollState()
  bindObserver()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

watch(
  () => props.mediaList,
  async () => {
    await nextTick()
    updateScrollState()
    bindObserver()
  },
  { deep: true },
)

defineExpose({
  imageIndexMap,
  imagePreviewUrls,
  isImageItem,
})
