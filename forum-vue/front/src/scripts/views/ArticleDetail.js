import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { downloadImageByUrl, guessImageFileName } from '@/utils/imageDownload'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Share,
  PictureFilled,
  CollectionTag,
  Close,
  MagicStick,
  Picture,
  ArrowLeft,
  ArrowRight,
  RefreshRight,
  ArrowUp,
  Promotion,
  Flag,
  Download,
  Compass,
  VideoPlay,
  VideoPause,
  Headset,
  View,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import {
  getArticleDetail,
  getArticleSummaryState,
  getAuditStatus,
  reportArticleContent,
  regenerateArticleSummary,
} from '@/api/article'
import {
  acceptQuestionAnswer,
  setQuestionResolved,
} from '@/api/articleQuestion'
import { captureFeedScroll, restoreFeedScroll } from '@/utils/feedScrollRestore'
import {
  animateDetailDialogFromCard,
  animateDetailDialogToCard,
  captureVideoFirstFrame,
  clearFeedNavigationState,
  getFeedCardOrigin,
  getFeedReturnPath,
  notifyFeedVisitCountUpdate,
  preloadFeedOpenImage,
  removeFeedOpenMorphLayers,
  shouldReturnBackToFeed,
  shouldReturnBackToSearch,
} from '@/utils/feedNavigation'
import { ossThumbUrl } from '@/utils/ossImageStyle'
import { getReplyList, submitReply as apiSubmitReply, submitSubReply, likeReply, unlikeReply } from '@/api/reply'
import { likeArticle, unlikeArticle } from '@/api/like'
import { cancelArticleFavorite, getMyFavoriteFolders, saveArticleFavorite } from '@/api/favorite'
import SubReplyArea from '@/components/article/SubReplyArea.vue'
import CommentExpandableText from '@/components/article/CommentExpandableText.vue'
import ArticleDetailVideo from '@/components/article/ArticleDetailVideo.vue'
import BorderGlow from '@/components/common/BorderGlow.vue'
import { marked } from 'marked'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { sanitizeHtml, sanitizePlainTextAsHtml } from '@/utils/security'
import { apiErrorCode, unwrapPageRecords } from '@/utils/apiData'
import {
  ARTICLE_STATUS,
  articleDetailBlockedHint,
  canOpenArticleDetail,
} from '@/utils/articleStatus'
import { formatCommentTimeShanghai, formatForumDateTimeShanghai } from '@/utils/datetime'
import {
  QUESTION_STATUS,
  isQuestionArticle,
  isQuestionResolved,
  questionStatusClass,
  questionStatusLabel,
} from '@/utils/articleQuestion'
import { ensureLoggedIn } from '@/utils/loginPrompt'
import { followUser, unfollowUser, getFollowStats } from '@/api/userFollow'
import { markRecommendationNotInterested } from '@/api/recommendation'
import { useNotInterestedArticleStore } from '@/stores/notInterestedArticle'
import { uploadChatImage } from '@/api/message'
import { useEmojiShopStore } from '@/stores/emojiShop'
import { validateLocalImageFile } from '@/utils/imageUploadFeedback'
import { validateChatImageMime } from '@/utils/chatMedia'
import emojiPackIconUrl from '@/assets/svg/表情包.svg?url'
import emptyCommentIconUrl from '@/assets/svg/空评论.svg?url'
import articleNotFoundImageUrl from '@/assets/images/article_not_found.png'
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
  const triplePressActive = ref(false)
  const triplePressProgress = ref(0)
  let triplePressFrame = null
  let triplePressStartedAt = 0
  let triplePointerId = null
  const engagementSubmitting = ref(false)
  const isOwner = ref(false)
  // 被拒帖子的审核次数信息，仅本人可见
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
  const aiSummaryCollapsed = ref(true)
  const aiSummaryAreaRef = ref(null)
  const aiSummaryStatus = ref('NOT_READY')
  const aiSummaryCanExpand = ref(false)
  const aiSummaryCanRegenerate = ref(false)
  const contentReportDialogVisible = ref(false)
  const contentReportDialogTitle = ref('举报内容')
  const contentReportSubmitting = ref(false)
  const pendingContentReport = ref(null)
  let aiSummaryPollTimer = null
  let aiSummaryPollStartedAt = 0

  function resizeAiSummaryArea() {
    const el = aiSummaryAreaRef.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.max(0, el.scrollHeight)}px`
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
  // 置顶的在前，正常列表里同 id 的过滤掉 —— 否则用户往下滚到真实位置会看到重复的一条
  const visibleReplies = computed(() => {
    if (!pinnedReplies.value.length) return replies.value
    const pinnedIds = new Set(pinnedReplies.value.map((item) => item?.articleReply?.id))
    return [
      ...pinnedReplies.value,
      ...replies.value.filter((item) => !pinnedIds.has(item?.articleReply?.id)),
    ]
  })
  const replyPageNum = ref(1)
  const replyPageSize = ref(10)
  const replyTotal = ref(0)
  const replyLoadingMore = ref(false)
  const replyLoadMoreSentinelRef = ref(null)
  const articleContentScrollRef = ref(null)
  let replyLoadObserver = null
  const replyHasMore = computed(() => replies.value.length < Number(replyTotal.value || 0))
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
  const replySubmitting = ref(false)
  // 评论按时间正序 + 无限滚动，新评论其实落在最末尾，用户看不到自己刚发的。
  // 这里把它临时置顶在最前面——只是本次会话的视图状态，刷新后自然消失、
  // 回到真实顺序，所以不需要维护它的生命周期
  const pinnedReplies = ref([])
  const favoriteSubmitting = ref(false)
  const replyLikePending = ref(new Set())
  // AI 导读标题右侧的"刚刚更新"提示
  const summaryJustUpdated = ref(false)
  let summaryJustUpdatedTimer = null
  const subReplyRefreshTokens = ref({})

  const REPLY_MEDIA_TYPE_IMAGE = 1
  const REPLY_MEDIA_TYPE_SHOP_EMOJI = 2
  // 图片 + 表情包合计上限，与一行 8 槽位对齐
  const REPLY_MEDIA_MAX = 8

  function replyPendingMediaCount() {
    return replyPendingImages.value.length + replyPendingEmojis.value.length
  }

  function replyPendingMediaRemaining() {
    return Math.max(0, REPLY_MEDIA_MAX - replyPendingMediaCount())
  }

  const isQuestion = computed(() => isQuestionArticle(article.value))
  const isNotInterested = computed(() => notInterestedArticleStore.isNotInterested(article.value?.id))
  // 关闭问题已移除；历史 CLOSED 不再拦截回答
  const canAcceptAnswer = computed(() => isQuestion.value && isOwner.value)
  const canToggleQuestionResolved = computed(() => isQuestion.value && isOwner.value)
  const questionResolved = computed(() => isQuestionResolved(article.value))
  const questionResolveHint = computed(() =>
    questionResolved.value ? '该问题未解决？' : '该问题解决了吗？',
  )

  const canSubmitReply = computed(() => {
    if (replySubmitting.value) return false
    if (replyPendingImages.value.some((img) => img?.pending)) return false
    const text = replyContent.value.trim()
    const readyImages = replyPendingImages.value.filter(
      (img) => img && !img.pending && !img.failed && img.mediaUrl,
    )
    return !!text || readyImages.length > 0 || replyPendingEmojis.value.length > 0
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
      if (img?.pending || img?.failed || !img?.mediaUrl) continue
      list.push({ mediaType: REPLY_MEDIA_TYPE_IMAGE, mediaUrl: img.mediaUrl })
    }
    for (const em of replyPendingEmojis.value) {
      list.push({ mediaType: REPLY_MEDIA_TYPE_SHOP_EMOJI, mediaUrl: em.mediaUrl, shopId: em.shopId })
    }
    return list
  }

  function clearReplyPendingMedia() {
    replyPendingImages.value.forEach((img) => {
      if (img?.previewUrl) URL.revokeObjectURL(img.previewUrl)
    })
    replyPendingImages.value = []
    replyPendingEmojis.value = []
  }

  function removePendingImage(idx) {
    const target = replyPendingImages.value[idx]
    if (!target) return
    if (target.previewUrl) URL.revokeObjectURL(target.previewUrl)
    replyPendingImages.value = replyPendingImages.value.filter((_, i) => i !== idx)
  }

  function removePendingEmoji(idx) {
    replyPendingEmojis.value = replyPendingEmojis.value.filter((_, i) => i !== idx)
  }

  function markReplyImageFailed(placeholder) {
    const slotIndex = replyPendingImages.value.findIndex((item) => item.id === placeholder.id)
    if (slotIndex < 0) return
    const current = replyPendingImages.value[slotIndex]
    replyPendingImages.value.splice(slotIndex, 1, {
      pending: false,
      failed: true,
      id: current.id,
      previewUrl: current.previewUrl || '',
      mediaUrl: current.previewUrl || current.mediaUrl || '',
      file: current.file || placeholder.file,
    })
  }

  async function uploadOneReplyImage(placeholder, file) {
    const slotIndex = replyPendingImages.value.findIndex((item) => item.id === placeholder.id)
    if (slotIndex < 0) return false
    replyPendingImages.value.splice(slotIndex, 1, {
      ...replyPendingImages.value[slotIndex],
      pending: true,
      failed: false,
      mediaUrl: replyPendingImages.value[slotIndex].previewUrl
        || replyPendingImages.value[slotIndex].mediaUrl
        || '',
      file,
    })
    try {
      const res = await uploadChatImage(file, { silentBizCodes: [] })
      const url = typeof res?.data === 'string' ? res.data.trim() : ''
      const latestIndex = replyPendingImages.value.findIndex((item) => item.id === placeholder.id)
      if (latestIndex < 0) return false
      const latest = replyPendingImages.value[latestIndex]
      if (url) {
        if (latest.previewUrl) URL.revokeObjectURL(latest.previewUrl)
        replyPendingImages.value.splice(latestIndex, 1, {
          pending: false,
          failed: false,
          id: latest.id,
          previewUrl: '',
          mediaUrl: url,
        })
        return true
      }
      markReplyImageFailed({ ...latest, file })
      return false
    } catch {
      markReplyImageFailed({
        id: placeholder.id,
        previewUrl: replyPendingImages.value.find((item) => item.id === placeholder.id)?.previewUrl,
        mediaUrl: '',
        file,
      })
      return false
    }
  }

  async function retryPendingImage(idx) {
    const target = replyPendingImages.value[idx]
    if (!target?.failed || !target.file || target.pending) return
    const ok = await uploadOneReplyImage(target, target.file)
    if (!ok) ElMessage.warning('图片重试失败，请稍后再试')
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

    const remaining = replyPendingMediaRemaining()
    if (remaining <= 0) {
      ElMessage.warning(`图片和表情合计最多 ${REPLY_MEDIA_MAX} 个`)
      return
    }
    if (files.length > remaining) {
      ElMessage.warning(`图片和表情合计最多 ${REPLY_MEDIA_MAX} 个，还能添加 ${remaining} 个`)
      return
    }

    const validFiles = []
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
      validFiles.push(file)
    }
    if (!validFiles.length) return

    const placeholders = validFiles.map((file) => ({
      pending: true,
      failed: false,
      id: `pending-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      previewUrl: URL.createObjectURL(file),
      mediaUrl: '',
      file,
    }))
    placeholders.forEach((item) => {
      item.mediaUrl = item.previewUrl
    })
    replyPendingImages.value.push(...placeholders)

    // 逐张并行上传：每张独立完成/失败，避免整批卡在「上传中」且发送键长期灰掉
    const CONCURRENCY = 3
    let cursor = 0
    let failCount = 0
    const workers = Array.from({ length: Math.min(CONCURRENCY, placeholders.length) }, async () => {
      while (cursor < placeholders.length) {
        const index = cursor
        cursor += 1
        const placeholder = placeholders[index]
        const file = validFiles[index]
        const ok = await uploadOneReplyImage(placeholder, file)
        if (!ok) failCount += 1
      }
    })
    await Promise.all(workers)
    if (failCount > 0 && failCount === placeholders.length) {
      ElMessage.error('图片上传失败，可点图片重试')
    } else if (failCount > 0) {
      ElMessage.warning(`有 ${failCount} 张图片上传失败，可点图片重试`)
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
      // 已提示
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
    if (replyPendingMediaRemaining() <= 0) {
      ElMessage.warning(`图片和表情合计最多 ${REPLY_MEDIA_MAX} 个`)
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
  // 从首页卡片进入时先隐藏弹窗；等详情拿到帖内首图/视频首帧后再展开
  const expandFromCardPrep = ref(
    shouldReturnBackToFeed() && !!getFeedCardOrigin(route.params.id),
  )
  let openFromCardAnimStarted = false
  // 视频贴：展开/首帧就绪前只展示静帧，完成后再挂载播放器并自动播
  const videoPlayerReady = ref(false)
  const videoPosterUrl = ref('')

  function resolveOpenMorphImageUrl() {
    const firstGallery = String(articleGalleryUrls.value?.[0] || '').trim()
    if (firstGallery) return firstGallery
    return String(article.value?.coverImg || '').trim()
  }

  async function resolveVideoStillUrl(fallback = '') {
    const videoUrl = String(article.value?.videoUrl || '').trim()
    // 只接受真实截取的首帧；不要退回封面 封面会在交接时闪一下
    if (videoUrl) {
      const frame = await captureVideoFirstFrame(videoUrl)
      if (frame) return frame
    }
    return ''
  }

  let detailVideoPlayingWait = null

  function onDetailVideoPlaying() {
    if (detailVideoPlayingWait) {
      detailVideoPlayingWait()
      detailVideoPlayingWait = null
    }
  }

  function waitDetailVideoPlaying(timeoutMs = 2200) {
    return new Promise((resolve) => {
      let settled = false
      const done = () => {
        if (settled) return
        settled = true
        detailVideoPlayingWait = null
        resolve()
      }
      detailVideoPlayingWait = done
      setTimeout(done, timeoutMs)
    })
  }

  async function runOpenFromCardAnimation() {
    if (!expandFromCardPrep.value || openFromCardAnimStarted) return
    openFromCardAnimStarted = true
    const articleId = route.params.id
    const origin = getFeedCardOrigin(articleId)
    if (!origin) {
      expandFromCardPrep.value = false
      return
    }
    try {
      const isVideoOpen = isVideoArticle.value && !!articleVideoUrl.value
      if (isVideoOpen) {
        videoPlayerReady.value = false
        videoPosterUrl.value = ''
        resolveVideoStillUrl().then((frame) => {
          if (frame) videoPosterUrl.value = frame
        })
      } else {
        const heroUrl = resolveOpenMorphImageUrl() || origin.coverUrl || ''
        if (heroUrl) await preloadFeedOpenImage(heroUrl)
      }
      await nextTick()
      await new Promise((resolve) => {
        requestAnimationFrame(() => requestAnimationFrame(resolve))
      })
      await animateDetailDialogFromCard(origin)
      if (isVideoOpen) {
        expandFromCardPrep.value = false
        await nextTick()
        videoPlayerReady.value = true
        await nextTick()
        await waitDetailVideoPlaying()
      }
    } finally {
      expandFromCardPrep.value = false
      if (isVideoArticle.value && articleVideoUrl.value && !videoPlayerReady.value) {
        videoPlayerReady.value = true
      }
      removeFeedOpenMorphLayers()
    }
  }

  // 笔记相册 URL 与 article 正文独立
  const articleTags = ref([])

  const replyTargetLabel = computed(() => {
    if (!replyTarget.value) return ''
    const nickname = replyTarget.value.nickname || '用户'
    return replyTarget.value.showMention ? `回复给 @${nickname}` : `回复给 ${nickname}`
  })

  const articleGalleryUrls = ref([])
  const activeGalleryIndex = ref(0)
  const galleryAutoplayStopped = ref(false)
  const galleryStripRef = ref(null)
  const galleryStripOverflow = ref(false)
  const galleryStripFadeLeft = ref(false)
  const galleryStripFadeRight = ref(false)
  let galleryResizeObserver = null
  let galleryAutoplayTimer = null
  const GALLERY_AUTOPLAY_MS = 5000

  const activeGalleryUrl = computed(() => {
    const urls = articleGalleryUrls.value
    if (!urls.length) return ''
    const i = Math.min(Math.max(0, activeGalleryIndex.value), urls.length - 1)
    return urls[i] || ''
  })

  // 主图：相册优先，否则用封面 与首页瀑布流卡片一致
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
  const articleHlsUrl = computed(() => String(article.value?.hlsUrl || '').trim())
  const articleVideoTranscodeStatus = computed(() => {
    const status = Number(article.value?.videoTranscodeStatus)
    return Number.isFinite(status) ? status : 0
  })
  const detailVideoRef = ref(null)

  // 转码中只能播原片，转好了也不会自己切到 HLS，那行"流处理中"提示还会一直挂着。
  // 30 秒问一次，转好或超过 10 分钟就收工——不值得为此上 WebSocket
  const TRANSCODE_POLL_INTERVAL_MS = 30_000
  const TRANSCODE_POLL_MAX_MS = 10 * 60 * 1000
  let transcodePollTimer = null
  let transcodePollStartedAt = 0

  function stopTranscodePolling() {
    if (transcodePollTimer != null) {
      window.clearTimeout(transcodePollTimer)
      transcodePollTimer = null
    }
    transcodePollStartedAt = 0
  }

  async function pollTranscodeStatusOnce(articleId) {
    transcodePollTimer = null
    if (Date.now() - transcodePollStartedAt > TRANSCODE_POLL_MAX_MS) {
      stopTranscodePolling()
      return
    }
    try {
      const res = await getArticleDetail(articleId)
      // 只接当前这篇的结果：轮询期间用户可能已经翻到别的帖子了
      if (res?.code !== 0 || String(article.value?.id) !== String(articleId)) {
        stopTranscodePolling()
        return
      }
      const next = Number(res.data?.article?.videoTranscodeStatus)
      if (next === 1) {
        transcodePollTimer = window.setTimeout(
          () => pollTranscodeStatusOnce(articleId),
          TRANSCODE_POLL_INTERVAL_MS,
        )
        return
      }
      // 转好（或失败）了，只动这一个字段：整份替换会连带重置图集/关注等状态
      article.value = { ...article.value, videoTranscodeStatus: next }
      stopTranscodePolling()
    } catch {
      stopTranscodePolling()
    }
  }

  function startTranscodePollingIfNeeded(articleId) {
    stopTranscodePolling()
    if (!articleId || articleVideoTranscodeStatus.value !== 1) return
    transcodePollStartedAt = Date.now()
    transcodePollTimer = window.setTimeout(
      () => pollTranscodeStatusOnce(articleId),
      TRANSCODE_POLL_INTERVAL_MS,
    )
  }

  const isVideoArticle = computed(() => {
    if (Number(article.value?.mediaType) === 1) return true
    return !!articleVideoUrl.value && !articleGalleryUrls.value.length
  })

  const articleMusic = computed(() => {
    const a = article.value
    if (!a?.musicAudioUrl || isVideoArticle.value) return null
    return {
      title: a.musicTitle || a.musicKey || '帖子配乐',
      coverUrl: a.musicCoverUrl || '',
      audioUrl: a.musicAudioUrl,
    }
  })
  const musicPlaying = ref(false)
  const musicAudioRef = ref(null)
  const musicEqBars = [0, 1, 2, 3]

  function pauseDetailVideoForMusic() {
    detailVideoRef.value?.pausePlayback?.()
  }

  async function startArticleMusicPlayback() {
    const el = musicAudioRef.value
    if (!el || !articleMusic.value?.audioUrl) return false
    pauseDetailVideoForMusic()
    const url = articleMusic.value.audioUrl
    if (el.src !== url) {
      el.src = url
      el.load()
    }
    try {
      await el.play()
      musicPlaying.value = true
      return true
    } catch {
      musicPlaying.value = false
      return false
    }
  }

  function toggleArticleMusic() {
    const el = musicAudioRef.value
    if (!el || !articleMusic.value?.audioUrl) return
    if (musicPlaying.value) {
      el.pause()
      musicPlaying.value = false
      return
    }
    startArticleMusicPlayback()
  }

  async function autoPlayArticleMusic() {
    if (!articleMusic.value?.audioUrl) return
    await nextTick()
    await startArticleMusicPlayback()
  }

  function onMusicEnded() {
    musicPlaying.value = false
  }

  watch(articleMusic, () => {
    const el = musicAudioRef.value
    if (el) {
      el.pause()
      el.removeAttribute('src')
      el.load()
    }
    musicPlaying.value = false
  })

  // 图文详情页作者行：发布时间 · IP 属地
  const authorMetaText = computed(() => {
    const time = formatForumDateTimeShanghai(article.value?.createTime)
    const region = String(article.value?.ipRegion || '').trim()
    if (time && region) return `${time} · ${region}`
    return time || region || ''
  })

  function formatCommentMeta(createTime, ipRegion) {
    const time = formatCommentTimeShanghai(createTime)
    const region = String(ipRegion || '').trim()
    if (time && region) return `${time} · ${region}`
    return time || region || ''
  }

  function isOwnComment(item) {
    const uid = item?.user?.id
    const me = userStore.userInfo?.id
    if (uid == null || me == null) return false
    return Number(uid) === Number(me)
  }

  // 楼主自己发的一级回答不可采纳
  function isArticleAuthorReply(item) {
    const uid = item?.user?.id
    const authorId = author.value?.id
    if (uid == null || authorId == null) return false
    return Number(uid) === Number(authorId)
  }

  async function openContentReportDialog(title, targetType, targetId) {
    if (!(await ensureLoggedIn('举报需要登录')) || !targetId) return
    contentReportDialogTitle.value = title
    pendingContentReport.value = { targetType, targetId }
    contentReportDialogVisible.value = true
  }

  function reportArticle() {
    return openContentReportDialog('举报帖子', 'ARTICLE', article.value?.id)
  }

  function reportReply(item) {
    if (isOwnComment(item) || isArticleAuthorReply(item)) return
    return openContentReportDialog('举报评论', 'REPLY', item?.articleReply?.id)
  }

  function reportSubReply(payload) {
    return openContentReportDialog(
      '举报回复',
      'SUB_REPLY',
      payload?.subReplyId || payload?.subReply?.id || payload?.id,
    )
  }

  function reportDanmaku(item) {
    return openContentReportDialog('举报弹幕', 'DANMAKU', item?.id)
  }

  async function submitContentReport(reason) {
    if (!pendingContentReport.value || contentReportSubmitting.value) return
    contentReportSubmitting.value = true
    try {
      const response = await reportArticleContent({
        ...pendingContentReport.value,
        reason,
      })
      if (response.code !== 0) {

        return
      }
      contentReportDialogVisible.value = false
      pendingContentReport.value = null
      ElMessage.success('已收到举报，结果会通过消息中心通知')
    } finally {
      contentReportSubmitting.value = false
    }
  }

  const galleryPageLabel = computed(() => {
    const total = articleGalleryUrls.value.length
    if (total <= 0) return ''
    const current = Math.min(Math.max(0, activeGalleryIndex.value), total - 1) + 1
    return `${current} / ${total}`
  })

  const showGalleryNavArrows = computed(() => (
    !isVideoArticle.value && articleGalleryUrls.value.length >= 2
  ))

  const galleryCanGoPrev = computed(() => activeGalleryIndex.value > 0)

  const galleryCanGoNext = computed(() => (
    activeGalleryIndex.value < articleGalleryUrls.value.length - 1
  ))

  function formatCompactNumber(value) {
    const number = Math.max(0, Number(value) || 0)
    if (number >= 10000) return `${(number / 10000).toFixed(number >= 100000 ? 0 : 1)}万`
    if (number >= 1000) return `${(number / 1000).toFixed(1)}k`
    if (!Number.isInteger(number)) return number.toFixed(1)
    return String(number)
  }

  function stopGalleryAutoplay() {
    if (galleryAutoplayTimer) {
      clearInterval(galleryAutoplayTimer)
      galleryAutoplayTimer = null
    }
  }

  function stopGalleryAutoplayByUser() {
    galleryAutoplayStopped.value = true
    stopGalleryAutoplay()
  }

  function tickGalleryAutoplay() {
    const total = articleGalleryUrls.value.length
    if (total < 2 || isVideoArticle.value) return
    const next = activeGalleryIndex.value + 1
    activeGalleryIndex.value = next >= total ? 0 : next
  }

  function startGalleryAutoplay() {
    stopGalleryAutoplay()
    if (
      galleryAutoplayStopped.value
      || isVideoArticle.value
      || articleGalleryUrls.value.length < 2
      || document.hidden
    ) {
      return
    }
    galleryAutoplayTimer = window.setInterval(tickGalleryAutoplay, GALLERY_AUTOPLAY_MS)
  }

  function onGalleryVisibilityChange() {
    if (document.hidden) {
      stopGalleryAutoplay()
      return
    }
    startGalleryAutoplay()
  }

  // 缩略图条一屏放不下九张，翻到后面时当前项会滚出可视区，
  // 用户就只能靠角标知道自己在第几张了。这里让它跟着走。
  // 用 strip.scrollTo 而不是 scrollIntoView：后者会连带滚动祖先容器，
  // 详情页本身是个弹窗，会被拽歪
  function scrollActiveThumbIntoView() {
    const strip = galleryStripRef.value
    if (!strip) return
    const thumb = strip.children?.[activeGalleryIndex.value]
    if (!thumb) return
    const target = thumb.offsetLeft - (strip.clientWidth - thumb.clientWidth) / 2
    strip.scrollTo({ left: Math.max(0, target), behavior: 'smooth' })
  }

  // 鼠标停在图上说明正在细看，别把它翻走。
  // 移开后交给 startGalleryAutoplay 自己判断——用户主动翻过页就不再恢复
  function pauseGalleryAutoplayOnHover() {
    stopGalleryAutoplay()
  }

  function resumeGalleryAutoplayOnHover() {
    startGalleryAutoplay()
  }

  function scrollGalleryStripBy(deltaPx) {
    const el = galleryStripRef.value
    if (!el) return
    el.scrollBy({ left: deltaPx, behavior: 'smooth' })
  }

  function toggleAiSummaryCollapsed() {
    if (!aiSummaryCanExpand.value) return
    aiSummaryCollapsed.value = !aiSummaryCollapsed.value
    if (!aiSummaryCollapsed.value) {
      loadAiSummary()
    }
  }

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

  // 缩略图改走 style/thumb 之后，主图的原图不再被缩略图顺带缓存，
  // 切换时要现下载，el-image 在 src 变化时又会先清空再显示——就是那一下闪。
  // 提前把相邻两张的原图拉进浏览器缓存，切过去时基本是瞬时的
  const preloadedGalleryUrls = new Set()

  function preloadGalleryImage(url) {
    const target = String(url || '').trim()
    if (!target || preloadedGalleryUrls.has(target)) return
    preloadedGalleryUrls.add(target)
    const img = new Image()
    img.src = target
  }

  function preloadNeighborGalleryImages(index) {
    const urls = articleGalleryUrls.value
    if (!urls.length) return
    for (const offset of [1, -1, 2]) {
      const neighbor = index + offset
      if (neighbor >= 0 && neighbor < urls.length) {
        preloadGalleryImage(urls[neighbor])
      }
    }
  }

  watch(activeGalleryIndex, (index) => {
    nextTick(scrollActiveThumbIntoView)
    preloadNeighborGalleryImages(index)
  })

  watch(articleGalleryUrls, (urls) => {
    preloadedGalleryUrls.clear()
    if (urls?.length) preloadNeighborGalleryImages(activeGalleryIndex.value)
  })

  function setActiveGalleryIndex(index, byUser = false) {
    activeGalleryIndex.value = index
    if (byUser) stopGalleryAutoplayByUser()
  }

  function shiftGalleryIndex(delta) {
    const total = articleGalleryUrls.value.length
    if (total <= 0) return
    const next = activeGalleryIndex.value + delta
    if (next < 0 || next >= total) return
    activeGalleryIndex.value = next
    stopGalleryAutoplayByUser()
  }

  const mainImagePreviewVisible = ref(false)

  function openMainImagePreview() {
    if (!imagePreviewList.value.length) return
    mainImagePreviewVisible.value = true
  }

  function closeMainImagePreview() {
    mainImagePreviewVisible.value = false
  }

  async function downloadCurrentGalleryImage() {
    const url = mainDisplayImageUrl.value
    if (!url || isVideoArticle.value) return
    const name = guessImageFileName(url, `article-${article.value?.id || 'image'}`,
      activeGalleryIndex.value + 1)
    const ok = await downloadImageByUrl(url, name)
    if (!ok) {
      ElMessage.error('下载失败：图片跨域受限，请稍后重试或联系管理员配置 OSS 跨域')
    }
  }

  function reloadArticleDetail() {
    const id = route.params.id
    if (id) loadArticleDetail(id)
  }

  function browseOtherArticles() {
    // 关闭当前帖子详情，回到列表继续逛 不是重新加载
    closeDetailDialog()
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
      startGalleryAutoplay()
    })
    if (!urls?.length) {
      galleryStripOverflow.value = false
      galleryStripFadeLeft.value = false
      galleryStripFadeRight.value = false
      stopGalleryAutoplay()
    }
  })

  onMounted(() => {
    document.addEventListener('visibilitychange', onGalleryVisibilityChange)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', onGalleryVisibilityChange)
    stopGalleryAutoplay()
    galleryResizeObserver?.disconnect()
    galleryResizeObserver = null
    unbindReplyLoadObserver()
    if (shareCopiedTimer) {
      clearTimeout(shareCopiedTimer)
      shareCopiedTimer = null
    }
    stopAiSummaryPolling()
    stopTranscodePolling()
    stopTriplePressAnimation()
    if (summaryJustUpdatedTimer) {
      window.clearTimeout(summaryJustUpdatedTimer)
      summaryJustUpdatedTimer = null
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
    return Math.max(Number(article.value.replyCount) || 0, Number(replyTotal.value) || 0)
  })

  function resetDetailTransientState() {
    stopGalleryAutoplay()
    stopAiSummaryPolling()
    stopTranscodePolling()
    aiLoading.value = false
    detailVideoRef.value?.resetDanmakuEngine?.()
    const musicEl = musicAudioRef.value
    if (musicEl && !musicEl.paused) {
      try {
        musicEl.pause()
      } catch {
        // 忽略
      }
    }
    musicPlaying.value = false
    replyContent.value = ''
    replyTarget.value = null
    replyEmojiPanelOpen.value = false
    replySubmitting.value = false
    clearReplyPendingMedia()
    replyPageNum.value = 1
    replyTotal.value = 0
    replyLoadingMore.value = false
    replies.value = []
    pinnedReplies.value = []
    unbindReplyLoadObserver()
    subReplyRefreshTokens.value = {}
  }

  function syncVisitCountToFeed() {
    const id = article.value?.id
    if (id == null) return
    notifyFeedVisitCountUpdate(id, article.value?.visitCount)
  }

  async function loadArticleDetail(articleId) {
    if (articleId == null || articleId === '') return
    loading.value = true
    resetDetailTransientState()
    article.value = null
    contentExpanded.value = false
    aiSummary.value = ''
    aiSummaryIsHint.value = false
    aiSummaryCollapsed.value = true
    aiSummaryStatus.value = 'NOT_READY'
    aiSummaryCanExpand.value = false
    aiSummaryCanRegenerate.value = false
    galleryAutoplayStopped.value = false
    articleGalleryUrls.value = []
    articleTags.value = []
    activeGalleryIndex.value = 0
    videoPlayerReady.value = false
    videoPosterUrl.value = ''
    try {
      const res = await getArticleDetail(articleId)
      if (res.code === 0) {
        // 只有已发布的帖子能进详情。作者本人也一样：草稿和审核中的内容还没定稿，
        // 未通过 / 异常的原因已经在创作中心卡片和编辑页里说明了，不必再来一趟。
        // 这里挡的是直接输 URL 与旧链接 —— 详情接口是编辑页共用的，不能在后端拦，
        // 否则编辑页拿不到数据就没法提示"正在审核中"
        if (!canOpenArticleDetail(res.data.article?.status)) {
          ElMessage.info(articleDetailBlockedHint(res.data.article?.status))
          router.replace('/creative')
          return
        }
        article.value = res.data.article
        author.value = res.data.user
        board.value = res.data.board
        articleTags.value = Array.isArray(res.data.tags) ? res.data.tags : []
        isLiked.value = res.data.isLiked || false
        isOwner.value = res.data.isOwner || false
        isFavorited.value = res.data.isFavorited || false
        notInterestedArticleStore.syncFeedbackState(articleId, res.data.isNotInterested === true)
        articleGalleryUrls.value = Array.isArray(res.data.imageUrls) ? [...res.data.imageUrls] : []
        startTranscodePollingIfNeeded(articleId)
        await loadAuthorFollowState()
        await syncOwnerArticleStatus(articleId)
        await nextTick()
        updateGalleryStripState()
        bindGalleryStripObserver()
        await refreshAiSummaryState()
        // 详情首图/视频静帧就绪后再从封面位展开，避免闪切
        if (expandFromCardPrep.value) {
          await runOpenFromCardAnimation()
        } else if (isVideoArticle.value && articleVideoUrl.value) {
          // 非首页卡片进入：先备好海报/首帧，再挂载播放器自动播
          videoPosterUrl.value = await resolveVideoStillUrl()
          videoPlayerReady.value = true
        }
        if (!isVideoArticle.value && articleMusic.value?.audioUrl) {
          await autoPlayArticleMusic()
        }
      } else if (expandFromCardPrep.value) {
        expandFromCardPrep.value = false
      }
    } catch {
      if (expandFromCardPrep.value) {
        expandFromCardPrep.value = false
      }
    } finally {
      loading.value = false
    }
    await loadReplies(1)
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
      // 忽略
    }
  }

  function shouldReturnToProfile() {
    return route.query.from === 'profile'
  }

  function shouldReturnToCreative() {
    return route.query.from === 'creative'
  }

  function shouldReturnToHotSource() {
    return route.query.from === 'hot'
  }

  function shouldReturnToShellSource() {
    return shouldReturnToProfile() || shouldReturnToCreative() || shouldReturnToHotSource()
  }

  function getProfileReturnPath() {
    try {
      const raw = sessionStorage.getItem('profile-return-state')
      if (raw) {
        const state = JSON.parse(raw)
        if (state.profileUserId) return `/profile/${state.profileUserId}`
      }
    } catch {
      // 忽略
    }
    return '/profile'
  }

  function getShellSourceReturnPath() {
    if (shouldReturnToProfile()) return getProfileReturnPath()
    if (shouldReturnToCreative()) return '/creative'
    return getFeedReturnPath()
  }

  async function handleBeforeClose(done) {
    if (detailClosing) {
      done()
      return
    }
    detailClosing = true

    const articleId = route.params.id
    const fromHome = shouldReturnBackToFeed() && !shouldReturnToShellSource()
    const fromSearch = shouldReturnBackToSearch() && !shouldReturnToShellSource()
    const origin = fromHome ? getFeedCardOrigin(articleId) : null

    try {
      if (origin) {
        const heroUrl =
          resolveOpenMorphImageUrl() ||
          videoPosterUrl.value ||
          String(article.value?.coverImg || '').trim() ||
          origin.coverUrl ||
          ''
        await animateDetailDialogToCard(
          {
            ...origin,
            coverUrl: heroUrl,
          },
          { articleId, heroUrl },
        )
      }
    } catch {
      // 忽略
    } finally {
      done()
      detailClosing = false
      if (shouldReturnToShellSource()) {
        skipDialogClosedNav = true
        if (window.history.length > 1) {
          router.back()
        } else {
          router.replace(getShellSourceReturnPath())
        }
        return
      }
      if ((fromHome || fromSearch) && window.history.length > 1) {
        skipDialogClosedNav = true
        clearFeedNavigationState()
        const removeAfterEach = router.afterEach(() => {
          removeAfterEach()
          syncVisitCountToFeed()
          nextTick(() => restoreFeedScroll())
        })
        router.back()
      }
    }
  }

  function handleDialogClosed() {
    if (skipDialogClosedNav) {
      skipDialogClosedNav = false
      return
    }
    if (/^\/article\//.test(route.path)) {
      if (shouldReturnToShellSource()) {
        router.replace(getShellSourceReturnPath())
        return
      }
      if (shouldReturnBackToSearch()) {
        const returnPath = getFeedReturnPath()
        clearFeedNavigationState()
        router.replace(returnPath)
        return
      }
      const returnPath = getFeedReturnPath()
      clearFeedNavigationState()
      router.replace(returnPath).then(() => {
        if (returnPath === '/community') restoreFeedScroll()
      })
    }
  }

  function closeDetailDialog() {
    handleBeforeClose(() => {
      dialogOpen.value = false
    })
  }

  function goAuthorProfile() {
    goUserProfile(author.value?.id)
  }

  function goUserProfile(userId) {
    const uid = String(userId ?? '').trim()
    if (!/^\d+$/.test(uid) || /^0+$/.test(uid)) return
    skipDialogClosedNav = true
    clearFeedNavigationState()
    // 个人主页跳转不是 关闭详情并返回来源页 ，不能复用带返回动画的 closeDetailDialog
    dialogOpen.value = false
    router.push({ name: 'profile', params: { id: uid } })
  }

  function unbindReplyLoadObserver() {
    replyLoadObserver?.disconnect()
    replyLoadObserver = null
  }

  function resolveArticleScrollRoot() {
    const scrollbar = articleContentScrollRef.value
    if (!scrollbar) return null
    const wrap = scrollbar.wrapRef
    if (wrap?.value instanceof HTMLElement) return wrap.value
    if (wrap instanceof HTMLElement) return wrap
    return scrollbar.$el?.querySelector?.('.el-scrollbar__wrap') || null
  }

  function bindReplyLoadObserver() {
    unbindReplyLoadObserver()
    if (!replyHasMore.value) return
    const sentinel = replyLoadMoreSentinelRef.value
    if (!sentinel || typeof IntersectionObserver === 'undefined') return
    const root = resolveArticleScrollRoot()
    replyLoadObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          void loadMoreReplies()
        }
      },
      { root, rootMargin: '160px 0px', threshold: 0 },
    )
    replyLoadObserver.observe(sentinel)
  }

  async function loadReplies(page = 1, { append = false } = {}) {
    const articleId = route.params.id
    if (articleId == null || articleId === '') return
    if (append) {
      if (replyLoadingMore.value || !replyHasMore.value) return
      replyLoadingMore.value = true
    }
    const pageNum = Math.max(1, Number(page) || 1)
    try {
      const res = await getReplyList({
        articleId,
        pageNum,
        pageSize: replyPageSize.value,
      })
      if (res.code !== 0) return
      const raw = res.data
      const rows = unwrapPageRecords(raw).map((row) => ({
        ...row,
        liked: !!row.liked,
        subReplyCount: row.subReplyCount ?? 0,
      }))
      replyTotal.value = raw?.total != null ? Number(raw.total) : (append
        ? Math.max(Number(replyTotal.value) || 0, replies.value.length + rows.length)
        : rows.length)
      replyPageNum.value = pageNum
      if (append) {
        const seen = new Set(replies.value.map((item) => Number(item?.articleReply?.id)))
        const extra = rows.filter((item) => !seen.has(Number(item?.articleReply?.id)))
        replies.value = [...replies.value, ...extra]
      } else {
        replies.value = rows
      }
      if (article.value) {
        article.value.replyCount = Math.max(
          Number(article.value.replyCount) || 0,
          replyTotal.value,
        )
      }
    } finally {
      if (append) replyLoadingMore.value = false
      await nextTick()
      bindReplyLoadObserver()
    }
  }

  async function loadMoreReplies() {
    if (!replyHasMore.value || replyLoadingMore.value) return
    await loadReplies(replyPageNum.value + 1, { append: true })
  }

  function isAcceptedReply(item) {
    return !!item?.accepted
  }

  async function acceptAnswer(item) {
    if (!canAcceptAnswer.value || questionActionSaving.value) return
    if (isArticleAuthorReply(item)) return
    const replyId = item?.articleReply?.id
    if (!replyId || !article.value?.id) return
    if (item.accepted) return
    questionActionSaving.value = true
    try {
      const res = await acceptQuestionAnswer({
        articleId: article.value.id,
        replyId,
      })
      if (res.code === 0) {
        item.accepted = true
        ElMessage.success('已采纳')
      }
    } catch {
      // 请求层已统一展示错误
    } finally {
      questionActionSaving.value = false
    }
  }

  async function acceptSubAnswer(payload) {
    if (!canAcceptAnswer.value || questionActionSaving.value) return
    const subUserId = payload?.postUser?.id
    if (subUserId != null && author.value?.id != null && Number(subUserId) === Number(author.value.id)) {
      return
    }
    const subReplyId = payload?.subReply?.id || payload?.subReplyId
    if (!subReplyId || !article.value?.id) return
    if (payload?.accepted) return
    questionActionSaving.value = true
    try {
      const res = await acceptQuestionAnswer({
        articleId: article.value.id,
        subReplyId,
      })
      if (res.code === 0) {
        if (payload) payload.accepted = true
        ElMessage.success('已采纳')
      }
    } catch {
      // 忽略
    } finally {
      questionActionSaving.value = false
    }
  }

  async function toggleQuestionResolved() {
    if (!canToggleQuestionResolved.value || questionActionSaving.value || !article.value?.id) return
    const nextResolved = !questionResolved.value
    questionActionSaving.value = true
    try {
      const res = await setQuestionResolved({
        articleId: article.value.id,
        resolved: nextResolved,
      })
      if (res.code === 0) {
        article.value.questionStatus = nextResolved
          ? QUESTION_STATUS.RESOLVED
          : QUESTION_STATUS.WAITING
        ElMessage.success(nextResolved ? '已标记为已解决' : '已标记为未解决')
      }
    } catch {
      // 忽略
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
    // 逐条评论各自防重：连点会重复请求，点赞数是本地加减的，会被加错
    if (replyLikePending.value.has(replyId)) return
    replyLikePending.value = new Set(replyLikePending.value).add(replyId)
    try {
      const res = item.liked ? await unlikeReply(replyId) : await likeReply(replyId)
      if (res.code === 0) {
        item.liked = !item.liked
        const base = Number(item.articleReply.likeCount) || 0
        item.articleReply.likeCount = Math.max(0, base + (item.liked ? 1 : -1))
      }
    } finally {
      const next = new Set(replyLikePending.value)
      next.delete(replyId)
      replyLikePending.value = next
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
      }
    } catch {
      // 失败原因由响应拦截器统一提示
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

  async function handleShare() {
    if (!article.value?.id) return
    const url = `${window.location.origin}/article/${article.value.id}`
    // 只复制链接，不走 navigator.share —— 那会拉起系统分享面板/外部程序，
    // 长按三连结束时也会自动触发一次，很打断
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
    if (engagementSubmitting.value) return
    engagementSubmitting.value = true
    try {
      const res = isLiked.value 
        ? await unlikeArticle(article.value.id) 
        : await likeArticle(article.value.id)

      if (res.code === 0) {
        isLiked.value = !isLiked.value
        const currentLikeCount = Number(article.value.likeCount) || 0
        article.value.likeCount = Math.max(0, currentLikeCount + (isLiked.value ? 1 : -1))
        ElMessage.success(isLiked.value ? '已点赞' : '已取消')
      }
    } finally {
      engagementSubmitting.value = false
    }
  }

  function stopTriplePressAnimation() {
    if (triplePressFrame != null) {
      cancelAnimationFrame(triplePressFrame)
      triplePressFrame = null
    }
    triplePressActive.value = false
    triplePressProgress.value = 0
  }

  function tickTriplePress(now) {
    const elapsed = Math.max(0, now - triplePressStartedAt)
    triplePressProgress.value = Math.min(1, elapsed / 5000)
    if (triplePressProgress.value >= 1) {
      stopTriplePressAnimation()
      void completeTripleEngagement()
      return
    }
    triplePressFrame = requestAnimationFrame(tickTriplePress)
  }

  function startTriplePress(event) {
    if (event?.button != null && event.button !== 0) return
    if (engagementSubmitting.value) return
    event?.preventDefault?.()
    stopTriplePressAnimation()
    triplePointerId = event?.pointerId ?? null
    if (triplePointerId != null && event?.currentTarget?.setPointerCapture) {
      event.currentTarget.setPointerCapture(triplePointerId)
    }
    triplePressActive.value = true
    triplePressStartedAt = performance.now()
    triplePressFrame = requestAnimationFrame(tickTriplePress)
  }

  function releaseTriplePointer(event) {
    if (triplePointerId != null && event?.currentTarget?.hasPointerCapture?.(triplePointerId)) {
      try {
        event.currentTarget.releasePointerCapture(triplePointerId)
      } catch {
        // 指针已由浏览器释放时无需重复处理
      }
    }
    triplePointerId = null
  }

  function cancelTriplePress(event) {
    releaseTriplePointer(event)
    if (!triplePressActive.value) return
    stopTriplePressAnimation()
  }

  function finishTriplePress(event) {
    releaseTriplePointer(event)
    if (!triplePressActive.value) return
    const elapsed = Math.max(0, performance.now() - triplePressStartedAt)
    stopTriplePressAnimation()
    if (elapsed < 5000) {
      void handleLike()
    }
  }

  async function completeTripleEngagement() {
    if (!article.value?.id || engagementSubmitting.value) return
    engagementSubmitting.value = true
    try {
      if (!isLiked.value) {
        const likeRes = await likeArticle(article.value.id)
        if (likeRes.code !== 0) throw new Error(likeRes.message || '点赞失败')
        isLiked.value = true
        article.value.likeCount = (Number(article.value.likeCount) || 0) + 1
      }
      if (!isFavorited.value) {
        const favoriteRes = await saveArticleFavorite({ articleId: article.value.id, folderId: null })
        if (favoriteRes.code !== 0) throw new Error(favoriteRes.message || '收藏失败')
        isFavorited.value = true
        article.value.favoriteCount = (Number(article.value.favoriteCount) || 0) + 1
      }
      ElMessage.success('三连成功，感谢支持！')
      await handleShare()
    } catch {
      // 拦截器已弹出真实原因，这里不再重复提示
    } finally {
      engagementSubmitting.value = false
    }
  }

  function openNotInterestedDialog() {
    if (!article.value?.id || isOwner.value || isNotInterested.value) return
    notInterestedReasonCode.value = ''
    notInterestedReasonDetail.value = ''
    notInterestedDialogVisible.value = true
  }

  async function submitNotInterested() {
    if (!article.value?.id || notInterestedSaving.value) return
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
    if (!(await ensureLoggedIn('收藏需要登录'))) return
    if (!article.value?.id) return
    // 取消收藏此前没有防重：连点会重复请求，而收藏数是前端本地加减的，会被减多次
    if (favoriteSubmitting.value) return

    if (isFavorited.value) {
      favoriteSubmitting.value = true
      try {
        const res = await cancelArticleFavorite(article.value.id)
        if (res.code === 0) {
          isFavorited.value = false
          const fc = Number(article.value.favoriteCount) || 0
          article.value.favoriteCount = Math.max(0, fc - 1)
          ElMessage.success('已取消收藏')
        }
      } finally {
        favoriteSubmitting.value = false
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

      }
    } finally {
      favoriteSaving.value = false
    }
  }

  function onReplyKeydown(event) {
    if (event.shiftKey) return
    event.preventDefault()
    if (!canSubmitReply.value) return
    submitReply()
  }

  async function submitReply() {
    if (!(await ensureLoggedIn('评论需要登录'))) return
    if (blockIfMuted(userStore)) return
    if (replySubmitting.value) return
    // 这两处原本是静默 return：用户点了发送，界面纹丝不动，不知道为什么
    if (replyPendingImages.value.some((img) => img?.pending)) {
      ElMessage.warning('图片还在上传中，请稍候')
      return
    }
    const text = replyContent.value.trim()
    const mediaList = buildReplyMediaList()
    if (!text && !mediaList.length) {
      ElMessage.warning('说点什么再发送吧')
      return
    }
    // 失败的图片会被 buildReplyMediaList 跳过。不提醒的话，传 3 张失败 1 张，
    // 发出去只有 2 张，用户却以为都发了
    if (replyPendingImages.value.some((img) => img?.failed)) {
      ElMessage.warning('有图片上传失败，请重试或移除后再发送')
      return
    }
    replySubmitting.value = true
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
        if (!wasSub && res.data?.articleReply?.id != null) {
          pinnedReplies.value = [
            { ...res.data, liked: !!res.data.liked },
            ...pinnedReplies.value,
          ]
        }
        const subReplyId = wasSub ? replyTarget.value.replyId : null
        replyTarget.value = null
        clearReplyPendingMedia()
        await loadReplies(1)
        if (subReplyId != null) {
          subReplyRefreshTokens.value = {
            ...subReplyRefreshTokens.value,
            [subReplyId]: (subReplyRefreshTokens.value[subReplyId] || 0) + 1,
          }
        }
      }
    } catch (err) {
      // 失败原因由响应拦截器统一提示；这里再弹一次，弹的是 AxiosError 的
      // 原始 message（"Request failed with status code 400"），只会盖掉真正的原因
      if (apiErrorCode(err) === 1104) return
    } finally {
      replySubmitting.value = false
    }
  }

  async function refreshAiSummaryState() {
    if (!article.value?.id) return
    try {
      const res = await getArticleSummaryState(article.value.id)
      if (res.code !== 0 || !res.data) return
      applyAiSummaryState(res.data)
      if (res.data.status === 'PROCESSING' || res.data.status === 'NOT_READY') {
        startAiSummaryPolling()
      } else {
        stopAiSummaryPolling()
      }
    } catch {
      aiSummaryStatus.value = 'FAILED'
      aiSummaryCanRegenerate.value = true
    }
  }

  function applyAiSummaryState(state) {
    const wasProcessing = aiSummaryStatus.value === 'PROCESSING' || aiLoading.value
    const nextStatus = state.status || 'NOT_READY'
    aiSummaryStatus.value = nextStatus
    aiSummaryCanExpand.value = nextStatus === 'PROCESSING' ? false : state.canExpand === true
    aiSummaryCanRegenerate.value = state.canRegenerate === true
    aiLoading.value = nextStatus === 'PROCESSING'
    if (nextStatus === 'PROCESSING') {
      aiSummaryCollapsed.value = true
    } else if (wasProcessing && (nextStatus === 'READY' || nextStatus === 'TOO_SHORT')) {
      aiSummaryCollapsed.value = false
    }
    if (state.summary) {
      aiSummary.value = state.summary
      aiSummaryIsHint.value = nextStatus === 'TOO_SHORT' || isAiSummaryHintMessage(state.summary)
      nextTick(resizeAiSummaryArea)
    }
  }

  function startAiSummaryPolling() {
    if (aiSummaryPollTimer) return
    aiSummaryPollStartedAt = Date.now()
    aiSummaryPollTimer = window.setInterval(async () => {
      if (Date.now() - aiSummaryPollStartedAt >= 120000) {
        stopAiSummaryPolling()
        return
      }
      await refreshAiSummaryState()
    }, 5000)
  }

  function showSummaryJustUpdated() {
    summaryJustUpdated.value = true
    if (summaryJustUpdatedTimer) window.clearTimeout(summaryJustUpdatedTimer)
    summaryJustUpdatedTimer = window.setTimeout(() => {
      summaryJustUpdated.value = false
      summaryJustUpdatedTimer = null
    }, 4000)
  }

  function stopAiSummaryPolling() {
    if (aiSummaryPollTimer) {
      window.clearInterval(aiSummaryPollTimer)
      aiSummaryPollTimer = null
    }
  }

  async function loadAiSummary() {
    if (!article.value?.id || !aiSummaryCanExpand.value) return
    await refreshAiSummaryState()
    aiSummaryCollapsed.value = false
    await nextTick()
    resizeAiSummaryArea()
  }

  async function regenerateAiSummary() {
    if (!article.value?.id || !aiSummaryCanRegenerate.value || aiLoading.value) return
    if (!(await ensureLoggedIn('重新生成AI导读需要登录'))) return
    const previousCollapsed = aiSummaryCollapsed.value
    const previousCanExpand = aiSummaryCanExpand.value
    aiSummaryCollapsed.value = true
    aiSummaryCanExpand.value = false
    aiLoading.value = true
    try {
      const res = await regenerateArticleSummary(article.value.id)
      if (res.code !== 0) {
        aiSummaryCollapsed.value = previousCollapsed
        aiSummaryCanExpand.value = previousCanExpand
        return
      }
      applyAiSummaryState(res.data || {})
      // F) 冷却期内后端只是原样返回当前状态，没有真的重新生成。
      // 不弹 toast，改成标题右侧一行淡色小字，几秒后自动消失
      if (res.data?.cooldownHit) {
        showSummaryJustUpdated()
        aiSummaryCollapsed.value = previousCollapsed
        aiSummaryCanExpand.value = previousCanExpand
        return
      }
      startAiSummaryPolling()
    } catch {
      aiSummaryCollapsed.value = previousCollapsed
      aiSummaryCanExpand.value = previousCanExpand
    } finally {
      aiLoading.value = aiSummaryStatus.value === 'PROCESSING'
    }
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
    ArrowUp,
    ChatDotRound,
    Close,
    CollectionTag,
    MagicStick,
    Flag,
    Download,
    Compass,
    Headset,
    VideoPlay,
    VideoPause,
    Picture,
    PictureFilled,
    Promotion,
    RefreshRight,
    Share,
    View,
    ArticleDetailVideo,
    BorderGlow,
    CommentExpandableText,
    SubReplyArea,
    articleNotFoundImageUrl,
    aiLoading,
    aiSummary,
    aiSummaryStatus,
    aiSummaryCanExpand,
    aiSummaryCanRegenerate,
    contentReportDialogVisible,
    contentReportDialogTitle,
    contentReportSubmitting,
    aiSummaryAreaRef,
    aiSummaryCollapsed,
    aiSummaryIsHint,
    authorMetaText,
    activeGalleryIndex,
    galleryCanGoNext,
    galleryCanGoPrev,
    galleryPageLabel,
    ossThumbUrl,
    pauseGalleryAutoplayOnHover,
    resumeGalleryAutoplayOnHover,
    showGalleryNavArrows,
    shiftGalleryIndex,
    formatCompactNumber,
    scrollGalleryStripBy,
    toggleAiSummaryCollapsed,
    downloadCurrentGalleryImage,
    reloadArticleDetail,
    browseOtherArticles,
    activeGalleryUrl,
    mainDisplayImageUrl,
    imagePreviewList,
    mainImagePreviewVisible,
    openMainImagePreview,
    closeMainImagePreview,
    article,
    articleContentScrollRef,
    articleTags,
    articleGalleryUrls,
    articleVideoUrl,
    articleHlsUrl,
    articleVideoTranscodeStatus,
    articleMusic,
    musicPlaying,
    musicAudioRef,
    musicEqBars,
    toggleArticleMusic,
    onMusicEnded,
    detailVideoRef,
    isVideoArticle,
    videoPlayerReady,
    videoPosterUrl,
    onDetailVideoPlaying,
    author,
    addReplyShopEmoji,
    canSubmitReply,
    canAcceptAnswer,
    canToggleQuestionResolved,
    questionResolved,
    questionResolveHint,
    clearReplyTarget,
    confirmFavorite,
    contentExpanded,
    shouldCollapseContent,
    closeDetailDialog,
    defaultAvatar,
    dialogOpen,
    expandFromCardPrep,
    followSaving,
    isFollowingAuthor,
    isQuestion,
    isAcceptedReply,
    acceptAnswer,
    acceptSubAnswer,
    toggleQuestionResolved,
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
    startTriplePress,
    finishTriplePress,
    cancelTriplePress,
    triplePressActive,
    triplePressProgress,
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
    regenerateAiSummary,
    loading,
    loadFavoriteFolders,
    onGalleryStripScroll,
    onReplyEmojiPopoverShow,
    onReplyKeydown,
    onReplyImageFileChange,
    onReplyPackBarScroll,
    openCommentShopDetail,
    questionActionSaving,
    questionStatusClass,
    questionStatusLabel,
    renderedContent,
    renderCommentHtml,
    replies,
    replyHasMore,
    replyLoadMoreSentinelRef,
    replyLoadingMore,
    replyPageNum,
    replyPageSize,
    replyTotal,
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
    replySubmitting,
    visibleReplies,
    favoriteSubmitting,
    summaryJustUpdated,
    replyTarget,
    replyTargetLabel,
    replyVisiblePacks,
    removePendingEmoji,
    removePendingImage,
    retryPendingImage,
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
    formatCommentMeta,
    isOwnComment,
    isArticleAuthorReply,
    reportArticle,
    reportDanmaku,
    reportReply,
    reportSubReply,
    submitContentReport,
  }
}
