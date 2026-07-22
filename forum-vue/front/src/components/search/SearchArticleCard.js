import { computed } from 'vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { isQuestionArticle, questionStatusClass, questionStatusLabel } from '@/utils/articleQuestion'

const props = defineProps({
  entry: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['open'])
const defaultAvatar = DEFAULT_AVATAR
const article = computed(() => props.entry?.article || {})
const coverImageUrl = computed(() => article.value.coverImg || '')
const isQuestion = computed(() => isQuestionArticle(article.value))
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

function emitOpen() {
  emit('open', props.entry)
}
