import { Search, Setting } from '@element-plus/icons-vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import VipSubscribeDialog from '@/components/vip/VipSubscribeDialog/VipSubscribeDialog.vue'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useVipStatusEntry } from '@/composables/useVipStatusEntry'
import '@/assets/styles/vip-status-pill.css'

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

const {
  vipDialogVisible,
  vipStatusIcon,
  vipStatusLabel,
  vipStatusPillClass,
  openVipPurchase,
} = useVipStatusEntry(userStore)
