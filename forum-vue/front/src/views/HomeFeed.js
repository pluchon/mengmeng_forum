import { computed, onActivated, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ossAvatarUrl, ossFeedCoverUrl } from '@/utils/ossImageStyle'
import boardEmptyImageUrl from '@/assets/images/search_chat_empty.png'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Loading, Picture, Refresh } from '@element-plus/icons-vue'
import LikeCountIcon from '@/components/common/LikeCountIcon.vue'
import UserAvatarVip from '@/components/common/UserAvatarVip.vue'
import Masonry from '@/components/common/Masonry.vue'
import AppPagination from '@/components/common/AppPagination.vue'
import feedServerErrorArt from '@/assets/images/503_error.png'
import feedNetworkErrorArt from '@/assets/images/network_error.png'
import { useBoardStore } from '@/stores/board'
import { useHomeShellContext } from '@/composables/useHomeShell'
import { restoreFeedScroll } from '@/utils/feedScrollRestore'
import {
  captureFeedCardOrigin,
  captureFeedOpenFrom,
  getFeedCardOrigin,
  onFeedVisitCountUpdate,
} from '@/utils/feedNavigation'
import {
  isQuestionArticle,
  questionStatusClass,
  questionStatusLabel,
  QUESTION_STATUS,
} from '@/utils/articleQuestion'
import { useNotInterestedArticleStore } from '@/stores/notInterestedArticle'
import { defaultCardOutline } from '@/utils/coverThemeColor'

defineOptions({ name: 'HomeFeed' })

const route = useRoute()
const router = useRouter()
const boardStore = useBoardStore()

const {
  articleList,
  activeCategoryId,
  categoriesWithId,
  coverImageUrl,
  currentBoardId,
  defaultAvatar,
  feedError,
  feedForbidden,
  feedErrorKind,
  ensureHomeFeedLoaded,
  fetchArticles,
  getRandomPastel,
  loading,
  pageNum,
  pageSize,
  placeholderMinHeight,
  selectCategoryMenu,
  selectHomeBoard,
  showCategoryNavigator,
  isRecommendationFeed,
  total,
} = useHomeShellContext()

const feedList = computed(() => articleList.value)
const notInterestedArticleStore = useNotInterestedArticleStore()
const coverLoadedById = reactive({})
const coverAspectById = reactive({})
const videoDurationById = reactive({})
const probingDurationIds = new Set()
let stopVisitCountListen = null
const MASONRY_COLUMN_WIDTH = 220

function estimateCoverHeight(article, entry, seed) {
  const hasCover = Boolean(coverImageUrl(entry))
  if (!hasCover) {
    if (isVideoArticle(article)) return 200
    return 160 + Math.abs(seed % 5) * 36
  }
  const aspect = coverAspectById[article?.id]
  if (aspect) {
    const [aw, ah] = String(aspect).split('/').map((part) => Number(String(part).trim()))
    if (aw > 0 && ah > 0) {
      return Math.min(420, Math.round(MASONRY_COLUMN_WIDTH * (ah / aw)))
    }
  }
  // 与 CSS 未锁定封面的 4:3 占位一致
  return Math.round(MASONRY_COLUMN_WIDTH * 4 / 3)
}

function coverSrcMatchesBase(loadedSrc, baseSrc) {
  const loaded = String(loadedSrc || '').trim()
  const base = String(baseSrc || '').trim()
  if (!loaded || !base) return false
  if (loaded === base) return true
  try {
    const loadedUrl = new URL(loaded, window.location.origin)
    const baseUrl = new URL(base, window.location.origin)
    return loadedUrl.pathname === baseUrl.pathname
  } catch {
    return loaded.endsWith(base) || base.endsWith(loaded)
  }
}

const masonryCards = computed(() => {
  const list = feedList.value || []
  return list.map((entry, index) => {
    const article = entry?.article || {}
    const seed = Number(article?.id) || index + 1
    const baseCoverHeight = estimateCoverHeight(article, entry, seed)
    const titleLength = String(article?.title || '').length
    const titleHeight = Math.min(2, Math.max(1, Math.ceil(titleLength / 24))) * 24
    const extraHeight = (isQuestionArticle(article) ? 24 : 0) + (isVideoArticle(article) ? 20 : 0)
    return {
      id: String(article?.id || `home-feed-${index}`),
      entry,
      height: baseCoverHeight + 128 + titleHeight + extraHeight,
      heightSettled: !coverImageUrl(entry)
        || Boolean(coverAspectById[article?.id] || coverLoadedById[article?.id]),
      article,
    }
  })
})
const masonryReloadKey = computed(() => {
  const first = masonryCards.value?.[0]?.id
  return `${currentBoardId.value}-${masonryCards.value.length}-${first || ''}`
})

const feedLoadErrorArt = computed(() => (
  feedErrorKind.value === 'network' ? feedNetworkErrorArt : feedServerErrorArt
))

const feedLoadErrorTitle = computed(() => {
  if (feedErrorKind.value === 'forbidden' || feedForbidden.value) {
    return '暂时无法访问这部分内容'
  }
  if (feedErrorKind.value === 'network') {
    return '网络好像走神了，请重试'
  }
  if (feedErrorKind.value === 'server') {
    return '服务器开小差了，请重试'
  }
  return feedError.value || '内容加载失败，请稍后重试'
})

function markCoverLoaded(articleId) {
  if (!articleId) return
  coverLoadedById[articleId] = true
}

// 封面展示成功：只标记渐显；按封面自然比例锁死卡片，悬停首帧/首图不挤高
function onCoverLoad(articleId, entry, event) {
  markCoverLoaded(articleId)
  if (!articleId || coverAspectById[articleId]) return
  const img = event?.target
  const base = baseCoverUrl(entry)
  const loadedSrc = String(img?.currentSrc || img?.src || '').trim()
  if (base && loadedSrc && !coverSrcMatchesBase(loadedSrc, base)) return
  const w = Number(img?.naturalWidth) || 0
  const h = Number(img?.naturalHeight) || 0
  if (w > 0 && h > 0) {
    coverAspectById[articleId] = `${w} / ${h}`
  }
}

function coverAspectStyle(articleId) {
  const ratio = coverAspectById[articleId]
  return ratio ? { '--cover-aspect': ratio } : undefined
}

function onCoverError(articleId) {
  // 封面失败也视为该卡可展示，避免永久透明
  markCoverLoaded(articleId)
}

function isNotInterestedArticle(articleId) {
  return notInterestedArticleStore.isNotInterested(articleId)
}

function isVideoArticle(article) {
  return Number(article?.mediaType) === 1 || Boolean(String(article?.videoUrl || '').trim())
}

function cardTypeClass(article) {
  const classes = {
    'note-card--not-interested': isNotInterestedArticle(article?.id),
  }
  if (isQuestionArticle(article)) {
    classes['note-card--question'] = true
    const status = Number(article?.questionStatus)
    if (status === QUESTION_STATUS.RESOLVED) classes['note-card--question-resolved'] = true
    else classes['note-card--question-waiting'] = true
    if (isVideoArticle(article)) classes['note-card--video'] = true
  } else if (isVideoArticle(article)) {
    classes['note-card--video'] = true
  } else {
    classes['note-card--image'] = true
  }
  return classes
}

function cardOutlineStyle(entry) {
  return { '--note-card-outline': defaultCardOutline(entry?.article) }
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function videoDurationLabel(articleId) {
  const sec = videoDurationById[articleId]
  if (!Number.isFinite(sec) || sec <= 0) return ''
  return formatDuration(sec)
}

function probeVideoDuration(article) {
  const id = article?.id
  const url = String(article?.videoUrl || '').trim()
  if (!id || !url || videoDurationById[id] || probingDurationIds.has(id)) return
  probingDurationIds.add(id)
  const video = document.createElement('video')
  video.preload = 'metadata'
  video.muted = true
  video.src = url
  const finish = () => {
    probingDurationIds.delete(id)
    video.removeAttribute('src')
    video.load()
  }
  video.onloadedmetadata = () => {
    const duration = Number(video.duration)
    if (Number.isFinite(duration) && duration > 0) {
      videoDurationById[id] = duration
    }
    finish()
  }
  video.onerror = finish
}

watch(
  feedList,
  (list) => {
    for (const entry of list || []) {
      if (isVideoArticle(entry?.article)) {
        probeVideoDuration(entry.article)
      }
    }
  },
  { immediate: true },
)

const openCategoryId = ref(null)
let categoryCloseTimer = null

// 卡片列宽固定 282px，原本直接加载原图：一屏几十张手机拍的大图，
// 全量下载只为渲染 282px 宽的卡片。改走 OSS 缩略样式。
// 详情页主图和"下载原图"仍用原始 URL，不受影响
function baseCoverUrl(entry) {
  return ossFeedCoverUrl(String(coverImageUrl(entry) || '').trim())
}

function displayCoverUrl(entry) {
  return baseCoverUrl(entry)
}

function openArticle(entry, event) {
  const id = entry?.article?.id
  if (!id) return
  const card = event?.currentTarget?.closest?.('.note-card') || event?.currentTarget
  const cover = card?.querySelector?.('.note-cover') || card
  if (cover) {
    captureFeedCardOrigin(id, cover, { coverUrl: displayCoverUrl(entry) })
  }
  getFeedCardOrigin(id)
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
  stopVisitCountListen = onFeedVisitCountUpdate((detail) => {
    const id = detail?.articleId
    const count = Number(detail?.visitCount)
    if (!id || Number.isNaN(count)) return
    const row = articleList.value.find((item) => String(item?.article?.id) === String(id))
    if (row?.article) row.article.visitCount = count
  })
  await boardStore.fetchCategoryList()
  await ensureHomeFeedLoaded()
})

onActivated(() => {
  restoreFeedScroll()
})

onUnmounted(() => {
  if (categoryCloseTimer) clearTimeout(categoryCloseTimer)
  if (typeof stopVisitCountListen === 'function') stopVisitCountListen()
})
