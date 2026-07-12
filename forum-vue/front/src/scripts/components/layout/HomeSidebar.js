import {
  Calendar,
  Compass,
  EditPen,
  HomeFilled,
  Medal,
  Opportunity,
  Present,
  ShoppingBag,
  Trophy,
  User,
} from '@element-plus/icons-vue'

import { useHomeShellContext } from '@/composables/useHomeShell'

const {
  goCheckin,
  goLottery,
  goProfile,
  goToCreative,
  menuActiveKey,
  selectCategoryMenu,
  userStore,
} = useHomeShellContext()
