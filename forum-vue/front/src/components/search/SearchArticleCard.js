import { computed, ref, watch } from 'vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { isQuestionArticle, questionStatusClass, questionStatusLabel } from '@/utils/articleQuestion'
import { ossAvatarUrl, ossFeedCoverUrl } from '@/utils/ossImageStyle'

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
const coverAspect = ref('')
const coverImageUrl = computed(() => baseCoverUrl.value)
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
  coverAspect.value = ''
}, { immediate: true })

function markCoverLoaded(event) {
  coverLoaded.value = true
  if (coverAspect.value) return
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

function emitOpen(event) {
  emit('open', props.entry, event, { previewUrl: baseCoverUrl.value })
}
