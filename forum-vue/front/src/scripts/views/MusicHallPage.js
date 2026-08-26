import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MusicHall from '@/components/article/MusicHall.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import VipSubscribeDialog from '@/components/vip/VipSubscribeDialog/VipSubscribeDialog.vue'
import iconMusicHall from '@/assets/svg/14_music_hall.svg'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useVipStatusEntry } from '@/composables/useVipStatusEntry'
import '@/assets/styles/vip-status-pill.css'

const route = useRoute()

const { defaultAvatar, goProfile, userStore } = useHomeShellContext()

const hallTab = computed(() => (route.name === 'musicHallMine' ? 'mine' : 'discover'))

const {
  vipDialogVisible,
  vipStatusIcon,
  vipStatusLabel,
  vipStatusPillClass,
  openVipPurchase,
} = useVipStatusEntry(userStore)
