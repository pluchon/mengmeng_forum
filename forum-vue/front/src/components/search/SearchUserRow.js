import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { DEFAULT_AVATAR } from '@/utils/constants'

const props = defineProps({
  user: {
    type: Object,
    required: true,
  },
  saving: Boolean,
  isSelf: Boolean,
})

const emit = defineEmits(['open', 'toggle-follow'])
const defaultAvatar = DEFAULT_AVATAR

function emitOpen() {
  emit('open', props.user)
}

function emitToggleFollow() {
  emit('toggle-follow', props.user)
}
