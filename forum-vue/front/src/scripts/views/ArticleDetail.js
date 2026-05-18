import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Share, PictureFilled, CollectionTag, Close } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import { getArticleDetail, getAiSummary, getAuditStatus, getLatestLikers } from '@/api/article'
import { captureFeedScroll, restoreFeedScroll } from '@/utils/feedScrollRestore'
import { getReplyList, submitReply as apiSubmitReply } from '@/api/reply'
import { likeArticle, unlikeArticle } from '@/api/like'
import { cancelArticleFavorite, getMyFavoriteFolders, saveArticleFavorite } from '@/api/favorite'
import SubReplyArea from '@/components/article/SubReplyArea.vue'
import { marked } from 'marked'
import { DEFAULT_AVATAR } from '@/utils/constants'
import { sanitizeHtml } from '@/utils/security'
import { unwrapPageRecords } from '@/utils/apiData'
import { ARTICLE_STATUS } from '@/utils/articleStatus'
import aiIconUrl from '@/assets/svg/AI.svg?url'
import replyIconUrl from '@/assets/svg/回复.svg?url'
import sendIconUrl from '@/assets/svg/发送.svg?url'
import likersMenuListIconUrl from '@/assets/svg/列表.svg?url'
import emptyCommentIconUrl from '@/assets/svg/空评论.svg?url'
import '@/assets/styles/article.css'

export function useArticleDetail() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const defaultAvatar = DEFAULT_AVATAR

  const loading = ref(true)
  const article = ref(null)
  const author = ref(null)
  const board = ref(null)
  const isLiked = ref(false)
  const isOwner = ref(false)
  const isFavorited = ref(false)
  const aiSummary = ref('')
  const aiSummaryIsHint = ref(false)
  const aiLoading = ref(false)

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
  const detailCoverBg = ref('#ffffff')

  /** @type {Map<number, { openReplyTo?: (u: unknown) => Promise<void> }>} */
  const subReplyAreaByReplyId = new Map()

  function registerSubReplyAreaRef(replyId, el) {
    const id = Number(replyId)
    if (Number.isNaN(id)) return
    if (el) subReplyAreaByReplyId.set(id, el)
    else subReplyAreaByReplyId.delete(id)
  }

  const showLikersDialog = ref(false)
  const loadingLikers = ref(false)
  const latestLikers = ref([])

  const favoriteDialogVisible = ref(false)
  const favoriteFoldersLoading = ref(false)
  const favoriteFolders = ref([])
  const selectedFolderId = ref(null) // null -> 默认收藏夹；字符串避免 Long 精度丢失
  const favoriteSaving = ref(false)

  const dialogOpen = ref(true)

  /** 笔记相册 URL（与 article 正文独立） */
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
        buttonText: '查看审核进度',
        path: `/article/${id}/audit`,
      }
    }
    if (s === ARTICLE_STATUS.PENDING_AUDIT) {
      return {
        type: 'warning',
        title: '审核中',
        description: '内容正在由系统异步审核，通过后帖子会自动发布。',
        buttonText: '查看审核进度',
        path: `/article/${id}/audit`,
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
    aiSummary.value = ''
    aiSummaryIsHint.value = false
    articleGalleryUrls.value = []
    activeGalleryIndex.value = 0
    try {
      const res = await getArticleDetail(articleId)
      if (res.code === 0) {
        article.value = res.data.article
        author.value = res.data.user
        board.value = res.data.board
        isLiked.value = res.data.isLiked || false
        isOwner.value = res.data.isOwner || false
        isFavorited.value = res.data.isFavorited || false
        detailCoverBg.value = '#ffffff'
        articleGalleryUrls.value = Array.isArray(res.data.imageUrls) ? [...res.data.imageUrls] : []
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

  function handleDialogClosed() {
    router.replace('/').then(() => {
      restoreFeedScroll()
    })
  }

  function closeDetailDialog() {
    dialogOpen.value = false
  }

  function goAuthorProfile() {
    const uid = author.value?.id
    if (!uid) return
    closeDetailDialog()
    router.push(`/profile/${uid}`)
  }

  function goUserProfile(userId) {
    const uid = Number(userId)
    if (!Number.isFinite(uid) || uid <= 0) return
    closeDetailDialog()
    router.push(`/profile/${uid}`)
  }

  async function loadReplies() {
    const res = await getReplyList({ articleId: route.params.id, pageNum: 1, pageSize: 50 })
    if (res.code === 0) {
      replies.value = unwrapPageRecords(res.data)
      if (article.value) {
        article.value.replyCount = Math.max(Number(article.value.replyCount) || 0, replies.value.length)
      }
    }
  }

  async function handleLike() {
    if (!userStore.isLoggedIn) return router.push('/sign-in')
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

  async function loadFavoriteFolders() {
    favoriteFoldersLoading.value = true
    try {
      const res = await getMyFavoriteFolders()
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
    if (!userStore.isLoggedIn) return router.push('/sign-in')
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
    if (!userStore.isLoggedIn) return router.push('/sign-in')
    if (blockIfMuted(userStore)) return
    if (!replyContent.value.trim()) return
    try {
      const res = await apiSubmitReply({ articleId: article.value.id, content: replyContent.value })
      if (res.code === 0) {
        ElMessage.success('发送成功')
        replyContent.value = ''
        loadReplies()
      } else {
        ElMessage.error(res.message || '评论发送失败')
      }
    } catch (err) {
      if (err?.code === 1104) return
      ElMessage.error(err?.message || '评论发送失败')
    }
  }

  async function loadAiSummary() {
    aiLoading.value = true
    try {
      const res = await getAiSummary(article.value.id)
      if (res.code === 0) {
        const text = typeof res.data === 'string' ? res.data : (res.data?.summary ?? '')
        if (isSummaryLikelyArticleBody(text, article.value?.content)) {
          aiSummary.value = 'AI 返回内容与正文过于相似，请充实正文后再尝试智能导读。'
          aiSummaryIsHint.value = true
        } else {
          aiSummary.value = text
          aiSummaryIsHint.value = isAiSummaryHintMessage(text)
        }
      } else {
        ElMessage.error(res.message || '获取摘要失败')
      }
    } finally {
      aiLoading.value = false
    }
  }

  async function handleReplyTo(item) {
    if (!userStore.isLoggedIn) return router.push('/sign-in')
    const rid = Number(item?.articleReply?.id)
    const area = subReplyAreaByReplyId.get(rid)
    if (area?.openReplyTo) {
      await area.openReplyTo(item.user ?? null)
      return
    }
    ElMessage.warning('楼中楼未加载完成，请稍后重试')
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

  async function fetchLikers() {
    loadingLikers.value = true
    try {
      const res = await getLatestLikers(article.value.id)
      if (res.code === 0) {
        latestLikers.value = res.data || []
      }
    } finally {
      loadingLikers.value = false
    }
  }

  return {
    ChatDotRound,
    Close,
    CollectionTag,
    PictureFilled,
    Share,
    SubReplyArea,
    aiLoading,
    aiSummary,
    aiSummaryIsHint,
    activeGalleryIndex,
    activeGalleryUrl,
    article,
    articleGalleryUrls,
    author,
    confirmFavorite,
    closeDetailDialog,
    defaultAvatar,
    detailCoverBg,
    dialogOpen,
    fetchLikers,
    galleryStripFadeLeft,
    galleryStripFadeRight,
    galleryStripOverflow,
    galleryStripRef,
    goAuthorProfile,
    goUserProfile,
    handleDialogClosed,
    favoriteDialogVisible,
    favoriteFolders,
    favoriteFoldersLoading,
    favoriteSaving,
    handleLike,
    handleReplyTo,
    registerSubReplyAreaRef,
    isLiked,
    isOwner,
    isFavorited,
    isVipGold,
    latestLikers,
    likersMenuListIconUrl,
    emptyCommentIconUrl,
    loadAiSummary,
    loading,
    loadingLikers,
    loadFavoriteFolders,
    aiIconUrl,
    onGalleryStripScroll,
    ownerAuditNotice,
    renderedContent,
    replies,
    replyIconUrl,
    sendIconUrl,
    replyContent,
    replyCountDisplay,
    selectedFolderId,
    setActiveGalleryIndex,
    showLikersDialog,
    submitReply,
    toggleFavorite,
  }
}
