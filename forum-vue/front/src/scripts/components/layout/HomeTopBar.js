import { computed } from 'vue'
import { Coin, Message, Mouse, Search, Setting } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import MessageIncomingBubble from '@/components/layout/MessageIncomingBubble.vue'
import { SITE_NAME as siteName } from '@/constants/site'
import { useHomeShellContext } from '@/composables/useHomeShell'

const mascotPassthroughTip = computed(() =>
  mascotUi.pointerPassThrough ? '关闭看板娘穿透' : '看板娘鼠标穿透',
)

const {
  aiSearchMode,
  defaultAvatar,
  goPoints,
  goSettings,
  handleLogout,
  mascotUi,
  msgUnread,
  openMessageCenter,
  pointsBalance,
  searchInputPlaceholder,
  searchQuery,
  showAnnouncement,
  submitSearch,
  toggleAiSearchMode,
  toggleMascotPassthrough,
  userStore,
} = useHomeShellContext()
