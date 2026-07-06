import {
  Calendar,
  ChatDotRound,
  Compass,
  EditPen,
  Food,
  Location,
  Medal,
  Notebook,
  Present,
  ShoppingBag,
  TrendCharts,
  Trophy,
  User,
} from '@element-plus/icons-vue'

import { useHomeShellContext } from '@/composables/useHomeShell'

const CATEGORY_ICONS = [Location, Trophy, ShoppingBag, Food, Compass]

function categoryIcon(idx) {
  return CATEGORY_ICONS[idx % CATEGORY_ICONS.length]
}

const {
  categoriesWithId,
  goCheckin,
  goLottery,
  goProfile,
  goToCreative,
  menuActiveKey,
  selectCategoryMenu,
  userStore,
} = useHomeShellContext()
