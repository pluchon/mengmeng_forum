import {
  Calendar,
  Compass,
  EditPen,
  HomeFilled,
  Medal,
  Notebook,
  Present,
  ShoppingBag,
  TrendCharts,
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
