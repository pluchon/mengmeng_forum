import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Close, Loading, MoreFilled, TrendCharts } from '@element-plus/icons-vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import InterestPreferenceDialog from '@/components/recommendation/InterestPreferenceDialog.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import { useBoardStore } from '@/stores/board'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { useHomeMasonry } from '@/composables/useHomeMasonry'
import { restoreFeedScroll } from '@/utils/feedScrollRestore'
import { captureFeedCardOrigin, captureFeedOpenFrom } from '@/utils/feedNavigation'
import { isQuestionArticle, questionStatusClass, questionStatusLabel } from '@/utils/articleQuestion'

defineOptions({ name: 'HomeFeed' })

const route = useRoute()
const router = useRouter()
const boardStore = useBoardStore()

const {
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
  selectCategoryMenu,
  selectHomeBoard,
  showCategoryNavigator,
  showCheckinHomeStrip,
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

const feedList = computed(() => articleList.value)

const openCategoryId = ref(null)
let categoryCloseTimer = null

const { containerRef: masonryRef, columns: masonryColumns } = useHomeMasonry(feedList, {
  columnWidth: 200,
  gap: 16,
})

function handleDismissCheckin() {
  dismissCheckinHomeStrip()
}

function openArticle(entry, event) {
  const id = entry?.article?.id
  if (!id) return
  const card = event?.currentTarget?.closest?.('.home-masonry-item') || event?.currentTarget
  if (card) captureFeedCardOrigin(id, card)
  captureFeedOpenFrom(route.path)
  router.push(`/article/${id}`)
}

function formatCardNickname(nickname) {
  const characters = Array.from(String(nickname || ''))
  if (characters.length <= 5) return characters.join('')
  return `${characters.slice(0, 5).join('')}…`
}

function categoryTriggerLabel(item) {
  if (Number(activeCategoryId.value) !== Number(item?.category?.id)) {
    return item?.category?.name || ''
  }
  const selectedBoard = (item?.boardList || [])
    .find(board => Number(board?.id) === Number(currentBoardId.value))
  return selectedBoard?.name || item?.category?.name || ''
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
