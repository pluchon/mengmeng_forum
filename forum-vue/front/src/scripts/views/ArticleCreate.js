import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog, iconConfirm } from '@/utils/appDialog'
import { sanitizeHtml, sanitizePlainTextAsHtml } from '@/utils/security'
import {
  Close,
  MagicStick,
  Picture,
  Plus,
  Upload,
  VideoCamera,
} from '@element-plus/icons-vue'
import { useBoardStore } from '@/stores/board'
import { useUserStore } from '@/stores/user'
import { blockIfMuted } from '@/utils/userMute'
import {
  createDraft,
  updateArticle,
  getArticleDetail,
  uploadCoverFile,
  updateArticleCoverByUrl,
  uploadArticleImage,
  uploadArticleImages,
  replaceArticleImages,
  uploadArticleVideo,
  setArticleVideo,
  clearArticleVideo,
  setArticleMusic,
  clearArticleMusic,
} from '@/api/article'
import { submitArticleForAuditWithPrompt } from '@/composables/useArticleAuditSubmit'
import { aiArticleCover } from '@/api/ai'
import { extractApiErrorMessage } from '@/api/httpError'
import { ARTICLE_STATUS, isArticleEditingLocked } from '@/utils/articleStatus'
import WangEditor from '@/components/common/WangEditor.vue'
import { marked } from 'marked'
import { stripSingleOuterParagraph } from '@/utils/htmlNormalize'
import { ARTICLE_TYPE } from '@/utils/articleQuestion'
import { COVER_IMAGE_QUALITY_OPTIONS } from '@/constants/aiModels'
import {
  openImageUploadLoading,
  validateLocalImageFile,
  validateLocalImageFileMagic,
} from '@/utils/imageUploadFeedback'
import '@/assets/styles/editor.css'

const MAX_ARTICLE_GALLERY = 15
// 与后端 Constant.ARTICLE_GALLERY_MIN_CONTENT_LEN 保持一致：有相册图时正文纯文本至少 10 字
const GALLERY_MIN_CONTENT_LEN = 10

const GALLERY_UPLOAD_GAP_MS = 800
const GALLERY_UPLOAD_RETRY_MS = 1200

export function useArticleCreate() {
  const route = useRoute()
  const router = useRouter()
  const boardStore = useBoardStore()
  const userStore = useUserStore()

  const isEdit = computed(() => !!route.params.id)
  const submitting = ref(false)
  const editorMode = ref('rich')
  const aiWriting = ref(false)
  const selectedBoard = ref([])

  const form = reactive({
    boardId: '',
    title: '',
    content: '',
    contentType: 0,
    articleType: ARTICLE_TYPE.NORMAL,
    coverImg: ''
  })

  // 封面选完即传：上传本身不需要 articleId，只有绑定需要。
  // 早传的真正价值不是"发布变快"，而是把 AI 审图的驳回提前到选图那一刻，
  // 而不是让人写完整篇文章、点了发布才被告知封面不合规
  const coverPreview = ref('')
  const coverUploading = ref(false)
  // 审核未通过时把理由带到编辑页，和创作中心卡片、站内信是同一句话，
  // 免得用户只知道"没过"却不知道要改什么
  const rejectReason = ref('')
  const coverInputRef = ref(null)
  const coverImageQuality = ref('normal')
  const coverAiGenerating = ref(false)
  const tagAiGenerating = ref(false)
  const mdFileInput = ref(null)
  const mdTextareaRef = ref(null)
  // 笔记相册 article_image ，与正文独立；顺序即展示顺序
  const galleryUrls = ref([])
  // 媒体模式：相册 / 视频 二选一
  const mediaMode = ref('gallery') // gallery | video
  // 单视频 URL 服务端落库到 article.video_url
  const videoUrl = ref('')
  const videoUploading = ref(false)
  const videoUploadProgress = ref(0)
  const videoUploadError = ref('')
  // 失败后留住文件对象，重试就不用让用户重新选一遍
  const lastVideoFile = ref(null)
  const canRetryVideoUpload = computed(() => !videoUploading.value && !!lastVideoFile.value)
  let videoUploadAbort = null
  let pendingVideoUpload = null
  const galleryUploading = ref(false)
  const galleryPendingCount = ref(0)
  const tagIds = ref([])
  const galleryInputRef = ref(null)
  const videoInputRef = ref(null)
  const galleryItemsRef = ref(null)
  const galleryStripOverflow = ref(false)
  const galleryStripFadeLeft = ref(false)
  let galleryResizeObserver = null
  const selectedMusic = ref(null)
  const musicHallOpen = ref(false)

  const canAddGallery = computed(() => galleryUrls.value.length < MAX_ARTICLE_GALLERY)
  const imageModelOptions = computed(() => COVER_IMAGE_QUALITY_OPTIONS)

  const isVip = computed(() => {
    if (Number(userStore.isAdmin) === 1) return true
    const tier = Number(userStore.vipTier) || 0
    if (tier <= 0) return false
    if (!userStore.vipExpireAt) return true
    const expireAt = new Date(userStore.vipExpireAt).getTime()
    return Number.isNaN(expireAt) || Date.now() <= expireAt
  })

  const mdUndoStack = ref([])
  const mdRedoStack = ref([])
  let mdUndoGuard = false

  function updateGalleryStripState() {
    const el = galleryItemsRef.value
    if (!el) return
    const overflow = el.scrollWidth > el.clientWidth + 2
    const fadeLeft = overflow && el.scrollLeft > 4
    galleryStripOverflow.value = overflow
    galleryStripFadeLeft.value = fadeLeft
  }

  function scrollGalleryToEnd() {
    const el = galleryItemsRef.value
    if (!el) return
    el.scrollLeft = el.scrollWidth
    updateGalleryStripState()
  }

  function bindGalleryOverflowWatch() {
    galleryResizeObserver?.disconnect()
    const el = galleryItemsRef.value
    if (!el || typeof ResizeObserver === 'undefined') return
    galleryResizeObserver = new ResizeObserver(() => updateGalleryStripState())
    galleryResizeObserver.observe(el)
  }

  function resetGalleryStripState() {
    galleryItemsRef.value = null
    galleryStripOverflow.value = false
    galleryStripFadeLeft.value = false
    galleryResizeObserver?.disconnect()
    galleryResizeObserver = null
  }

  function buildArticlePayload() {
    const content =
      editorMode.value === 'rich'
        ? stripSingleOuterParagraph(form.content)
        : form.content
    return {
      boardId: form.boardId,
      title: form.title,
      content,
      contentType: Number(form.contentType) || 0,
      articleType: Number(form.articleType) === ARTICLE_TYPE.QUESTION
        ? ARTICLE_TYPE.QUESTION
        : ARTICLE_TYPE.NORMAL,
      coverImg: form.coverImg,
      tagIds: [...tagIds.value],
    }
  }

  function revokeCoverPreviewIfNeeded() {
    if (coverPreview.value?.startsWith('blob:')) {
      URL.revokeObjectURL(coverPreview.value)
    }
  }

  function resolveAiImageUrl(payload) {
    if (typeof payload === 'string') return payload
    if (!payload || typeof payload !== 'object') return ''
    return payload.url || payload.imageUrl || payload.image_url || ''
  }

  // 图在选择时已经传好了，这里只剩一次轻量的绑定
  async function runCoverUploadToArticle(articleId) {
    const previewUrl = String(coverPreview.value || '').trim()
    if (!previewUrl || previewUrl === form.coverImg) return { ok: true }
    const bindRes = await updateArticleCoverByUrl(articleId, previewUrl)
    if (bindRes.code === 0) {
      form.coverImg = previewUrl
      return { ok: true }
    }
    ElMessage.error(bindRes.message || '封面绑定失败')
    return { ok: false }
  }

  watch(galleryUrls, () => {
    nextTick(scrollGalleryToEnd)
  }, { deep: true })

  watch(editorMode, () => {
    nextTick(() => {
      scrollGalleryToEnd()
      bindGalleryOverflowWatch()
    })
  })

  // 编辑内容变化后延迟暂存，避免每敲一个字就写一次 localStorage
  watch(() => [form.title, form.content, form.boardId], () => {
    if (localDraftSaveTimer) window.clearTimeout(localDraftSaveTimer)
    localDraftSaveTimer = window.setTimeout(saveLocalDraft, 800)
  })

  let lastMdContent = form.content
  watch(
    () => form.content,
    (val) => {
      if (editorMode.value !== 'markdown' || mdUndoGuard) {
        lastMdContent = val
        return
      }
      if (val !== lastMdContent) {
        mdUndoStack.value.push(lastMdContent)
        if (mdUndoStack.value.length > 80) mdUndoStack.value.shift()
        mdRedoStack.value = []
        lastMdContent = val
      }
    },
  )

  onBeforeUnmount(() => {
    galleryResizeObserver?.disconnect()
    revokeCoverPreviewIfNeeded()
    window.removeEventListener('beforeunload', onBeforeUnload)
    if (localDraftSaveTimer) window.clearTimeout(localDraftSaveTimer)
  })

  onMounted(async () => {
    if (blockIfMuted(userStore)) {
      router.replace('/community')
      return
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    nextTick(() => {
      if (editorMode.value === 'markdown') {
        scrollGalleryToEnd()
        bindGalleryOverflowWatch()
      }
    })

    await boardStore.fetchCategoryList()

    if (isEdit.value) {
      const res = await getArticleDetail(route.params.id)
      if (res.code === 0) {
        const a = res.data.article
        if (isArticleEditingLocked(a.status)) {
          ElMessage.info('该帖子正在审核中，请稍候')
          router.replace('/community')
          return
        }
        const ct = Number(a.contentType) || 0
        Object.assign(form, {
          boardId: a.boardId,
          title: a.title,
          content: a.content,
          contentType: ct,
          articleType: Number(a.articleType) === ARTICLE_TYPE.QUESTION
            ? ARTICLE_TYPE.QUESTION
            : ARTICLE_TYPE.NORMAL,
          coverImg: a.coverImg || '',
        })
        editorMode.value = ct === 1 ? 'markdown' : 'rich'
        coverPreview.value = a.coverImg
        // 只有"审核未通过"才带理由：审核异常属于系统侧问题，写原因没有参考价值
        rejectReason.value = Number(a.status) === ARTICLE_STATUS.REJECTED
          ? String(a.auditResultMessage || '').trim()
          : ''
        galleryUrls.value = Array.isArray(res.data.imageUrls) ? [...res.data.imageUrls] : []
        videoUrl.value = a.videoUrl || ''
        const isVideoPost = Number(a.mediaType) === 1 || Boolean(String(a.videoUrl || '').trim())
        mediaMode.value = isVideoPost ? 'video' : 'gallery'
        if (a.musicAudioUrl || a.musicKey) {
          selectedMusic.value = {
            musicKey: a.musicKey || '',
            title: a.musicTitle || a.musicKey || '已选配乐',
            coverUrl: a.musicCoverUrl || '',
            audioUrl: a.musicAudioUrl || '',
            lrcUrl: a.musicLrcUrl || '',
          }
        } else {
          selectedMusic.value = null
        }
        const tags = Array.isArray(res.data.tags) ? res.data.tags : []
        tagIds.value = tags.map((t) => t.id).filter(Boolean)

        if (form.boardId) {
          boardStore.categoryList.forEach((cat) => {
            if (cat.boardList?.some((board) => board.id === form.boardId)) {
              selectedBoard.value = [cat.category.id, form.boardId]
            }
          })
        }
      }
    }

    // 恢复上次没保存完的内容。放在最后：编辑已有帖子时也可能有更新的本地暂存
    if (restoreLocalDraft()) {
      if (form.boardId) {
        boardStore.categoryList.forEach((cat) => {
          if (cat.boardList?.some((board) => board.id === form.boardId)) {
            selectedBoard.value = [cat.category.id, form.boardId]
          }
        })
      }
      ElMessage.info('已恢复上次未保存的内容')
    }
  })

  async function setMediaMode(mode) {
    if (mediaMode.value === mode) return
    if (mode === 'gallery' && videoUrl.value) {
      try {
        await confirmDialog('切换到相册将移除已上传的视频，是否继续？', '切换媒体类型', {
          type: 'warning',
          confirmButtonText: '继续',
          cancelButtonText: '取消',
        })
      } catch {
        return
      }
      videoUrl.value = ''
    } else if (mode === 'video' && galleryUrls.value.length) {
      try {
        await confirmDialog('切换到视频将清空相册图片，是否继续？', '切换媒体类型', {
          type: 'warning',
          confirmButtonText: '继续',
          cancelButtonText: '取消',
        })
      } catch {
        return
      }
      galleryUrls.value = []
    }
    mediaMode.value = mode
  }

  // 级联选择器处理
  const cascaderOptions = computed(() => {
    return boardStore.categoryList.map(cat => ({
      label: cat.category.name,
      value: cat.category.id,
      children: cat.boardList?.map(board => ({
        label: board.name,
        value: board.id
      })) || []
    }))
  })

  function handleBoardChange(val) {
    if (!Array.isArray(val) || val.length < 2) {
      form.boardId = ''
      tagIds.value = []
      if (val?.length === 1) {
        ElMessage.warning('请选择具体版块，不能只选分类')
      }
      return
    }
    const next = val[val.length - 1]
    if (next !== form.boardId) {
      tagIds.value = []
    }
    form.boardId = next
  }

  // 模式切换
  function switchMode(mode) {
    form.contentType = mode === 'markdown' ? 1 : 0
  }

  // 两个编辑器共用同一个 form.content，中间不做任何格式转换：
  // Markdown 内容被富文本编辑器接手后会被规范化成 HTML 并写回，切回来就是一堆标签。
  // 与其让内容被悄悄改坏，不如明说会清空，由用户决定
  async function setEditorMode(mode) {
    if (aiWriting.value) return
    if (editorMode.value === mode) return
    if (String(form.content || '').trim()) {
      const target = mode === 'markdown' ? 'Markdown' : '富文本'
      const ok = await iconConfirm({
        title: '切换编辑器会清空正文',
        message: `两种编辑器的格式不通用，切换到${target}后当前正文会被清空，请先自行备份。`,
        confirmText: '清空并切换',
        cancelText: '取消',
        danger: true,
      }).catch(() => false)
      if (!ok) return
      form.content = ''
      mdUndoStack.value = []
      mdRedoStack.value = []
    }
    editorMode.value = mode
    switchMode(mode)
    nextTick(() => {
      scrollGalleryToEnd()
      bindGalleryOverflowWatch()
    })
  }

  function onMdKeydown(e) {
    if (editorMode.value !== 'markdown') return
    const isMod = e.ctrlKey || e.metaKey
    if (!isMod) return
    const key = String(e.key || '').toLowerCase()
    if (key === 'z' && !e.shiftKey) {
      e.preventDefault()
      e.stopPropagation()
      if (!mdUndoStack.value.length) return
      mdUndoGuard = true
      mdRedoStack.value.push(form.content)
      form.content = mdUndoStack.value.pop()
      lastMdContent = form.content
      mdUndoGuard = false
      return
    }
    if ((key === 'z' && e.shiftKey) || key === 'y') {
      e.preventDefault()
      e.stopPropagation()
      if (!mdRedoStack.value.length) return
      mdUndoGuard = true
      mdUndoStack.value.push(form.content)
      form.content = mdRedoStack.value.pop()
      lastMdContent = form.content
      mdUndoGuard = false
    }
  }

  function applyAiContent(text) {
    form.content = text || ''
  }

  function setAiWriting(value) {
    aiWriting.value = Boolean(value)
  }

  function setTagAiGenerating(value) {
    tagAiGenerating.value = Boolean(value)
  }

  function openCoverPicker() {
    coverInputRef.value?.click()
  }

  async function onCoverFileSelected(event) {
    const file = event.target?.files?.[0]
    event.target.value = ''
    if (!file || coverUploading.value) return
    // 每次选图都要真的写 OSS 并过一次 AI 审图，先用魔数校验挡掉明显不合格的，别白花钱
    const pre = await validateLocalImageFileMagic(file)
    if (!pre.ok) {
      ElMessage.warning(pre.message)
      return
    }
    coverUploading.value = true
    const loading = openImageUploadLoading(file, '正在上传封面，请稍候…')
    try {
      const uploadRes = await uploadCoverFile(file)
      revokeCoverPreviewIfNeeded()
      coverPreview.value = uploadRes.data
      ElMessage.success('封面已就绪')
    } catch (error) {
      ElMessage.error(extractApiErrorMessage(error) || '封面上传失败，请重试')
    } finally {
      loading.close()
      coverUploading.value = false
    }
  }

  function clearCover() {
    if (coverUploading.value) return
    revokeCoverPreviewIfNeeded()
    coverPreview.value = ''
    form.coverImg = ''
  }

  function setCoverImageQuality(value) {
    if (imageModelOptions.value.some((option) => option.value === value)) {
      coverImageQuality.value = value
    }
  }

  async function generateAiCover() {
    if (coverAiGenerating.value) return
    if (!isVip.value) {
      ElMessage.warning('AI 配图为会员专享（PRO / MAX）')
      return
    }
    const content = String(form.content || '').trim()
    if (!content) {
      ElMessage.warning('请先写一点正文，再生成封面')
      return
    }
    coverAiGenerating.value = true
    try {
      const res = await aiArticleCover({
        title: String(form.title || '').trim(),
        content,
        editorMode: editorMode.value,
        quality: coverImageQuality.value,
        clientRequestId: globalThis.crypto?.randomUUID
          ? globalThis.crypto.randomUUID()
          : `cover-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      })
      const imageUrl = resolveAiImageUrl(res?.data)
      if (!imageUrl) {
        ElMessage.error(res?.message || '封面生成失败')
        return
      }
      revokeCoverPreviewIfNeeded()
      coverPreview.value = imageUrl
      ElMessage.success('已生成封面，提交时会自动保存')
    } catch (error) {
      ElMessage.error(extractApiErrorMessage(error, '封面生成失败'))
    } finally {
      coverAiGenerating.value = false
    }
  }

  function bindGalleryItemsRef(el) {
    galleryItemsRef.value = el
    if (!el) return
    nextTick(() => {
      scrollGalleryToEnd()
      bindGalleryOverflowWatch()
      updateGalleryStripState()
    })
  }

  function openGalleryPicker() {
    if (galleryUploading.value) return
    galleryInputRef.value?.click()
  }

  function removeGalleryAt(index) {
    if (index < 0 || index >= galleryUrls.value.length) return
    galleryUrls.value.splice(index, 1)
    nextTick(scrollGalleryToEnd)
  }

  async function onGalleryFilesSelected(e) {
    const raw = e.target?.files
    if (!raw?.length) return
    if (galleryUploading.value) return
    const files = Array.from(raw)
    e.target.value = ''
    if (mediaMode.value === 'video') {
      ElMessage.warning('当前为视频模式，请切换到相册后再上传图片')
      return
    }
    const room = MAX_ARTICLE_GALLERY - galleryUrls.value.length
    if (room <= 0) {
      ElMessage.warning(`相册最多 ${MAX_ARTICLE_GALLERY} 张`)
      return
    }
    const take = files.slice(0, room)
    if (files.length > take.length) {
      ElMessage.warning(`最多再添加 ${room} 张，已自动截取前 ${room} 张`)
    }
    const validFiles = []
    for (const file of take) {
      const pre = await validateLocalImageFileMagic(file)
      if (!pre.ok) {
        ElMessage.warning(`${file.name}：${pre.message}`)
      } else {
        validFiles.push(file)
      }
    }
    if (!validFiles.length) return

    galleryPendingCount.value = validFiles.length
    galleryUploading.value = true
    let okCount = 0
    let failCount = 0
    let lastError = ''
    try {
      if (validFiles.length === 1) {
        try {
          const res = await uploadArticleImage(validFiles[0], { silentHttpError: true })
          if (res.code === 0 && res.data) {
            galleryUrls.value.push(String(res.data))
            okCount = 1
            nextTick(scrollGalleryToEnd)
          } else {
            failCount = 1
            lastError = res.message || '图片上传失败'
          }
        } catch (err) {
          failCount = 1
          lastError = extractApiErrorMessage(err, '图片上传异常')
        }
      } else {
        try {
          const res = await uploadArticleImages(validFiles, { silentHttpError: true })
          if (res.code === 0 && res.data) {
            const successList = Array.isArray(res.data.success) ? res.data.success : []
            const failedList = Array.isArray(res.data.failed) ? res.data.failed : []
            successList
              .slice()
              .sort((a, b) => Number(a.index) - Number(b.index))
              .forEach((item) => {
                if (item?.url) {
                  galleryUrls.value.push(String(item.url))
                  okCount += 1
                }
              })
            failCount = failedList.length
            if (failedList[0]?.reason) lastError = String(failedList[0].reason)
            if (okCount > 0) nextTick(scrollGalleryToEnd)
          } else {
            failCount = validFiles.length
            lastError = res.message || '图片上传失败'
          }
        } catch (err) {
          failCount = validFiles.length
          lastError = extractApiErrorMessage(err, '图片上传异常')
        }
      }
      if (okCount > 0 && failCount === 0) {
        ElMessage.success(validFiles.length > 1 ? `已成功上传 ${okCount} 张图片` : '相册图片上传完成')
      } else if (okCount > 0) {
        ElMessage.warning(`已上传 ${okCount} 张，${failCount} 张失败：${lastError}`)
      } else {
        ElMessage.error(lastError || '图片上传失败')
      }
    } finally {
      galleryPendingCount.value = 0
      galleryUploading.value = false
    }
  }

  async function waitForPendingVideoUpload() {
    if (pendingVideoUpload) {
      await pendingVideoUpload
    }
  }

  async function syncGalleryToServer(articleId) {
    const id = Number(articleId)
    if (!id || Number.isNaN(id)) return { ok: false }
    if (mediaMode.value === 'video') {
      if (!videoUrl.value) {
        ElMessage.warning('请先上传视频')
        return { ok: false }
      }
      try {
        const bind = await setArticleVideo(id, videoUrl.value)
        if (bind.code === 0) return { ok: true }
        ElMessage.error(bind.message || '视频绑定失败')
        return { ok: false }
      } catch {
        ElMessage.error('视频绑定异常')
        return { ok: false }
      }
    }
    try {
      await clearArticleVideo(id)
      const res = await replaceArticleImages({
        articleId: id,
        imageUrls: [...galleryUrls.value],
      })
      if (res.code === 0) return { ok: true }
      ElMessage.error(res.message || '相册保存失败')
      return { ok: false }
    } catch {
      ElMessage.error('相册保存异常')
      return { ok: false }
    }
  }

  async function syncMusicToServer(articleId) {
    const id = Number(articleId)
    if (!id || Number.isNaN(id)) return { ok: false }
    try {
      if (!selectedMusic.value?.audioUrl) {
        const res = await clearArticleMusic(id)
        if (res.code === 0) return { ok: true }
        ElMessage.error(res.message || '清除配乐失败')
        return { ok: false }
      }
      const track = selectedMusic.value
      const res = await setArticleMusic({
        articleId: id,
        musicKey: track.musicKey,
        musicTitle: track.title || track.musicKey,
        musicCoverUrl: track.coverUrl || '',
        musicAudioUrl: track.audioUrl,
        musicLrcUrl: track.lrcUrl || '',
      })
      if (res.code === 0) return { ok: true }
      ElMessage.error(res.message || '配乐绑定失败')
      return { ok: false }
    } catch (err) {
      ElMessage.error(extractApiErrorMessage(err, '配乐同步异常'))
      return { ok: false }
    }
  }

  function openMusicHall() {
    musicHallOpen.value = true
  }

  function onMusicConfirm(track) {
    selectedMusic.value = track ? { ...track } : null
  }

  function clearSelectedMusic() {
    selectedMusic.value = null
  }

  function openVideoPicker() {
    if (mediaMode.value !== 'video') {
      ElMessage.warning('请先切换到视频模式')
      return
    }
    videoInputRef.value?.click()
  }

  function removeVideo() {
    if (videoUploading.value) {
      ElMessage.warning('视频仍在上传，请先取消上传')
      return
    }
    videoUrl.value = ''
    videoUploadError.value = ''
    videoUploadProgress.value = 0
    lastVideoFile.value = null
  }

  // 与后端 VIDEO_HARD_MAX_SIZE 对齐（nginx 与 multipart 都是 350MB，留 10MB 给表单开销）
  const VIDEO_MAX_MB = 340
  const VIDEO_ALLOWED_EXT = ['.mp4', '.mov', '.m4v', '.webm']

  // 之前只把体积拿来拼提示文案、不拦截：选个 1GB 的文件会真的开始传，
  // 传到 350MB 才被 nginx 用 413 掐断，白等一场
  function validateLocalVideoFile(file) {
    const name = String(file?.name || '').toLowerCase()
    const dot = name.lastIndexOf('.')
    if (dot < 0 || !VIDEO_ALLOWED_EXT.includes(name.slice(dot))) {
      return { ok: false, message: '仅支持 MP4 / MOV / M4V / WEBM 格式的视频' }
    }
    if (file.size > VIDEO_MAX_MB * 1024 * 1024) {
      const mb = (file.size / 1024 / 1024).toFixed(1)
      return { ok: false, message: `视频 ${mb}MB，超过 ${VIDEO_MAX_MB}MB 上限，请压缩后再上传` }
    }
    return { ok: true }
  }

  function startVideoUpload(file) {
    const sizeMb = (file.size / 1024 / 1024).toFixed(1)
    videoUploadError.value = ''
    videoUploadProgress.value = 0
    videoUploading.value = true
    videoUploadAbort = new AbortController()
    ElMessage.info(
      sizeMb >= 200
        ? `视频约 ${sizeMb}MB，超过 200MB 将后台处理，请勿重复点击`
        : `视频约 ${sizeMb}MB，上传中，可先写正文`,
    )
    pendingVideoUpload = uploadArticleVideo(file, {
      signal: videoUploadAbort.signal,
      onUploadProgress: (ev) => {
        if (!ev.total) return
        const pct = Math.round((ev.loaded / ev.total) * 100)
        videoUploadProgress.value = ev.loaded >= ev.total ? 100 : Math.min(99, pct)
      },
    })
      .then((res) => {
        if (res.code === 0 && res.data) {
          videoUrl.value = String(res.data)
          videoUploadProgress.value = 100
          lastVideoFile.value = null
          ElMessage.success('视频上传成功')
        } else {
          videoUploadError.value = res.message || '视频上传失败'
          ElMessage.error(videoUploadError.value)
        }
      })
      .catch((err) => {
        if (err?.code === 'ERR_CANCELED' || err?.name === 'CanceledError') {
          videoUploadError.value = '已取消上传，可重试或换一个视频'
          return
        }
        // 断网 / 超时都落在这里：文件对象留着，用户点重试即可，不用重新选
        videoUploadError.value = extractApiErrorMessage(err, '视频上传异常，请检查网络后重试')
        ElMessage.error(videoUploadError.value)
      })
      .finally(() => {
        videoUploading.value = false
        videoUploadAbort = null
        pendingVideoUpload = null
      })
  }

  function cancelVideoUpload() {
    if (!videoUploading.value) return
    videoUploadAbort?.abort()
  }

  function retryVideoUpload() {
    if (videoUploading.value || !lastVideoFile.value) return
    startVideoUpload(lastVideoFile.value)
  }

  function onVideoFileSelected(e) {
    const raw = e.target?.files
    if (!raw?.length) return
    const file = raw[0]
    e.target.value = ''
    if (mediaMode.value !== 'video') {
      ElMessage.warning('请先切换到视频模式')
      return
    }
    if (videoUploading.value) {
      ElMessage.warning('已有视频在上传，请等待完成')
      return
    }
    const pre = validateLocalVideoFile(file)
    if (!pre.ok) {
      ElMessage.warning(pre.message)
      return
    }
    lastVideoFile.value = file
    startVideoUpload(file)
  }

  // Markdown 预览
  const renderedPreview = computed(() => {
    if (!form.content) return '<div class="preview-empty">预览区域</div>'
    // 与详情页一致地净化：预览只给作者自己看，但渲染路径不该有两套标准
    try { return sanitizeHtml(marked.parse(form.content)) }
    catch { return sanitizePlainTextAsHtml(form.content) }
  })

  // Markdown 工具栏
  function mdWrap(before, after) {
    const textarea = mdTextareaRef.value?.textarea ?? mdTextareaRef.value?.$el?.querySelector('textarea')
    if (!textarea) return
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selected = form.content.substring(start, end)
    form.content = form.content.substring(0, start) + before + selected + after + form.content.substring(end)
  }

  function handleMdInsertImage() {
    mdFileInput.value?.click()
  }

  async function handleMdFileSelected(e) {
    const raw = e.target?.files
    if (!raw?.length) return
    const files = Array.from(raw)
    e.target.value = ''

    for (const file of files) {
      const pre = validateLocalImageFile(file)
      if (!pre.ok) {
        ElMessage.warning(`${file.name}：${pre.message}`)
        return
      }
    }

    ElMessage.info('插图在后台上传，可先继续编辑正文')
    try {
      for (const file of files) {
        const placeholder = `![上传中...](uploading-${Date.now()})`
        form.content += `\n${placeholder}\n`

        try {
          const res = await uploadArticleImage(file)
          if (res.code === 0) {
            form.content = form.content.replace(placeholder, `![图片](${res.data})`)
          } else {
            form.content = form.content.replace(placeholder, `![上传失败](${file.name})`)
          }
        } catch {
          form.content = form.content.replace(placeholder, `![上传异常](${file.name})`)
        }
      }
    } catch {
      ElMessage.error('插图上传异常')
    }
  }

  // 与后端一致：去掉 HTML 标签后计算正文纯文本长度
  function plainContentLength() {
    const raw = form.content || ''
    return raw.replace(/<[^>]+>/g, '').trim().length
  }

  // 存草稿：只挡"存不下去"的硬条件（版块、长度上限、上传未完成），
  // 不要求封面和最少字数 —— 草稿的意义就是写一半先存着
  async function validateForDraft() {
    if (!form.boardId) {
      ElMessage.warning('请先选择发布版块')
      return false
    }
    if (!form.title.trim() && !form.content.trim()) {
      ElMessage.warning('写点标题或正文再存草稿吧')
      return false
    }
    if (form.title.length > 100) {
      ElMessage.warning('标题最多 100 个字')
      return false
    }
    if (form.content.length > 20000) {
      ElMessage.warning('正文最多 20000 字，请精简后再保存')
      return false
    }
    if (coverUploading.value || galleryUploading.value || videoUploading.value) {
      ElMessage.warning('还有文件在上传，请稍候再保存')
      return false
    }
    return true
  }

  // 提交审核：完整门槛，与后端 assertReadyForAudit 对齐
  async function validateAndPrepare() {
    if (!form.boardId || !form.title || !form.content.trim()) {
      ElMessage.warning('标题、内容和版块缺一不可哦')
      return false
    }
    // 与后端 PublishArticleRequest / UpdateArticleRequest 的约束对齐，
    // 免得写完长文才在提交时被拒
    if (form.title.trim().length < 3) {
      ElMessage.warning('标题至少 3 个字')
      return false
    }
    if (form.content.trim().length < 6) {
      ElMessage.warning('正文至少 6 个字')
      return false
    }
    if (form.content.length > 20000) {
      ElMessage.warning('正文最多 20000 字，请精简后再发布')
      return false
    }
    if (coverUploading.value) {
      ElMessage.warning('封面还在上传中，请稍候')
      return false
    }
    if (!coverPreview.value) {
      ElMessage.warning('请先上传或生成帖子封面')
      return false
    }
    // 有相册图的帖子，正文纯文本必须 ≥ 10 字，否则后端相册同步会被拒 1146
    if (mediaMode.value === 'gallery' && galleryUrls.value.length > 0
        && plainContentLength() < GALLERY_MIN_CONTENT_LEN) {
      ElMessage.warning(`上传了图片的帖子，正文至少需要 ${GALLERY_MIN_CONTENT_LEN} 个字`)
      return false
    }
    if (galleryUploading.value) {
      ElMessage.warning('相册图片仍在上传，请稍候再保存')
      return false
    }
    if (mediaMode.value === 'video') {
      if (videoUploading.value) {
        ElMessage.warning('视频仍在上传，请稍候再保存')
        return false
      }
      if (!videoUrl.value) {
        ElMessage.warning('请先上传视频')
        return false
      }
    }

    // 内容合规审核在「提交审核」时由后端 LangGraph 异步完成，此处不再做同步 AI 校验
    return true
  }

  async function handleSaveDraft() {
    await waitForPendingVideoUpload()
    if (!await validateForDraft()) return
    submitting.value = true
    try {
      const payload = buildArticlePayload()
      const res = isEdit.value
        ? await updateArticle({ articleId: Number(route.params.id), ...payload })
        : await createDraft(payload)

      if (res.code === 0) {
        const articleId = isEdit.value ? route.params.id : res.data
        const gal = await syncGalleryToServer(articleId)
        if (!gal.ok) {
          // 相册/视频未同步成功 如正文不足 10 字 时，具体原因已由 syncGalleryToServer 提示
          // 此处中止跳转，避免用户误以为图片已保存
          const mediaHint = mediaMode.value === 'video' ? '视频' : '笔记相册'
          ElMessage.warning(`正文已保存，但${mediaHint}未同步，请按提示修正后重新保存`)
          return
        }
        const music = await syncMusicToServer(articleId)
        if (!music.ok) {
          ElMessage.warning('正文已保存，但配乐未同步，请稍后重试')
          return
        }
        const cov = await runCoverUploadToArticle(articleId)
        if (!cov.ok) {
          ElMessage.warning('正文已保存，但封面上传失败，请稍后在草稿中重试')
          return
        }
        clearLocalDraft()
        ElMessage.success('草稿已保存')
        router.push('/creative')
      } else if (res?.message) {
        ElMessage.error(res.message)
      }
    } catch (err) {
      ElMessage.error(extractApiErrorMessage(err, '保存草稿失败'))
    } finally {
      submitting.value = false
    }
  }

  async function handlePublish() {
    await waitForPendingVideoUpload()
    if (!await validateAndPrepare()) return
    submitting.value = true
    try {
      const payload = buildArticlePayload()
      let articleId
      if (isEdit.value) {
        articleId = route.params.id
        await updateArticle({ articleId: Number(articleId), ...payload })
      } else {
        const res = await createDraft(payload)
        if (res.code === 0) {
          articleId = res.data
        } else {
          return ElMessage.error(res.message || '草稿创建失败')
        }
      }

      if (articleId) {
        const gal = await syncGalleryToServer(articleId)
        if (!gal.ok) {
          const mediaHint = mediaMode.value === 'video' ? '视频' : '笔记相册'
          ElMessage.warning(`帖子已保存，但${mediaHint}未同步成功，请修正后重新提交审核`)
          return
        }
        const music = await syncMusicToServer(articleId)
        if (!music.ok) {
          ElMessage.warning('帖子已保存，但配乐未同步成功，请稍后重试')
          return
        }
        const cov = await runCoverUploadToArticle(articleId)
        if (!cov.ok) {
          ElMessage.warning('帖子已保存，但封面上传失败，暂未提交审核')
          return
        }

        const audit = await submitArticleForAuditWithPrompt(articleId)
        if (!audit.ok) return
        router.push('/community')
      }
    } catch (err) {
      ElMessage.error(extractApiErrorMessage(err, '提交审核失败'))
    } finally {
      submitting.value = false
    }
  }


  // 本地暂存：刷新 / 误关页后还能把写了一半的内容捞回来。
  // 只存纯文本字段，图片和视频已经在 OSS 上，靠 URL 恢复即可
  const LOCAL_DRAFT_KEY = 'forum:articleDraft'
  let localDraftSaveTimer = null

  function localDraftKey() {
    return `${LOCAL_DRAFT_KEY}:${userStore.id || 'anon'}:${route.params.id || 'new'}`
  }

  function hasUnsavedContent() {
    return Boolean(String(form.title || '').trim() || String(form.content || '').trim())
  }

  function saveLocalDraft() {
    if (!hasUnsavedContent()) return
    try {
      localStorage.setItem(localDraftKey(), JSON.stringify({
        title: form.title,
        content: form.content,
        contentType: form.contentType,
        articleType: form.articleType,
        boardId: form.boardId,
        editorMode: editorMode.value,
        coverPreview: coverPreview.value,
        tagIds: tagIds.value,
        savedAt: Date.now(),
      }))
    } catch {
      // 隐私模式或配额满：存不下就算了，不该影响正常编辑
    }
  }

  function clearLocalDraft() {
    try { localStorage.removeItem(localDraftKey()) } catch { /* 同上 */ }
  }

  function restoreLocalDraft() {
    let saved = null
    try {
      saved = JSON.parse(localStorage.getItem(localDraftKey()) || 'null')
    } catch {
      return false
    }
    if (!saved || (!saved.title && !saved.content)) return false
    form.title = saved.title || ''
    form.content = saved.content || ''
    form.contentType = Number(saved.contentType) || 0
    if (saved.articleType != null) form.articleType = saved.articleType
    if (saved.boardId) form.boardId = saved.boardId
    editorMode.value = saved.editorMode === 'markdown' ? 'markdown' : 'rich'
    if (saved.coverPreview) coverPreview.value = saved.coverPreview
    if (Array.isArray(saved.tagIds)) tagIds.value = saved.tagIds
    return true
  }

  // 关标签页 / 刷新前提示。内容其实已经暂存，但用户不知道，给个明确提醒
  function onBeforeUnload(event) {
    if (!hasUnsavedContent()) return
    saveLocalDraft()
    event.preventDefault()
    event.returnValue = ''
  }
  // 写了半天点一下就走人太狠了，有内容时先确认
  async function handleCancel() {
    if (hasUnsavedContent()) {
      const ok = await iconConfirm({
        title: '放弃这次编辑？',
        message: '当前内容尚未保存，离开后将会丢失。也可以先点「存草稿」保存。',
        confirmText: '放弃并离开',
        cancelText: '继续编辑',
        danger: true,
      }).catch(() => false)
      if (!ok) return
    }
    clearLocalDraft()
    router.back()
  }

  return {
    Close,
    MagicStick,
    Picture,
    Plus,
    Upload,
    VideoCamera,
    WangEditor,
    aiWriting,
    applyAiContent,
    cascaderOptions,
    coverPreview,
    coverUploading,
    rejectReason,
    editorMode,
    form,
    bindGalleryItemsRef,
    canAddGallery,
    clearCover,
    coverAiGenerating,
    coverImageQuality,
    coverInputRef,
    generateAiCover,
    galleryInputRef,
    imageModelOptions,
    galleryItemsRef,
    galleryMaxCount: MAX_ARTICLE_GALLERY,
    galleryStripFadeLeft,
    galleryStripOverflow,
    galleryUrls,
    mediaMode,
    videoUrl,
    selectedMusic,
    musicHallOpen,
    openMusicHall,
    onMusicConfirm,
    clearSelectedMusic,
    videoUploading,
    videoUploadProgress,
    canRetryVideoUpload,
    cancelVideoUpload,
    retryVideoUpload,
    videoUploadError,
    galleryUploading,
    galleryPendingCount,
    videoInputRef,
    handleBoardChange,
    handleCancel,
    handleMdFileSelected,
    handleMdInsertImage,
    handlePublish,
    handleSaveDraft,
    isEdit,
    mdFileInput,
    mdTextareaRef,
    mdWrap,
    onGalleryFilesSelected,
    onCoverFileSelected,
    openVideoPicker,
    removeVideo,
    onVideoFileSelected,
    openGalleryPicker,
    openCoverPicker,
    removeGalleryAt,
    renderedPreview,
    selectedBoard,
    submitting,
    tagIds,
    tagAiGenerating,
    setTagAiGenerating,
    setEditorMode,
    setCoverImageQuality,
    setAiWriting,
    setMediaMode,
    onMdKeydown,
    switchMode,
    updateGalleryStripState,
  }
}
