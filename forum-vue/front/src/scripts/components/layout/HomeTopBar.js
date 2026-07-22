import { Coin, Search, Setting } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { SITE_NAME as siteName } from '@/constants/site'
import { useHomeShellContext } from '@/composables/useHomeShell'

const {
  aiSearchMode,
  defaultAvatar,
  goPoints,
  goSettings,
  handleLogout,
  pointsBalance,
  searchInputPlaceholder,
  searchQuery,
  submitSearch,
  toggleAiSearchMode,
  userStore,
} = useHomeShellContext()
