import {
  Bell,
  Calendar,
  Compass,
  EditPen,
  HomeFilled,
  Medal,
  Message,
  Opportunity,
  Present,
  ShoppingBag,
  Trophy,
  User,
} from '@element-plus/icons-vue'

import ThemeModeSwitch from '@/components/layout/ThemeModeSwitch.vue'
import { useHomeShellContext } from '@/composables/useHomeShell'

const {
  goCheckin,
  goLottery,
  goProfile,
  goToCreative,
  menuActiveKey,
  msgUnread,
  openMessageCenter,
  selectCategoryMenu,
  showAnnouncement,
} = useHomeShellContext()
