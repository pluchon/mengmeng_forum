defineOptions({ name: 'HomeFeed' })

import { computed, onActivated, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import FollowingBadge from '@/components/common/FollowingBadge.vue'
import { useBoardStore } from '@/stores/board'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useHomeMasonry } from '@/composables/useHomeMasonry'
import { restoreFeedScroll } from '@/utils/feedScrollRestore'

import { captureFeedCardOrigin, captureFeedOpenFrom } from '@/utils/feedNavigation'

const route = useRoute()
const router = useRouter()
const boardStore = useBoardStore()

const {
  CircleCheck,
  Close,
  articleList,
  boardsInCategory,
  checkinSummary,
  coverImageUrl,
  currentBoardId,
  defaultAvatar,
  dismissCheckinHomeStrip,
  ensureHomeFeedLoaded,
  fetchArticles,
  getRandomPastel,
  hotFeedList,
  isHotFeed,
  loading,
  pageNum,
  pageSize,
  placeholderMinHeight,
  selectBoardPill,
  showBoardPillsRow,
  showCheckinHomeStrip,
  total,
} = useHomeShellContext()
