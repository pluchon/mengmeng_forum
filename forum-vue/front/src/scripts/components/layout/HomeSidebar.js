import { ref } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'
import HotRankingDialog from '@/components/home/HotRankingDialog.vue'
import { SITE_NAME as siteName } from '@/constants/site'
import { LOGIN_TITLE_WEBP_URL as loginTitleUrl } from '@/utils/clientOss'
import { useHomeShellContext } from '@/composables/useHomeShell'

import iconHome from '@/assets/svg/01_home.svg'
import iconRecommend from '@/assets/svg/02_recommend.svg'
import iconMessages from '@/assets/svg/04_messages.svg'
import iconProfile from '@/assets/svg/05_profile.svg'
import iconGameCenter from '@/assets/svg/06_game_center.svg'
import iconMusicHall from '@/assets/svg/14_music_hall.svg'
import iconPointsShop from '@/assets/svg/09_points_shop.svg'
import iconCreationCenter from '@/assets/svg/11_creation_center.svg'
import iconCheckin from '@/assets/svg/12_checkin.svg'
import iconPointsLottery from '@/assets/svg/13_points_lottery.svg'

const brandTitleFailed = ref(false)
const hotRankingDialogVisible = ref(false)

const {
  goCheckin,
  goLottery,
  goMusicHall,
  goProfile,
  goToCreative,
  sidebarMenuActive,
  msgUnread,
  openMessageCenter,
  selectCategoryMenu,
} = useHomeShellContext()

function openHotRankingDialog() {
  hotRankingDialogVisible.value = true
}
