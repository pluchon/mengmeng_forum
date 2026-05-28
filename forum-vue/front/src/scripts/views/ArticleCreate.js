import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import { Picture, Plus } from '@element-plus/icons-vue'
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
  replaceArticleImages,
} from '@/api/article'
import { submitArticleForAuditWithPrompt } from '@/composables/useArticleAuditSubmit'
import { extractApiErrorMessage } from '@/api/httpError'
import { isArticleEditingLocked } from '@/utils/articleStatus'
import WangEditor from '@/components/common/WangEditor.vue'
import { marked } from 'marked'
import { stripSingleOuterParagraph } from '@/utils/htmlNormalize'
import {
  getBatchImageUploadLoadingText,
  openImageUploadLoading,
  validateLocalImageFile,
} from '@/utils/imageUploadFeedback'
import '@/assets/styles/editor.css'

const MAX_ARTICLE_GALLERY = 15

export function useArticleCreate() {
  const route = useRoute()
  const router = useRouter()
  const boardStore = useBoardStore()
  const userStore = useUserStore()

  const isEdit = computed(() => !!route.params.id)
  const submitting = ref(false)
  const editorMode = ref('rich')
  const selectedBoard = ref([])

  const form = reactive({
    boardId: '',
    title: '',
    content: '',
    contentType: 0,
    coverImg: ''
  })

  const coverFile = ref(null)
  const coverPreview = ref('')
  const mdFileInput = ref(null)
  const mdTextareaRef = ref(null)
  /** 笔记相册（article_image），与正文独立；顺序即展示顺序 */
  const galleryUrls = ref([])
  const galleryInputRef = ref(null)
  const galleryItemsRef = ref(null)
  const galleryStripOverflow = ref(false)
  const galleryStripFadeLeft = ref(false)
  let galleryResizeObserver = null

  const canAddGallery = computed(() => galleryUrls.value.length < MAX_ARTICLE_GALLERY)

  function updateGalleryStripState() {
    const el = galleryItemsRef.value
    if (!el || editorMode.value !== 'markdown') return
    const overflow = el.scrollWidth > el.clientWidth + 2
    const fadeLeft = overflow && el.scrollLeft > 4
    galleryStripOverflow.value = overflow
    galleryStripFadeLeft.value = fadeLeft
  }

  function scrollGalleryToEnd() {
    if (editorMode.value !== 'markdown') return
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
      coverImg: form.coverImg,
    }
  }

  async function runCoverUploadToArticle(articleId) {
    if (!coverFile.value) return { ok: true }
    const pre = validateLocalImageFile(coverFile.value)
    if (!pre.ok) {
      ElMessage.warning(pre.message)
      return { ok: false }
    }
    const loading = openImageUploadLoading(coverFile.value, '正在上传封面，请稍候…')
    try {
      const uploadRes = await uploadCoverFile(coverFile.value)
      if (uploadRes.code !== 0) return { ok: false }
      const bindRes = await updateArticleCoverByUrl(articleId, uploadRes.data)
      return { ok: bindRes.code === 0 }
    } finally {
      loading.close()
    }
  }

  watch(galleryUrls, () => {
    nextTick(() => {
      if (editorMode.value === 'markdown') scrollGalleryToEnd()
    })
  }, { deep: true })

  watch(editorMode, (mode) => {
    nextTick(() => {
      if (mode === 'markdown') {
        scrollGalleryToEnd()
        bindGalleryOverflowWatch()
      } else {
        resetGalleryStripState()
      }
    })
  })

  onBeforeUnmount(() => {
    galleryResizeObserver?.disconnect()
  })

  onMounted(async () => {
    if (blockIfMuted(userStore)) {
      router.replace('/')
      return
    }
    nextTick(() => {
      if (editorMode.value === 'markdown') {
        scrollGalleryToEnd()
        bindGalleryOverflowWatch()
      }
    })

    if (boardStore.categoryList.length === 0) await boardStore.fetchCategoryList()

    if (isEdit.value) {
      const res = await getArticleDetail(route.params.id)
      if (res.code === 0) {
        const a = res.data.article
        if (isArticleEditingLocked(a.status)) {
          ElMessage.info('该帖子正在审核中，请稍候')
          router.replace(`/article/${route.params.id}/audit`)
          return
        }
        const ct = Number(a.contentType) || 0
        Object.assign(form, {
          boardId: a.boardId,
          title: a.title,
          content: a.content,
          contentType: ct,
          coverImg: a.coverImg || ''
        })
        editorMode.value = ct === 1 ? 'markdown' : 'rich'
        coverPreview.value = a.coverImg
        galleryUrls.value = Array.isArray(res.data.imageUrls) ? [...res.data.imageUrls] : []

        // 设置级联选择器回显
        if (form.boardId) {
          boardStore.categoryList.forEach(cat => {
            if (cat.boardList?.some(b => b.id === form.boardId)) {
              selectedBoard.value = [cat.category.id, form.boardId]
            }
          })
        }
      }
    }
  })

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
    form.boardId = val?.length ? val[val.length - 1] : ''
  }

  // 模式切换
  function switchMode(mode) {
    form.contentType = mode === 'markdown' ? 1 : 0
  }

  function setEditorMode(mode) {
    if (editorMode.value === mode) return
    if (mode === 'rich') resetGalleryStripState()
    editorMode.value = mode
    switchMode(mode)
  }

  function applyAiContent(text) {
    form.content = text || ''
  }

  // 封面处理
  function handleCoverChange(file) {
    coverFile.value = file.raw
    coverPreview.value = URL.createObjectURL(file.raw)
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
    galleryInputRef.value?.click()
  }

  function removeGalleryAt(index) {
    if (index < 0 || index >= galleryUrls.value.length) return
    galleryUrls.value.splice(index, 1)
    nextTick(() => {
      if (editorMode.value === 'markdown') scrollGalleryToEnd()
    })
  }

  async function onGalleryFilesSelected(e) {
    const raw = e.target?.files
    if (!raw?.length) return
    const files = Array.from(raw)
    e.target.value = ''
    const room = MAX_ARTICLE_GALLERY - galleryUrls.value.length
    if (room <= 0) {
      ElMessage.warning(`相册最多 ${MAX_ARTICLE_GALLERY} 张`)
      return
    }
    const take = files.slice(0, room)
    if (files.length > take.length) {
      ElMessage.warning(`最多再添加 ${room} 张，已自动截取前 ${room} 张`)
    }
    for (const file of take) {
      const pre = validateLocalImageFile(file)
      if (!pre.ok) {
        ElMessage.warning(`${file.name}：${pre.message}`)
        return
      }
    }
    const loading = openImageUploadLoading(
      take[0],
      getBatchImageUploadLoadingText(take, `正在上传相册图（共 ${take.length} 张）…`),
    )
    try {
      for (const file of take) {
        const res = await uploadArticleImage(file)
        if (res.code === 0 && res.data) {
          galleryUrls.value.push(String(res.data))
          nextTick(scrollGalleryToEnd)
        } else {
          ElMessage.error(res.message || '图片上传失败')
          return
        }
      }
    } catch (err) {
      ElMessage.error('图片上传异常')
    } finally {
      loading.close()
    }
  }

  async function syncGalleryToServer(articleId) {
    const id = Number(articleId)
    if (!id || Number.isNaN(id)) return { ok: false }
    try {
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

  // Markdown 预览
  const renderedPreview = computed(() => {
    if (!form.content) return '<div class="preview-empty">预览区域</div>'
    try { return marked.parse(form.content) }
    catch { return form.content }
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

    const loading = ElLoading.service({
      lock: true,
      text: getBatchImageUploadLoadingText(files, '正在上传插图，请稍候…'),
      background: 'rgba(255,255,255,0.72)',
    })
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
    } finally {
      loading.close()
    }
  }

  // 核心提交逻辑
  async function validateAndPrepare() {
    if (!form.boardId || !form.title || !form.content.trim()) {
      ElMessage.warning('标题、内容和版块缺一不可哦')
      return false
    }

    // 内容合规审核在「提交审核」时由后端 LangGraph 异步完成，此处不再做同步 AI 校验
    return true
  }

  async function handleSaveDraft() {
    if (!await validateAndPrepare()) return
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
          ElMessage.warning('正文已保存，但笔记相册未同步成功，可重新保存一次')
        }
        // edit 模式下用户若用了内嵌封面 uploader, 先把封面同步上去再跳转, 让 cover 页能展示最新预览
        const cov = await runCoverUploadToArticle(articleId)
        if (!cov.ok) {
          ElMessage.warning('正文已保存，但封面上传失败，可在封面页重试')
        }
        ElMessage.success('草稿已保存')
        const target = `/article/${articleId}/cover`
        router.replace(target)
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
          ElMessage.warning('帖子已保存，但笔记相册未同步成功，请修正后重新提交审核')
          return
        }
        const cov = await runCoverUploadToArticle(articleId)
        if (!cov.ok) {
          ElMessage.warning('帖子已保存，但封面上传失败，可先到封面页补充后再提交审核')
        }

        const audit = await submitArticleForAuditWithPrompt(articleId)
        if (!audit.ok) return
        const q = audit.taskId ? `?taskId=${encodeURIComponent(audit.taskId)}` : ''
        router.push(`/article/${articleId}/audit${q}`)
      }
    } catch (err) {
      ElMessage.error(extractApiErrorMessage(err, '提交审核失败'))
    } finally {
      submitting.value = false
    }
  }

  function handleCancel() {
    router.back()
  }

  return {
    Picture,
    Plus,
    WangEditor,
    applyAiContent,
    cascaderOptions,
    coverPreview,
    editorMode,
    form,
    bindGalleryItemsRef,
    canAddGallery,
    galleryInputRef,
    galleryItemsRef,
    galleryMaxCount: MAX_ARTICLE_GALLERY,
    galleryStripFadeLeft,
    galleryStripOverflow,
    galleryUrls,
    handleBoardChange,
    handleCancel,
    handleCoverChange,
    handleMdFileSelected,
    handleMdInsertImage,
    handlePublish,
    handleSaveDraft,
    isEdit,
    mdFileInput,
    mdTextareaRef,
    mdWrap,
    onGalleryFilesSelected,
    openGalleryPicker,
    removeGalleryAt,
    renderedPreview,
    selectedBoard,
    submitting,
    setEditorMode,
    switchMode,
    updateGalleryStripState,
  }
}
