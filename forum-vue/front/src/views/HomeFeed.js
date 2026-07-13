import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Loading, MoreFilled, TrendCharts } from '@element-plus/icons-vue'
import PawCoinIcon from '@/components/common/PawCoinIcon.vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import FollowingBadge from '@/components/common/FollowingBadge.vue'
import { useBoardStore } from '@/stores/board'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useHomeMasonry } from '@/composables/useHomeMasonry'
import { restoreFeedScroll } from '@/utils/feedScrollRestore'
import { captureFeedCardOrigin, captureFeedOpenFrom } from '@/utils/feedNavigation'
import {
  QUESTION_FILTER,
  isQuestionArticle,
  questionStatusClass,
  questionStatusLabel,
} from '@/utils/articleQuestion'

defineOptions({ name: 'HomeFeed' })

const route = useRoute()
const router = useRouter()
const boardStore = useBoardStore()

const {
  CircleCheck,
  Close,
  articleList,
  activeCategoryId,
  categoriesWithId,
  checkinSummary,
  coverImageUrl,
  currentBoardId,
  defaultAvatar,
  dismissCheckinHomeStrip,
  feedError,
  feedForbidden,
  ensureHomeFeedLoaded,
  fetchArticles,
  fetchHomeHotList,
  getRandomPastel,
  hideRecommendedArticle,
  homeHotList,
  homeHotTotal,
  homeHotLoading,
  homeHotCollapsed,
  homeHotPage,
  homeHotPageSize,
  isHomeFeed,
  isRecommendationFeed,
  loading,
  pageNum,
  pageSize,
  placeholderMinHeight,
  questionFilter,
  selectCategoryMenu,
  selectHomeBoard,
  selectQuestionFilter,
  showCategoryNavigator,
  showCheckinHomeStrip,
  showQuestionFilters,
  total,
  toggleHomeHotCollapsed,
  openRecommendationPreferences,
  recommendationDialogVisible,
  recommendationDraftBoardIds,
  recommendationSaving,
  saveRecommendationPreferences,
  showRecommendationInterestMask,
  userStore,
} = useHomeShellContext()

const questionFilterOptions = [
  { value: QUESTION_FILTER.ALL, label: '全部内容' },
  { value: QUESTION_FILTER.QUESTION, label: '问答' },
  { value: QUESTION_FILTER.WAITING, label: '待解决' },
  { value: QUESTION_FILTER.RESOLVED, label: '已解决' },
]

const feedList = computed(() => articleList.value)

const openCategoryId = ref(null)
let categoryCloseTimer = null

const { containerRef: masonryRef, columns: masonryColumns } = useHomeMasonry(feedList, {
  columnWidth: 220,
  gap: 16,
})

function openArticle(entry, event) {
  const id = entry?.article?.id
  if (!id) return
  const card = event?.currentTarget?.closest?.('.home-masonry-item') || event?.currentTarget
  if (card) captureFeedCardOrigin(id, card)
  captureFeedOpenFrom(route.path)
  router.push(`/article/${id}`)
}

function openCategory(categoryId) {
  if (categoryCloseTimer) clearTimeout(categoryCloseTimer)
  openCategoryId.value = categoryId
}

function scheduleCloseCategory() {
  if (categoryCloseTimer) clearTimeout(categoryCloseTimer)
  categoryCloseTimer = window.setTimeout(() => {
    openCategoryId.value = null
  }, 180)
}

function toggleCategory(categoryId) {
  openCategoryId.value = openCategoryId.value === categoryId ? null : categoryId
}

function handleCategoryFocusOut(event) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  scheduleCloseCategory()
}

onMounted(async () => {
  if (boardStore.categoryList.length === 0) await boardStore.fetchCategoryList()
  await ensureHomeFeedLoaded()
})

onActivated(() => {
  restoreFeedScroll()
})

onUnmounted(() => {
  if (categoryCloseTimer) clearTimeout(categoryCloseTimer)
})
