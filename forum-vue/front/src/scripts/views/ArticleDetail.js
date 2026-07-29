import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Share, PictureFilled, CollectionTag, Close, MagicStick, Picture, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { getArticleDetail, streamArticleGuide, getAuditStatus } from '@/api/article'
import {
  acceptQuestionAnswer,
  closeQuestion,
} from '@/api/articleQuestion'
import { captureFeedScroll, restoreFeedScroll } from '@/utils/feedScrollRestore'
import {
  animateDetailDialogToCard,
  clearFeedNavigationState,
  getFeedCardOrigin,
  shouldReturnBackToFeed,
} from '@/utils/feedNavigation'
import { getReplyList, submitReply as apiSubmitReply, submitSubReply, likeReply, unlikeReply } from '@/api/reply'
import { likeArticle, unlikeArticle } from '@/api/like'
import { cancelArticleFavorite, getMyFavoriteFolders, saveArticleFavorite } from '@/api/favorite'
import SubReplyArea from '@/components/article/SubReplyArea.vue'
import ArticleDetailVideo from '@/components/article/ArticleDetailVideo.vue'
import { marked } from 'marked'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { sanitizeHtml, sanitizePlainTextAsHtml } from '@/utils/security'
import { unwrapPageRecords } from '@/utils/apiData'
import { ARTICLE_STATUS } from '@/utils/articleStatus'
import { formatForumDateTimeShanghai } from '@/utils/datetime'
import {
  QUESTION_STATUS,
  isQuestionArticle,
  questionStatusClass,
  questionStatusLabel,
} from '@/utils/articleQuestion'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { followUser, unfollowUser, getFollowStats } from '@/api/userFollow'
import { markRecommendationNotInterested } from '@/api/recommendation'
import { useNotInterestedArticleStore } from '@/stores/notInterestedArticle'
import { uploadChatImage } from '@/api/message'
import { useEmojiShopStore } from '@/stores/emojiShop'
import { validateLocalImageFile, openImageUploadLoading, getBatchImageUploadLoadingText } from '@/utils/imageUploadFeedback'
import { validateChatImageMime } from '@/utils/chatMedia'
import emojiPackIconUrl from '@/assets/svg/表情包.svg?url'
import emptyCommentIconUrl from '@/assets/svg/空评论.svg?url'
import '@/assets/styles/article.css'

export function useArticleDetail() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const notInterestedArticleStore = useNotInterestedArticleStore()
  const defaultAvatar = DEFAULT_AVATAR

  const loading = ref(true)
  const article = ref(null)
  const author = ref(null)
  const board = ref(null)
  const isLiked = ref(false)
  const isOwner = ref(false)
  const isFavorited = ref(false)
  const notInterestedSaving = ref(false)
  const notInterestedDialogVisible = ref(false)
  const notInterestedReasonCode = ref('')
  const notInterestedReasonDetail = ref('')
  const notInterestedReasons = [
    { code: 'UNRELATED', label: '与我无关' },
    { code: 'TOPIC', label: '不喜欢这个话题' },
    { code: 'AUTHOR', label: '不喜欢这位作者' },
    { code: 'DUPLICATE', label: '内容重复' },
    { code: 'LOW_QUALITY', label: '内容质量不佳' },
    { code: 'OTHER', label: '其他原因' },
  ]
  const questionActionSaving = ref(false)
  const aiSummary = ref('')
  const aiSummaryIsHint = ref(false)
  const aiLoading = ref(false)
  const aiSummaryAreaRef = ref(null)
  let guideStreamAbort = null

  function resizeAiSummaryArea() {
    const el = aiSummaryAreaRef.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.max(72, el.scrollHeight)}px`
  }

  function isAiSummaryHintMessage(text) {
    const t = String(text || '')
    return (
      t.includes('内容较少')
      || t.includes('建议包含更多内容')
      || t.includes('暂时不可用')
      || t.includes('不存在或已被删除')
      || t.includes('过于相似')
      || t.includes('未能生成有效摘要')
    )
  }

  function isSummaryLikelyArticleBody(summaryText, articleContent) {
    const s = String(summaryText || '').replace(/\s+/g, '')
    const plain = String(articleContent || '').replace(/<[^>]+>/g, '').replace(/\s+/g, '').trim()
    if (!s || !plain || s.length < 20) return false
    return s === plain || (plain.length > 40 && (s.includes(plain) || plain.includes(s)))
  }
  const replies = ref([])
  const replyContent = ref('')
  const replyTarget = ref(null)
  const replyImageInput = ref(null)
  const emojiShopStore = useEmojiShopStore()
  const replyPendingImages = ref([])
  const replyPendingEmojis = ref([])
  const replyEmojiPanelOpen = ref(false)
  const replySelectedPackId = ref(null)
  const replyPackBarRef = ref(null)
  const replyPackBarCanScrollLeft = ref(false)
  const replyPackBarCanScrollRight = ref(false)
  const subReplyRefreshTokens = ref({})

  const REPLY_MEDIA_TYPE_IMAGE = 1
  const REPLY_MEDIA_TYPE_SHOP_EMOJI = 2
  const REPLY_IMAGE_MAX = 6
  const REPLY_EMOJI_MAX = 5

  const isQuestion = computed(() => isQuestionArticle(article.value))
  const isNotInterested = computed(() => notInterestedArticleStore.isNotInterested(article.value?.id))
  const isQuestionClosed = computed(() =>
    isQuestion.value && Number(article.value?.questionStatus) === QUESTION_STATUS.CLOSED,
  )
  const canCloseQuestion = computed(() =>
    isQuestion.value
      && isOwner.value
      && Number(article.value?.questionStatus) === QUESTION_STATUS.WAITING,
  )
  const canAcceptAnswer = computed(() => canCloseQuestion.value)

  const canSubmitReply = computed(() => {
    if (isQuestionClosed.value) return false
    const text = replyContent.value.trim()
    return !!text || replyPendingImages.value.length > 0 || replyPendingEmojis.value.length > 0
  })

  const replyVisiblePacks = computed(() => emojiShopStore.myPacks)

  const replySelectedPack = computed(() => {
    const packs = replyVisiblePacks.value
    if (!packs.length) return null
    const id = replySelectedPackId.value
    if (id != null) {
      const hit = packs.find((p) => Number(p.shopId) === Number(id) || Number(p.userEmojiId) === Number(id))
      if (hit) return hit
    }
    return packs[0]
  })

  function buildReplyMediaList() {
    const list = []
    for (const img of replyPendingImages.value) {
      list.push({ mediaType: REPLY_MEDIA_TYPE_IMAGE, mediaUrl: img.mediaUrl })
    }
    for (const em of replyPendingEmojis.value) {
      list.push({ mediaType: REPLY_MEDIA_TYPE_SHOP_EMOJI, mediaUrl: em.mediaUrl, shopId: em.shopId })
    }
    return list
  }

  function clearReplyPendingMedia() {
    replyPendingImages.value = []
    replyPendingEmojis.value = []
  }

  function removePendingImage(idx) {
    replyPendingImages.value = replyPendingImages.value.filter((_, i) => i !== idx)
  }

  function removePendingEmoji(idx) {
    replyPendingEmojis.value = replyPendingEmojis.value.filter((_, i) => i !== idx)
  }

  async function triggerReplyImagePick() {
    if (!(await ensureLoggedIn())) return
    replyImageInput.value?.click()
  }

  async function onReplyImageFileChange(e) {
    const files = Array.from(e.target.files || [])
    e.target.value = ''
    if (!files.length) return
    if (!(await ensureLoggedIn())) return

    const current = replyPendingImages.value.length
    const remaining = REPLY_IMAGE_MAX - current
    if (remaining <= 0) {
      ElMessage.warning(`最多上传 ${REPLY_IMAGE_MAX} 张图片`)
      return
    }
    if (files.length > remaining) {
      ElMessage.warning(`最多还能上传 ${remaining} 张，请重新选择`)
      return
    }

    for (const file of files) {
      const mimeOk = validateChatImageMime(file)
      if (!mimeOk.ok) {
        ElMessage.warning(`${file.name}：${mimeOk.message}`)
        return
      }
      const sizeOk = validateLocalImageFile(file)
      if (!sizeOk.ok) {
        ElMessage.warning(`${file.name}：${sizeOk.message}`)
        return
      }
    }

    const uploadTip = getBatchImageUploadLoadingText(files, '正在上传评论图片…')
    const tipMsg = ElMessage.info({ message: uploadTip, duration: 0, showClose: false, grouping: true })
    const batchLoading = openImageUploadLoading(files[0], uploadTip)
    try {
      for (const file of files) {
        const up = await uploadChatImage(file)
        if (up.code === 0 && up.data) {
          replyPendingImages.value.push({ mediaUrl: up.data })
        }
      }
    } catch {
      /* 拦截器已提示 */
    } finally {
      tipMsg.close()
      batchLoading.close()
    }
  }

  async function onReplyEmojiPopoverShow() {
    if (!userStore.isLoggedIn) return
    try {
      await emojiShopStore.fetchMyPacks()
      const packs = replyVisiblePacks.value
      if (packs.length && replySelectedPackId.value == null) {
        replySelectedPackId.value = packs[0].shopId
      }
      await nextTick()
      updateReplyPackBarScrollState()
    } catch {
      /* 已提示 */
    }
  }

  function selectReplyPack(pack) {
    replySelectedPackId.value = pack?.shopId ?? null
    nextTick(updateReplyPackBarScrollState)
  }

  function scrollReplyPackBarRight() {
    replyPackBarRef.value?.scrollBy({ left: 120, behavior: 'smooth' })
  }

  function scrollReplyPackBarLeft() {
    replyPackBarRef.value?.scrollBy({ left: -120, behavior: 'smooth' })
  }

  function onReplyPackBarScroll() {
    updateReplyPackBarScrollState()
  }

  function updateReplyPackBarScrollState() {
    const el = replyPackBarRef.value
    if (!el) {
      replyPackBarCanScrollLeft.value = false
      replyPackBarCanScrollRight.value = false
      return
    }
    const overflow = el.scrollWidth > el.clientWidth + 4
    replyPackBarCanScrollLeft.value = overflow && el.scrollLeft > 2
    replyPackBarCanScrollRight.value = overflow && el.scrollLeft < el.scrollWidth - el.clientWidth - 2
  }

  function addReplyShopEmoji(url) {
    const pack = replySelectedPack.value
    if (!pack || !url) return
    if (replyPendingEmojis.value.length >= REPLY_EMOJI_MAX) {
      ElMessage.warning(`最多添加 ${REPLY_EMOJI_MAX} 个表情`)
      return
    }
    if (replyPendingEmojis.value.some((item) => item.mediaUrl === url)) return
    replyPendingEmojis.value.push({ mediaUrl: url, shopId: pack.shopId })
    replyEmojiPanelOpen.value = false
  }

  function openCommentShopDetail(shopId) {
    const id = Number(shopId)
    if (!Number.isFinite(id) || id <= 0) return
    router.push({ path: '/emoji-shop', query: { detail: String(id) } }).catch(() => {})
  }

  const contentExpanded = ref(false)
  const isFollowingAuthor = ref(false)
  const followSaving = ref(false)

  const DETAIL_TOAST_Z_INDEX = 6000

  const shareCopied = ref(false)
  let shareCopiedTimer = null

  const favoriteDialogVisible = ref(false)
  const favoriteFoldersLoading = ref(false)
  const favoriteFolders = ref([])
  const selectedFolderId = ref(null) // null -> 默认收藏夹；字符串避免 Long 精度丢失
  const favoriteSaving = ref(false)

  const dialogOpen = ref(true)
  let detailClosing = false
  let skipDialogClosedNav = false

  /** 笔记相册 URL（与 article 正文独立） */
  const articleTags = ref([])
  const tagsExpanded = ref(false)
  const visibleArticleTags = computed(() => (
    tagsExpanded.value ? articleTags.value : articleTags.value.slice(0, 2)
  ))
  const hiddenArticleTagCount = computed(() => Math.max(0, articleTags.value.length - 2))

  function toggleArticleTags() {
    tagsExpanded.value = !tagsExpanded.value
  }

  const replyTargetLabel = computed(() => {
    if (!replyTarget.value) return ''
    const nickname = replyTarget.value.nickname || '用户'
    return replyTarget.value.showMention ? `回复给 @${nickname}` : `回复给 ${nickname}`
  })

  const articleGalleryUrls = ref([])
  const activeGalleryIndex = ref(0)
  const galleryStripRef = ref(null)
  const galleryStripOverflow = ref(false)
  const galleryStripFadeLeft = ref(false)
  const galleryStripFadeRight = ref(false)
  let galleryResizeObserver = null

  const activeGalleryUrl = computed(() => {
    const urls = articleGalleryUrls.value
    if (!urls.length) return ''
    const i = Math.min(Math.max(0, activeGalleryIndex.value), urls.length - 1)
    return urls[i] || ''
  })

  /** 主图：相册优先，否则用封面（与首页瀑布流卡片一致） */
  const mainDisplayImageUrl = computed(() => {
    const gallery = activeGalleryUrl.value
    if (gallery) return gallery
    return String(article.value?.coverImg || '').trim()
  })

  const imagePreviewList = computed(() => {
    if (articleGalleryUrls.value.length) return articleGalleryUrls.value
    const cover = String(article.value?.coverImg || '').trim()
    return cover ? [cover] : []
  })

  const articleVideoUrl = computed(() => String(article.value?.videoUrl || '').trim())
  const detailVideoRef = ref(null)

  function replayDetailVideo(e) {
    const v = e?.target || detailVideoRef.value
    if (!v) return
    v.currentTime = 0
    v.play().catch(() => {})
  }

  watch([articleVideoUrl, dialogOpen], () => {
    if (!dialogOpen.value || !articleVideoUrl.value) return
    nextTick(() => {
      const v = detailVideoRef.value
      if (!v) return
      v.currentTime = 0
      v.play().catch(() => {})
    })
  })

  const isVideoArticle = computed(() => {
    if (Number(article.value?.mediaType) === 1) return true
    return !!articleVideoUrl.value && !articleGalleryUrls.value.length
  })

  function updateGalleryStripState() {
    const el = galleryStripRef.value
    if (!el) return
    const overflow = el.scrollWidth > el.clientWidth + 2
    const fadeLeft = overflow && el.scrollLeft > 4
    const fadeRight = overflow && el.scrollLeft + el.clientWidth < el.scrollWidth - 4
    galleryStripOverflow.value = overflow
    galleryStripFadeLeft.value = fadeLeft
    galleryStripFadeRight.value = fadeRight
  }

  function onGalleryStripScroll() {
    updateGalleryStripState()
  }

  function setActiveGalleryIndex(index) {
    activeGalleryIndex.value = index
  }

  function bindGalleryStripObserver() {
    galleryResizeObserver?.disconnect()
    const el = galleryStripRef.value
    if (!el) return
    galleryResizeObserver = new ResizeObserver(() => updateGalleryStripState())
    galleryResizeObserver.observe(el)
  }

  watch(articleGalleryUrls, (urls) => {
    activeGalleryIndex.value = 0
    nextTick(() => {
      updateGalleryStripState()
      bindGalleryStripObserver()
    })
    if (!urls?.length) {
      galleryStripOverflow.value = false
      galleryStripFadeLeft.value = false
      galleryStripFadeRight.value = false
    }
  })

  onUnmounted(() => {
    galleryResizeObserver?.disconnect()
    galleryResizeObserver = null
    if (shareCopiedTimer) {
      clearTimeout(shareCopiedTimer)
      shareCopiedTimer = null
    }
  })

  const renderedContent = computed(() => {
    const content = article.value?.content || ''
    let html = ''
    const ct = Number(article.value?.contentType)
    if (ct === 1) {
      try {
        html = marked.parse(content)
      } catch (e) {
        html = content
      }
    } else if (content.trim().startsWith('<')) {
      html = content
    } else {
      html = `<p class="plain-text">${content}</p>`
    }
    return sanitizeHtml(html)
  })

  function renderCommentHtml(content) {
    return sanitizePlainTextAsHtml(content || '')
  }

  const plainContentLength = computed(() => {
    const raw = article.value?.content || ''
    return String(raw).replace(/<[^>]+>/g, '').replace(/\s+/g, '').length
  })

  const shouldCollapseContent = computed(() => plainContentLength.value > 280)

  const replyPlaceholder = computed(() => {
    if (isQuestionClosed.value) return '问题已关闭，暂不接受新回答'
    if (replyTarget.value) return ''
    return isQuestion.value ? '写下你的回答…' : '说点什么…'
  })

  function stripReplyPlainText(html) {
    return String(html || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
  }

  function buildReplyContentPreview(html) {
    const plain = stripReplyPlainText(html)
    if (!plain) return ''
    if (plain.length <= 20) return plain
    return `${plain.slice(0, 20)}...`
  }

  const replyCountDisplay = computed(() => {
    if (!article.value) return 0
    return Math.max(Number(article.value.replyCount) || 0, replies.value.length)
  })

  const ownerAuditNotice = computed(() => {
    if (!isOwner.value || !article.value?.id) return null
    const s = Number(article.value.status)
    const id = article.value.id
    if (s === ARTICLE_STATUS.APPROVED) {
      return {
        type: 'success',
        title: '审核通过',
        description: '帖子正在发布，请稍候刷新页面。',
        buttonText: '返回首页',
        path: '/',
      }
    }
    if (s === ARTICLE_STATUS.PENDING_AUDIT) {
      return {
        type: 'warning',
        title: '审核中',
        description: '内容正在由系统异步审核，通过后帖子会自动发布，结果将通过站内信通知。',
        buttonText: '返回首页',
        path: '/',
      }
    }
    if (s === ARTICLE_STATUS.REJECTED) {
      return {
        type: 'error',
        title: '审核未通过',
        description: article.value.auditResultMessage || '请修改正文或图片后重新提交审核。',
        buttonText: '去修改',
        path: `/article/edit/${id}`,
      }
    }
    if (s === ARTICLE_STATUS.AUDIT_ERROR) {
      return {
        type: 'warning',
        title: '审核异常',
        description: article.value.auditResultMessage || '审核服务暂时不可用，请稍后重试。',
        buttonText: '返回编辑',
        path: `/article/edit/${id}`,
      }
    }
    if (s === ARTICLE_STATUS.DRAFT) {
      return {
        type: 'info',
        title: '草稿',
        description: '帖子尚未上架。请完善内容、封面后提交审核。',
        buttonText: '继续编辑',
        path: `/article/edit/${id}`,
      }
    }
    return null
  })

  async function loadArticleDetail(articleId) {
    if (articleId == null || articleId === '') return
    loading.value = true
    article.value = null
    replies.value = []
    replyTarget.value = null
    contentExpanded.value = false
    aiSummary.value = ''
    aiSummaryIsHint.value = false
    articleGalleryUrls.value = []
    articleTags.value = []
    tagsExpanded.value = false
    activeGalleryIndex.value = 0
    try {
      const res = await getArticleDetail(articleId)
      if (res.code === 0) {
        article.value = res.data.article
        author.value = res.data.user
        board.value = res.data.board
        articleTags.value = Array.isArray(res.data.tags) ? res.data.tags : []
        isLiked.value = res.data.isLiked || false
        isOwner.value = res.data.isOwner || false
        isFavorited.value = res.data.isFavorited || false
        notInterestedArticleStore.syncFeedbackState(articleId, res.data.isNotInterested === true)
        articleGalleryUrls.value = Array.isArray(res.data.imageUrls) ? [...res.data.imageUrls] : []
        await loadAuthorFollowState()
        await syncOwnerArticleStatus(articleId)
        await nextTick()
        updateGalleryStripState()
        bindGalleryStripObserver()
      }
    } finally {
      loading.value = false
    }
    await loadReplies()
  }

  watch(
    () => route.params.id,
    (id) => {
      if (id) {
        dialogOpen.value = true
        loadArticleDetail(id)
      }
    },
    { immediate: true },
  )

  async function syncOwnerArticleStatus(articleId) {
    if (!isOwner.value || !article.value) return
    const s = Number(article.value.status)
    if (s !== ARTICLE_STATUS.PENDING_AUDIT && s !== ARTICLE_STATUS.APPROVED) return
    try {
      const stRes = await getAuditStatus(articleId)
      if (stRes.code !== 0 || stRes.data?.status == null) return
      const latest = Number(stRes.data.status)
      if (latest === s) return
      article.value = { ...article.value, status: latest }
      if (latest === ARTICLE_STATUS.PUBLISHED) {
        const again = await getArticleDetail(articleId)
        if (again.code === 0) {
          article.value = again.data.article
          author.value = again.data.user
          board.value = again.data.board
        }
      }
    } catch {
      /* ignore */
    }
  }

  function shouldReturnToProfile() {
    return route.query.from === 'profile'
  }

  function getProfileReturnPath() {
    try {
      const raw = sessionStorage.getItem('profile-return-state')
      if (raw) {
        const state = JSON.parse(raw)
        if (state.profileUserId) return `/profile/${state.profileUserId}`
      }
    } catch {
      /* ignore */
    }
    return '/profile'
  }

  async function handleBeforeClose(done) {
    if (detailClosing) {
      done()
      return
    }
    detailClosing = true

    const articleId = route.params.id
    const fromHome = shouldReturnBackToFeed()
    const origin = fromHome ? getFeedCardOrigin(articleId) : null

    try {
      if (origin) {
        await animateDetailDialogToCard(origin)
      }
    } catch {
      /* ignore */
    } finally {
      done()
      detailClosing = false
      if (shouldReturnToProfile()) {
        skipDialogClosedNav = true
        if (window.history.length > 1) {
          router.back()
        } else {
          router.replace(getProfileReturnPath())
        }
        return
      }
      if (fromHome && window.history.length > 1) {
        skipDialogClosedNav = true
        clearFeedNavigationState()
        router.back().then(() => {
          nextTick(() => restoreFeedScroll())
        })
      }
    }
  }

  function handleDialogClosed() {
    if (skipDialogClosedNav) {
      skipDialogClosedNav = false
      return
    }
    if (/^\/article\//.test(route.path)) {
      if (shouldReturnToProfile()) {
        router.replace(getProfileReturnPath())
        return
      }
      clearFeedNavigationState()
      router.replace('/').then(() => restoreFeedScroll())
    }
  }

  function closeDetailDialog() {
    handleBeforeClose(() => {
      dialogOpen.value = false
    })
  }

  function goAuthorProfile() {
    const uid = author.value?.id
    if (!uid) return
    skipDialogClosedNav = true
    clearFeedNavigationState()
    closeDetailDialog()
    router.push(`/profile/${uid}`)
  }

  function goUserProfile(userId) {
    const uid = Number(userId)
    if (!Number.isFinite(uid) || uid <= 0) return
    skipDialogClosedNav = true
    clearFeedNavigationState()
    closeDetailDialog()
    router.push(`/profile/${uid}`)
  }

  async function loadReplies() {
    const res = await getReplyList({ articleId: route.params.id, pageNum: 1, pageSize: 50 })
    if (res.code === 0) {
      replies.value = unwrapPageRecords(res.data).map((row) => ({
        ...row,
        liked: !!row.liked,
        subReplyCount: row.subReplyCount ?? 0,
      }))
      if (article.value) {
        article.value.replyCount = Math.max(Number(article.value.replyCount) || 0, replies.value.length)
      }
    }
  }

  function isAcceptedReply(item) {
    return Number(item?.articleReply?.id) === Number(article.value?.acceptedReplyId)
  }

  async function acceptAnswer(item) {
    if (!canAcceptAnswer.value || questionActionSaving.value) return
    const replyId = item?.articleReply?.id
    if (!replyId || !article.value?.id) return
    try {
      await ElMessageBox.confirm(
        '采纳后问题将标记为已解决，P0 暂不支持改选其他回答。',
        '采纳为最佳答案',
        {
          type: 'warning',
          confirmButtonText: '确认采纳',
          cancelButtonText: '再看看',
        },
      )
    } catch {
      return
    }
    questionActionSaving.value = true
    try {
      const res = await acceptQuestionAnswer({
        articleId: article.value.id,
        replyId,
      })
      if (res.code === 0) {
        article.value.questionStatus = QUESTION_STATUS.RESOLVED
        article.value.acceptedReplyId = replyId
        await loadReplies()
        ElMessage.success('已采纳为最佳答案')
      }
    } catch {
      // 请求层已统一展示错误，这里只阻止事件异常继续冒泡。
    } finally {
      questionActionSaving.value = false
    }
  }

  async function closeCurrentQuestion() {
    if (!canCloseQuestion.value || questionActionSaving.value || !article.value?.id) return
    try {
      await ElMessageBox.confirm(
        '关闭后将不再接受新回答，P0 暂不支持重新打开。',
        '关闭这个问题',
        {
          type: 'warning',
          confirmButtonText: '确认关闭',
          cancelButtonText: '取消',
        },
      )
    } catch {
      return
    }
    questionActionSaving.value = true
    try {
      const res = await closeQuestion({ articleId: article.value.id })
      if (res.code === 0) {
        article.value.questionStatus = QUESTION_STATUS.CLOSED
        clearReplyTarget()
        ElMessage.success('问题已关闭')
      }
    } catch {
      // 请求层已统一展示错误，这里只阻止事件异常继续冒泡。
    } finally {
      questionActionSaving.value = false
    }
  }

  function clearReplyTarget() {
    replyTarget.value = null
  }

  function startReplyToFloor(item) {
    if (!item?.articleReply?.id) return
    replyTarget.value = {
      mode: 'sub',
      replyId: item.articleReply.id,
      replyUserId: null,
      showMention: false,
      nickname: item.user?.nickname || '用户',
      contentPreview: buildReplyContentPreview(item.articleReply.content),
    }
  }

  function startReplyToSub(payload) {
    if (!payload?.replyId) return
    replyTarget.value = {
      mode: 'sub',
      replyId: payload.replyId,
      replyUserId: payload.replyUserId || null,
      showMention: true,
      nickname: payload.nickname || '用户',
      contentPreview: buildReplyContentPreview(payload.content),
    }
  }

  async function toggleReplyLike(item) {
    if (!(await ensureLoggedIn('点赞需要登录'))) return
    const replyId = item?.articleReply?.id
    if (!replyId) return
    try {
      const res = item.liked ? await unlikeReply(replyId) : await likeReply(replyId)
      if (res.code === 0) {
        item.liked = !item.liked
        const base = Number(item.articleReply.likeCount) || 0
        item.articleReply.likeCount = Math.max(0, base + (item.liked ? 1 : -1))
      } else {
        ElMessage.error(res.message || '操作失败')
      }
    } catch {
      ElMessage.error('点赞请求异常')
    }
  }

  async function loadAuthorFollowState() {
    isFollowingAuthor.value = false
    if (!author.value?.id || isOwner.value || !userStore.isLoggedIn) return
    try {
      const res = await getFollowStats(author.value.id)
      if (res.code === 0) {
        isFollowingAuthor.value = !!res.data?.isFollowing
      }
    } catch {
      isFollowingAuthor.value = false
    }
  }

  async function toggleFollowAuthor() {
    if (!(await ensureLoggedIn('关注需要登录'))) return
    const uid = author.value?.id
    if (!uid || isOwner.value) return
    followSaving.value = true
    try {
      const res = isFollowingAuthor.value
        ? await unfollowUser(uid)
        : await followUser(uid)
      if (res.code === 0) {
        isFollowingAuthor.value = !isFollowingAuthor.value
        ElMessage.success({
          message: isFollowingAuthor.value ? '关注成功' : '已取消关注',
          zIndex: DETAIL_TOAST_Z_INDEX,
        })
      } else {
        ElMessage.error({ message: res.message || '操作失败', zIndex: DETAIL_TOAST_Z_INDEX })
      }
    } catch {
      ElMessage.error({ message: '操作异常', zIndex: DETAIL_TOAST_Z_INDEX })
    } finally {
      followSaving.value = false
    }
  }

  function copyToClipboardSync(text) {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.setAttribute('readonly', '')
      textarea.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0;pointer-events:none'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      textarea.setSelectionRange(0, text.length)
      const ok = document.execCommand('copy')
      document.body.removeChild(textarea)
      if (ok) return true
    } catch {
      // fall through
    }
    return false
  }

  function showShareCopiedFeedback() {
    shareCopied.value = true
    if (shareCopiedTimer) clearTimeout(shareCopiedTimer)
    shareCopiedTimer = setTimeout(() => {
      shareCopied.value = false
      shareCopiedTimer = null
    }, 2000)
  }

  function handleShare() {
    if (!article.value?.id) return
    const url = `${window.location.origin}/article/${article.value.id}`
    const copied = copyToClipboardSync(url)
    if (copied) {
      showShareCopiedFeedback()
      ElMessage.success({ message: '已复制链接', zIndex: DETAIL_TOAST_Z_INDEX, offset: 72 })
      return
    }
    if (navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(url).then(() => {
        showShareCopiedFeedback()
        ElMessage.success({ message: '已复制链接', zIndex: DETAIL_TOAST_Z_INDEX, offset: 72 })
      }).catch(() => {
        ElMessage.error({ message: '复制失败，请手动复制地址栏链接', zIndex: DETAIL_TOAST_Z_INDEX, offset: 72 })
      })
      return
    }
    ElMessage.error({ message: '复制失败，请手动复制地址栏链接', zIndex: DETAIL_TOAST_Z_INDEX, offset: 72 })
  }

  async function handleLike() {
    if (!(await ensureLoggedIn('点赞需要登录'))) return
    try {
      const res = isLiked.value 
        ? await unlikeArticle(article.value.id) 
        : await likeArticle(article.value.id)

      if (res.code === 0) {
        isLiked.value = !isLiked.value
        article.value.likeCount += isLiked.value ? 1 : -1
        ElMessage.success(isLiked.value ? '已点赞' : '已取消')
      } else {
        ElMessage.error(res.message || '操作失败')
      }
    } catch (err) {
      ElMessage.error('点赞请求异常')
    }
  }

  function openNotInterestedDialog() {
    if (!article.value?.id || isOwner.value || isNotInterested.value) return
    notInterestedReasonCode.value = ''
    notInterestedReasonDetail.value = ''
    notInterestedDialogVisible.value = true
  }

  async function submitNotInterested() {
    if (!article.value?.id || isOwner.value || notInterestedSaving.value) return
    if (!(await ensureLoggedIn('调整推荐内容需要登录'))) return
    if (!notInterestedReasonCode.value) {
      ElMessage.warning('请选择原因')
      return
    }
    notInterestedSaving.value = true
    try {
      const articleId = article.value.id
      const res = await markRecommendationNotInterested(
        articleId,
        notInterestedReasonCode.value,
        notInterestedReasonCode.value === 'OTHER' ? notInterestedReasonDetail.value.trim() : undefined,
      )
      if (res.code === 0) {
        notInterestedArticleStore.markNotInterested(articleId)
        notInterestedDialogVisible.value = false
        ElMessage.success('已设为不感兴趣')
      }
    } finally {
      notInterestedSaving.value = false
    }
  }

  async function loadFavoriteFolders() {
    favoriteFoldersLoading.value = true
    try {
      const res = await getMyFavoriteFolders({ pageNum: 1, pageSize: 100 })
      if (res.code === 0) {
        const raw = res.data
        const rows = Array.isArray(raw)
          ? raw
          : (Array.isArray(raw?.records) ? raw.records : [])
        favoriteFolders.value = rows
          .filter((f) => f && f.id != null)
          .map((f) => ({
            id: Number(f.id),
            name: f.name || '未命名收藏夹',
            isDefault: Number(f.isDefault) === 1 ? 1 : 0,
          }))
      } else {
        favoriteFolders.value = []
      }
    } finally {
      favoriteFoldersLoading.value = false
    }
  }

  async function toggleFavorite() {
    if (!(await ensureLoggedIn('点赞需要登录'))) return
    if (!article.value?.id) return

    if (isFavorited.value) {
      const res = await cancelArticleFavorite(article.value.id)
      if (res.code === 0) {
        isFavorited.value = false
        const fc = Number(article.value.favoriteCount) || 0
        article.value.favoriteCount = Math.max(0, fc - 1)
        ElMessage.success('已取消收藏')
      } else {
        ElMessage.error(res.message || '取消收藏失败')
      }
      return
    }

    selectedFolderId.value = null
    favoriteDialogVisible.value = true
    await loadFavoriteFolders()
  }

  async function confirmFavorite() {
    if (!article.value?.id) return
    favoriteSaving.value = true
    try {
      const folderId =
        selectedFolderId.value != null && selectedFolderId.value !== ''
          ? Number(selectedFolderId.value)
          : null
      const res = await saveArticleFavorite({
        articleId: article.value.id,
        folderId: Number.isFinite(folderId) ? folderId : null,
      })
      if (res.code === 0) {
        isFavorited.value = true
        favoriteDialogVisible.value = false
        const fc = Number(article.value.favoriteCount) || 0
        article.value.favoriteCount = fc + 1
        ElMessage.success('已收藏')
      } else {
        ElMessage.error(res.message || '收藏失败')
      }
    } finally {
      favoriteSaving.value = false
    }
  }

  async function submitReply() {
    if (!(await ensureLoggedIn('评论需要登录'))) return
    if (blockIfMuted(userStore)) return
    const text = replyContent.value.trim()
    const mediaList = buildReplyMediaList()
    if (!text && !mediaList.length) return
    try {
      let res
      const payload = { content: text, mediaList }
      if (replyTarget.value?.mode === 'sub' && replyTarget.value.replyId) {
        res = await submitSubReply({
          articleId: article.value.id,
          replyId: replyTarget.value.replyId,
          replyUserId: replyTarget.value.replyUserId,
          ...payload,
        })
      } else {
        res = await apiSubmitReply({ articleId: article.value.id, ...payload })
      }
      if (res.code === 0) {
        ElMessage.success('发送成功')
        replyContent.value = ''
        const wasSub = replyTarget.value?.mode === 'sub' && replyTarget.value.replyId
        const subReplyId = wasSub ? replyTarget.value.replyId : null
        replyTarget.value = null
        clearReplyPendingMedia()
        await loadReplies()
        if (subReplyId != null) {
          subReplyRefreshTokens.value = {
            ...subReplyRefreshTokens.value,
            [subReplyId]: (subReplyRefreshTokens.value[subReplyId] || 0) + 1,
          }
        }
      } else {
        ElMessage.error(res.message || '评论发送失败')
      }
    } catch (err) {
      if (err?.code === 1104) return
      ElMessage.error(err?.message || '评论发送失败')
    }
  }

  async function loadAiSummary() {
    if (!article.value?.id) return
    const plainContent = String(article.value?.content || '')
      .replace(/<[^>]+>/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
    if (plainContent.length < 30) {
      aiSummary.value = '内容过少，无法生成摘要'
      aiSummaryIsHint.value = true
      await nextTick()
      resizeAiSummaryArea()
      return
    }
    if (guideStreamAbort) {
      guideStreamAbort()
      guideStreamAbort = null
    }
    aiLoading.value = true
    aiSummary.value = ''
    aiSummaryIsHint.value = false
    await nextTick()
    resizeAiSummaryArea()
    await new Promise((resolve) => {
      guideStreamAbort = streamArticleGuide(article.value.id, {
        onChunk: (piece) => {
          aiSummary.value += piece
          aiSummaryIsHint.value = isAiSummaryHintMessage(aiSummary.value)
          nextTick(resizeAiSummaryArea)
        },
        onDone: () => {
          if (
            isAiSummaryHintMessage(aiSummary.value)
            || isSummaryLikelyArticleBody(aiSummary.value, article.value?.content)
          ) {
            aiSummary.value = '内容过少，无法生成摘要'
            aiSummaryIsHint.value = true
          }
          nextTick(resizeAiSummaryArea)
          aiLoading.value = false
          guideStreamAbort = null
          resolve()
        },
        onError: (msg) => {
          ElMessage.error(msg || '生成摘要失败')
          aiLoading.value = false
          guideStreamAbort = null
          resolve()
        },
      })
    })
  }

  const isVipGold = computed(() => {
    const t = Number(userStore.vipTier) || 0
    if (t <= 0) return false
    const exp = userStore.vipExpireAt
    if (!exp) return true
    const ms = new Date(exp).getTime()
    if (Number.isNaN(ms)) return true
    return Date.now() <= ms
  })

  return {
    ArrowLeft,
    ArrowRight,
    ChatDotRound,
    Close,
    CollectionTag,
    MagicStick,
    Picture,
    PictureFilled,
    Share,
    ArticleDetailVideo,
    SubReplyArea,
    aiLoading,
    aiSummary,
    aiSummaryAreaRef,
    aiSummaryIsHint,
    activeGalleryIndex,
    activeGalleryUrl,
    acceptAnswer,
    mainDisplayImageUrl,
    imagePreviewList,
    article,
    articleTags,
    hiddenArticleTagCount,
    tagsExpanded,
    toggleArticleTags,
    visibleArticleTags,
    articleGalleryUrls,
    articleVideoUrl,
    detailVideoRef,
    isVideoArticle,
    replayDetailVideo,
    author,
    addReplyShopEmoji,
    canSubmitReply,
    canAcceptAnswer,
    canCloseQuestion,
    clearReplyTarget,
    confirmFavorite,
    contentExpanded,
    shouldCollapseContent,
    closeDetailDialog,
    closeCurrentQuestion,
    defaultAvatar,
    dialogOpen,
    followSaving,
    isFollowingAuthor,
    isQuestion,
    isQuestionClosed,
    isAcceptedReply,
    toggleFollowAuthor,
    galleryStripFadeLeft,
    galleryStripFadeRight,
    galleryStripOverflow,
    galleryStripRef,
    goAuthorProfile,
    goUserProfile,
    handleBeforeClose,
    handleDialogClosed,
    favoriteDialogVisible,
    favoriteFolders,
    favoriteFoldersLoading,
    favoriteSaving,
    handleLike,
    handleShare,
    shareCopied,
    isLiked,
    isNotInterested,
    isOwner,
    isFavorited,
    isVipGold,
    emptyCommentIconUrl,
    emojiPackIconUrl,
    emojiShopStore,
    loadAiSummary,
    loading,
    loadFavoriteFolders,
    onGalleryStripScroll,
    onReplyEmojiPopoverShow,
    onReplyImageFileChange,
    onReplyPackBarScroll,
    openCommentShopDetail,
    ownerAuditNotice,
    questionActionSaving,
    questionStatusClass,
    questionStatusLabel,
    renderedContent,
    renderCommentHtml,
    replies,
    replyContent,
    replyCountDisplay,
    replyEmojiPanelOpen,
    replyImageInput,
    replyPackBarCanScrollLeft,
    replyPackBarCanScrollRight,
    replyPackBarRef,
    replyPendingEmojis,
    replyPendingImages,
    replyPlaceholder,
    replySelectedPack,
    replyTarget,
    replyTargetLabel,
    replyVisiblePacks,
    removePendingEmoji,
    removePendingImage,
    scrollReplyPackBarLeft,
    scrollReplyPackBarRight,
    selectReplyPack,
    selectedFolderId,
    setActiveGalleryIndex,
    startReplyToFloor,
    startReplyToSub,
    subReplyRefreshTokens,
    submitReply,
    notInterestedSaving,
    notInterestedDialogVisible,
    notInterestedReasonCode,
    notInterestedReasonDetail,
    notInterestedReasons,
    openNotInterestedDialog,
    submitNotInterested,
    toggleReplyLike,
    toggleFavorite,
    triggerReplyImagePick,
    formatForumDateTimeShanghai,
  }
}
