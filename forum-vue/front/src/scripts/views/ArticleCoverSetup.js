import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { openImageUploadLoading, validateLocalImageFile } from '@/utils/imageUploadFeedback'
import {
  Edit,
  Plus,
  Upload,
  Picture,
  MagicStick,
  InfoFilled,
  View,
  Refresh,
  Download,
  Document,
} from '@element-plus/icons-vue'
import { getArticleDetail, uploadCoverFile, updateArticleCoverByUrl } from '@/api/article'
import { aiCoverHints, aiImage } from '@/api/ai'
import { submitArticleForAuditWithPrompt } from '@/composables/useArticleAuditSubmit'
import { ARTICLE_STATUS, isArticleEditingLocked } from '@/utils/articleStatus'
import { useUserStore } from '@/stores/user'
import { COVER_IMAGE_QUALITY_OPTIONS } from '@/constants/aiModels'
import { dismissGptImageSlowToast, showGptImageSlowToast } from '@/utils/gptImageToast'

export const COVER_PROMPT_MAX = 200
export const IMAGE_MODEL_OPTIONS = COVER_IMAGE_QUALITY_OPTIONS

function stripHtml(html) {
  if (!html || typeof html !== 'string') return ''
  try {
    const d = document.createElement('div')
    d.innerHTML = html
    return (d.textContent || d.innerText || '').trim()
  } catch {
    return html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
  }
}

function revokeIfBlobUrl(url) {
  if (url && typeof url === 'string' && url.startsWith('blob:')) {
    try {
      URL.revokeObjectURL(url)
    } catch {
      /* ignore */
    }
  }
}

/** @param {string} url http(s) 或 data URL */
async function imageUrlToFile(url) {
  const res = await fetch(url)
  const blob = await res.blob()
  const mime = blob.type && blob.type !== 'application/octet-stream' ? blob.type : 'image/png'
  const ext = mime.includes('jpeg') ? 'jpg' : mime.includes('webp') ? 'webp' : 'png'
  return new File([blob], `ai-cover-${Date.now()}.${ext}`, { type: mime })
}

export function useArticleCoverSetup() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()

  const title = ref('')
  const articleContent = ref('')
  const coverPreview = ref('')
  const coverFile = ref(null)
  const articleStatus = ref(null)
  const processing = ref(false)

  const aiPrompt = ref('')
  const imageQuality = ref('normal')
  const hintsLoading = ref(false)
  const aiGenerating = ref(false)
  const hasAiGenerated = ref(false)

  const articleTextPlain = computed(() => {
    const raw = articleContent.value || ''
    return stripHtml(raw)
  })

  const isVip = computed(() => {
    if (Number(userStore.isAdmin) === 1) return true
    const t = Number(userStore.vipTier) || 0
    if (t <= 0) return false
    const expRaw = userStore.vipExpireAt
    if (!expRaw) return true
    const exp = new Date(expRaw).getTime()
    if (Number.isNaN(exp)) return true
    return Date.now() <= exp
  })

  const isPublished = computed(() => Number(articleStatus.value) === ARTICLE_STATUS.PUBLISHED)

  const promptLength = computed(() => (aiPrompt.value || '').length)

  const tipText = computed(() => {
    const s = Number(articleStatus.value)
    if (s === ARTICLE_STATUS.PENDING_AUDIT) return '当前帖子正在审核中，请等待结果。'
    if (s === ARTICLE_STATUS.PUBLISHED) {
      return '当前帖子已发布。更换封面并保存后，若需重新上架请按提示提交审核。'
    }
    return '选好封面后可提交审核，或仅保留草稿。'
  })

  const canRegenerate = computed(
    () => isVip.value && !!aiPrompt.value.trim() && !aiGenerating.value,
  )

  async function refreshStatus() {
    const res = await getArticleDetail(route.params.id)
    if (res.code === 0) {
      title.value = res.data.article?.title || ''
      articleContent.value = res.data.article?.content || ''
      const existing = res.data.article?.coverImg || ''
      if (existing && !coverFile.value) {
        coverPreview.value = existing
      }
      articleStatus.value = res.data.article?.status != null ? Number(res.data.article.status) : null
    }
  }

  onMounted(() => {
    if (userStore.isLoggedIn) {
      userStore.fetchUserInfo()
    }
    refreshStatus()
  })

  function setImageModel(value) {
    if (!isVip.value) return
    imageQuality.value = value
  }

  async function fetchCoverHints() {
    if (!isVip.value) {
      ElMessage.warning('AI 配图为会员专享（PRO / MAX）')
      return
    }
    const text = articleTextPlain.value
    if (!text) {
      ElMessage.warning('暂无正文内容，请先在编辑器中撰写正文')
      return
    }
    hintsLoading.value = true
    try {
      const res = await aiCoverHints({ articleText: text.slice(0, 12000) })
      const payload = res.data || {}
      const content = payload.content ?? payload.payload
      if (typeof content === 'string' && content.trim()) {
        aiPrompt.value = content.trim().slice(0, COVER_PROMPT_MAX)
        ElMessage.success('已填入推荐要点，可修改后再生成封面')
      } else {
        ElMessage.warning('未拿到配图要点，请手动填写画面描述')
      }
    } finally {
      hintsLoading.value = false
    }
  }

  async function generateAiCover() {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      return
    }
    if (!isVip.value) {
      ElMessage.warning('AI 生图为会员专享（PRO / MAX），请先开通会员')
      return
    }
    const prompt = aiPrompt.value.trim()
    if (!prompt) {
      ElMessage.warning('请先填写画面描述，或使用「推荐配图要点」')
      return
    }
    if (prompt.length > COVER_PROMPT_MAX) {
      ElMessage.warning(`描述词最多 ${COVER_PROMPT_MAX} 字`)
      return
    }
    aiGenerating.value = true
    const usePremium = imageQuality.value === 'premium'
    if (usePremium) showGptImageSlowToast()
    try {
      const res = await aiImage({ prompt, quality: imageQuality.value })
      const payload = res.data || {}
      const url = payload.url ?? payload.payload?.url
      if (!url) {
        ElMessage.error('未返回图片地址')
        return
      }
      revokeIfBlobUrl(coverPreview.value)
      const file = await imageUrlToFile(url)
      coverFile.value = file
      coverPreview.value = URL.createObjectURL(file)
      hasAiGenerated.value = true
      ElMessage.success('已生成封面并载入预览')
    } finally {
      if (usePremium) dismissGptImageSlowToast()
      aiGenerating.value = false
    }
  }

  function handleCoverChange(file) {
    revokeIfBlobUrl(coverPreview.value)
    coverFile.value = file.raw
    coverPreview.value = URL.createObjectURL(file.raw)
    hasAiGenerated.value = false
  }

  function triggerUpload(uploadRef) {
    uploadRef?.click?.()
  }

  async function downloadCoverImage() {
    if (!coverPreview.value) return
    try {
      let href = coverPreview.value
      let name = `cover-${Date.now()}.png`
      if (href.startsWith('http')) {
        const file = await imageUrlToFile(href)
        href = URL.createObjectURL(file)
        const ext = file.name.split('.').pop() || 'png'
        name = `cover-${Date.now()}.${ext}`
      }
      const a = document.createElement('a')
      a.href = href
      a.download = name
      a.rel = 'noopener'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      if (href.startsWith('blob:') && href !== coverPreview.value) {
        URL.revokeObjectURL(href)
      }
    } catch {
      ElMessage.error('下载失败，请稍后重试')
    }
  }

  async function uploadCoverIfNeeded(articleId) {
    if (!coverFile.value) return { ok: true, skipped: true }
    const pre = validateLocalImageFile(coverFile.value)
    if (!pre.ok) {
      ElMessage.warning(pre.message)
      return { ok: false }
    }
    const imgLoading = openImageUploadLoading(coverFile.value, '正在上传封面，请稍候…')
    try {
      const uploadRes = await uploadCoverFile(coverFile.value)
      if (uploadRes.code !== 0) {
        ElMessage.error(uploadRes.message || '封面上传失败')
        return { ok: false }
      }
      const bindRes = await updateArticleCoverByUrl(articleId, uploadRes.data)
      if (bindRes.code !== 0) {
        ElMessage.error(bindRes.message || '封面绑定失败')
        return { ok: false }
      }
      ElMessage.success('封面已更新')
      coverFile.value = null
      coverPreview.value = uploadRes.data
      return { ok: true }
    } finally {
      imgLoading.close()
    }
  }

  async function persistCoverThen(submitAudit) {
    if (processing.value) return
    const articleId = route.params.id
    processing.value = true
    try {
      if (isArticleEditingLocked(articleStatus.value)) {
        ElMessage.warning('帖子正在审核中，暂时无法修改')
        router.push(`/article/${articleId}/audit`)
        return
      }

      const upRes = await uploadCoverIfNeeded(articleId)
      if (!upRes.ok) return

      await refreshStatus()

      if (Number(articleStatus.value) === ARTICLE_STATUS.PUBLISHED && !submitAudit) {
        router.push(`/article/${articleId}`)
        return
      }

      if (!submitAudit) {
        ElMessage.success('已保留草稿，可在创作中心继续编辑')
        router.push('/creative')
        return
      }

      const audit = await submitArticleForAuditWithPrompt(articleId)
      if (!audit.ok) return
      const q = audit.taskId ? `?taskId=${encodeURIComponent(audit.taskId)}` : ''
      router.push(`/article/${articleId}/audit${q}`)
    } finally {
      processing.value = false
    }
  }

  async function saveDraftOnly() {
    await persistCoverThen(false)
  }

  async function finishAndSubmitAudit() {
    if (isArticleEditingLocked(articleStatus.value)) {
      await persistCoverThen(false)
      return
    }
    try {
      await ElMessageBox.confirm(
        '是否提交内容审核？通过后笔记将自动发布并在本站展示。',
        '提交审核',
        { confirmButtonText: '提交审核', cancelButtonText: '取消', type: 'info' },
      )
    } catch {
      return
    }
    await persistCoverThen(true)
  }

  return {
    COVER_PROMPT_MAX,
    IMAGE_MODEL_OPTIONS,
    Document,
    Download,
    Edit,
    InfoFilled,
    MagicStick,
    Picture,
    Plus,
    Refresh,
    Upload,
    View,
    aiGenerating,
    aiPrompt,
    articleStatus,
    articleTextPlain,
    canRegenerate,
    coverPreview,
    downloadCoverImage,
    fetchCoverHints,
    finishAndSubmitAudit,
    generateAiCover,
    handleCoverChange,
    hasAiGenerated,
    hintsLoading,
    imageQuality,
    isPublished,
    isVip,
    processing,
    promptLength,
    saveDraftOnly,
    setImageModel,
    tipText,
    title,
    triggerUpload,
  }
}
