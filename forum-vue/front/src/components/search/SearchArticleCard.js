import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { isQuestionArticle, questionStatusClass, questionStatusLabel } from '@/utils/articleQuestion'
import { captureVideoFirstFrame, onFeedRestoreCover } from '@/utils/feedNavigation'

const props = defineProps({
  entry: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['open'])
const defaultAvatar = DEFAULT_AVATAR
const article = computed(() => props.entry?.article || {})
const baseCoverUrl = computed(() => String(article.value.coverImg || '').trim())
const firstGalleryUrl = computed(() => String(props.entry?.firstImageUrl || '').trim())
const previewUrl = ref('')
const hoverToken = ref(0)
const coverAspect = ref('')
const coverImageUrl = computed(() => previewUrl.value || baseCoverUrl.value)
const coverLoaded = ref(false)
const coverFailed = ref(false)
const coverAspectStyle = computed(() => (
  coverAspect.value ? { '--cover-aspect': coverAspect.value } : undefined
))
const hasCoverImage = computed(() => Boolean(coverImageUrl.value) && !coverFailed.value)
const isQuestion = computed(() => isQuestionArticle(article.value))
const isVideo = computed(() => Number(article.value.mediaType) === 1 || Boolean(String(article.value.videoUrl || '').trim()))
const shortTitle = computed(() => String(article.value.title || '').substring(0, 12))
const displayNickname = computed(() => {
  const chars = Array.from(String(props.entry?.user?.nickname || '匿名用户'))
  return chars.length <= 5 ? chars.join('') : `${chars.slice(0, 5).join('')}…`
})
const placeholderStyle = computed(() => {
  const seed = Number(article.value.id) || 0
  const hues = [0, 200, 330, 260, 160]
  return {
    background: `hsl(${hues[Math.abs(seed) % hues.length]}, 70%, 92%)`,
    minHeight: `${160 + (Math.abs(seed) % 5) * 36}px`,
  }
})

watch(baseCoverUrl, () => {
  coverLoaded.value = false
  coverFailed.value = false
  previewUrl.value = ''
  coverAspect.value = ''
}, { immediate: true })

let stopRestoreListen = null

onMounted(() => {
  stopRestoreListen = onFeedRestoreCover((detail) => {
    if (String(detail?.articleId) !== String(article.value.id)) return
    hoverToken.value += 1
    previewUrl.value = ''
  })
})

onUnmounted(() => {
  if (typeof stopRestoreListen === 'function') stopRestoreListen()
})

function markCoverLoaded(event) {
  coverLoaded.value = true
  if (previewUrl.value || coverAspect.value) return
  const img = event?.target
  const w = Number(img?.naturalWidth) || 0
  const h = Number(img?.naturalHeight) || 0
  if (w > 0 && h > 0) {
    coverAspect.value = `${w} / ${h}`
  }
}

function handleCoverError() {
  coverFailed.value = true
}

async function onCoverHoverEnter() {
  const token = hoverToken.value + 1
  hoverToken.value = token
  if (isVideo.value) {
    const videoUrl = String(article.value.videoUrl || '').trim()
    if (!videoUrl) return
    const frame = await captureVideoFirstFrame(videoUrl)
    if (hoverToken.value !== token) return
    if (frame) {
      previewUrl.value = frame
      coverFailed.value = false
    }
    return
  }
  const first = firstGalleryUrl.value
  if (!first || first === baseCoverUrl.value) return
  const ok = await new Promise((resolve) => {
    const img = new Image()
    img.onload = () => resolve(true)
    img.onerror = () => resolve(false)
    img.src = first
  })
  if (hoverToken.value !== token || !ok) return
  previewUrl.value = first
  coverFailed.value = false
}

function onCoverHoverLeave() {
  hoverToken.value += 1
  previewUrl.value = ''
}

function emitOpen(event) {
  emit('open', props.entry, event, {
    previewUrl: coverImageUrl.value,
    restoreCoverUrl: baseCoverUrl.value,
  })
}
