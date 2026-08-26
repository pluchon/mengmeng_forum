import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import {
  createShop,
  getShopDraft,
  saveShopDraft,
  submitShopDraft,
  uploadEmojiShopImage,
  uploadEmojiShopImages,
  getShopMyPublishedDetail,
  updateShopMyPublished,
  deleteShopMyPublished,
  relistShopMyPublished,
} from '@/api/shop'
import { validateLocalImageFile, openImageUploadLoading } from '@/utils/imageUploadFeedback'
import { validateChatImageMime } from '@/utils/chatMedia'
import uploadHeaderIconUrl from '@/assets/svg/上传.svg?url'
import warnIconUrl from '@/assets/svg/警告.svg?url'
import submitIconUrl from '@/assets/svg/表情包上传提交.svg?url'
import { EMOJI_SHOP_CATEGORY_OPTIONS } from '@/constants/emojiShopCategory'

export const UPLOAD_NOTICE_ITEMS = [
  '支持最多 60 张图片',
  '审核通过后即可在商城展示',
]

const DESCRIPTION_MAX = 50
const NAME_MAX = 20
const PACK_MAX = 60
const PACK_PAGE_SLOT_COUNT = 5
const UPLOAD_CONCURRENCY = 3
const PACK_BATCH_SIZE = 9

export function useEmojiShopUploadDialog({
  onCreated,
  onDraftSaved,
  onPublishedUpdated,
  onPublishedDeleted,
  onClosed,
} = {}) {
  const router = useRouter()

  const visible = ref(false)
  const submitting = ref(false)
  const savingDraft = ref(false)
  const autoSavingDraft = ref(false)
  const loadingDraft = ref(false)
  const waitingUploads = ref(false)
  const relisting = ref(false)
  const uploadingImageCount = ref(0)
  const pendingPackCount = ref(0)
  const pendingPackSlots = ref([])
  const failedUploadCount = ref(0)
  const draftId = ref(null)
  const publishedShopId = ref(null)
  const publishedStatus = ref(null)
  const packPage = ref(1)
  const coverInput = ref(null)
  const packInput = ref(null)
  let uploadGeneration = 0
  let queuedUploads = []
  const runningUploads = new Set()
  const idleWaiters = []
  let packCommitChain = Promise.resolve()
  let savedFormSnapshot = ''

  const form = reactive({
    name: '',
    category: '',
    description: '',
    price: 10,
    coverUrl: '',
    imageUrls: [],
  })

  const priceMode = computed(() => (Number(form.price) > 0 ? 'paid' : 'free'))
  const editMode = computed(() => publishedShopId.value != null)
  const interactionLocked = computed(() => submitting.value || savingDraft.value || autoSavingDraft.value || relisting.value)
  const dialogTitle = computed(() => (editMode.value ? '编辑表情包' : '上传表情包'))
  const isOfflinePublished = computed(() => editMode.value && Number(publishedStatus.value) === 2)
  const displayPackCount = computed(() => form.imageUrls.length + pendingPackCount.value)
  const packSlotItems = computed(() => [
    ...form.imageUrls.map((url, index) => ({ type: 'ready', url, index })),
    ...pendingPackSlots.value.map((slot) => ({ type: 'pending', url: slot.previewUrl, id: slot.id })),
  ])
  const packPageCount = computed(() => {
    const slotCount = packSlotItems.value.length + (displayPackCount.value < PACK_MAX ? 1 : 0)
    return Math.max(1, Math.ceil(slotCount / PACK_PAGE_SLOT_COUNT))
  })
  const visiblePackImages = computed(() => {
    const start = (packPage.value - 1) * PACK_PAGE_SLOT_COUNT
    return packSlotItems.value.slice(start, start + PACK_PAGE_SLOT_COUNT)
  })
  const showPackAdd = computed(() => {
    if (displayPackCount.value >= PACK_MAX) return false
    if (packPage.value !== packPageCount.value) return false
    const start = (packPage.value - 1) * PACK_PAGE_SLOT_COUNT
    const usedOnPage = packSlotItems.value.length - start
    return usedOnPage < PACK_PAGE_SLOT_COUNT
  })

  function resetForm() {
    draftId.value = null
    publishedShopId.value = null
    publishedStatus.value = null
    packPage.value = 1
    form.name = ''
    form.category = ''
    form.description = ''
    form.price = 10
    form.coverUrl = ''
    form.imageUrls = []
    pendingPackCount.value = 0
    pendingPackSlots.value.forEach((slot) => {
      if (slot.previewUrl) URL.revokeObjectURL(slot.previewUrl)
    })
    pendingPackSlots.value = []
    failedUploadCount.value = 0
    waitingUploads.value = false
    packCommitChain = Promise.resolve()
    savedFormSnapshot = serializeForm()
  }

  function serializeForm() {
    return JSON.stringify({
      name: form.name.trim(),
      category: form.category || '',
      description: form.description.trim(),
      price: Math.floor(Number(form.price)) || 0,
      coverUrl: form.coverUrl || '',
      imageUrls: [...form.imageUrls],
    })
  }

  function rememberSavedForm() {
    savedFormSnapshot = serializeForm()
  }

  function hasUnsavedChanges() {
    return serializeForm() !== savedFormSnapshot
      || uploadingImageCount.value > 0
      || pendingPackCount.value > 0
  }

  function countUploads(token) {
    return queuedUploads.filter((task) => task.token === token).length
      + [...runningUploads].filter((task) => task.token === token).length
  }

  function syncUploadingCount() {
    uploadingImageCount.value = countUploads(uploadGeneration) + pendingPackCount.value
    for (let index = idleWaiters.length - 1; index >= 0; index -= 1) {
      const waiter = idleWaiters[index]
      if (countUploads(waiter.token) > 0) continue
      idleWaiters.splice(index, 1)
      waiter.resolve()
    }
  }

  function waitForUploads(token) {
    if (countUploads(token) === 0) return Promise.resolve()
    return new Promise((resolve) => idleWaiters.push({ token, resolve }))
  }

  function cancelQueuedUploads(token) {
    const canceled = queuedUploads.filter((task) => task.token === token)
    queuedUploads = queuedUploads.filter((task) => task.token !== token)
    canceled.forEach((task) => task.resolve({ status: 'canceled' }))
    syncUploadingCount()
  }

  function validateUploadFile(file) {
    const mimeOk = validateChatImageMime(file)
    if (!mimeOk.ok) {
      ElMessage.warning(mimeOk.message)
      return false
    }
    const sizeOk = validateLocalImageFile(file)
    if (!sizeOk.ok) {
      ElMessage.warning(sizeOk.message)
      return false
    }
    return true
  }

  function enqueueUpload(file, token) {
    return new Promise((resolve) => {
      queuedUploads.push({ file, token, resolve })
      syncUploadingCount()
      pumpUploads()
    })
  }

  function pumpUploads() {
    while (runningUploads.size < UPLOAD_CONCURRENCY && queuedUploads.length > 0) {
      const task = queuedUploads.shift()
      runningUploads.add(task)
      syncUploadingCount()
      void runUploadTask(task)
    }
  }

  async function runUploadTask(task) {
    const loading = openImageUploadLoading(task.file, '正在上传…')
    let result = { status: 'failed' }
    try {
      const res = await uploadEmojiShopImage(task.file)
      const url = res.code === 0 && res.data ? String(res.data).trim() : ''
      if (url) result = { status: 'success', url }
    } catch {
      // 拦截器已提示
    } finally {
      loading.close()
      runningUploads.delete(task)
      task.resolve(result)
      syncUploadingCount()
      pumpUploads()
    }
  }

  async function open(id = null, mode = 'draft') {
    cancelQueuedUploads(uploadGeneration)
    uploadGeneration += 1
    resetForm()
    visible.value = true
    const targetDraftId = Number(id)
    if (!Number.isFinite(targetDraftId) || targetDraftId <= 0) {
      rememberSavedForm()
      return
    }
    loadingDraft.value = true
    try {
      const isPublished = mode === 'published'
      const res = isPublished
        ? await getShopMyPublishedDetail(targetDraftId)
        : await getShopDraft(targetDraftId)
      if (res.code !== 0 || !res.data) {
        ElMessage.warning(res.message || (isPublished ? '表情包不存在或无权编辑' : '草稿不存在或无权编辑'))
        close()
        return
      }
      const data = res.data
      if (isPublished) {
        publishedShopId.value = Number(data.id)
        publishedStatus.value = Number(data.status)
        if (Number(data.status) === 0) {
          ElMessage.info('审核中，通过后可编辑')
          closeImmediately()
          return
        }
      } else {
        draftId.value = Number(data.id)
      }
      form.name = data.name === '未命名草稿' ? '' : (data.name || '')
      form.category = data.category || ''
      form.description = data.description || ''
      form.price = Number.isFinite(Number(data.price)) ? Number(data.price) : 10
      form.coverUrl = data.coverUrl || ''
      form.imageUrls = Array.isArray(data.imageUrls) ? [...data.imageUrls] : []
      rememberSavedForm()
    } finally {
      loadingDraft.value = false
    }
  }

  function close() {
    if (!visible.value || interactionLocked.value) return
    closeImmediately()
  }

  function closeImmediately() {
    if (!visible.value) return
    const closingGeneration = uploadGeneration
    cancelQueuedUploads(closingGeneration)
    uploadGeneration += 1
    uploadingImageCount.value = 0
    pendingPackCount.value = 0
    pendingPackSlots.value.forEach((slot) => {
      if (slot.previewUrl) URL.revokeObjectURL(slot.previewUrl)
    })
    pendingPackSlots.value = []
    waitingUploads.value = false
    visible.value = false
  }

  async function handleDialogBeforeClose(done) {
    if (editMode.value || interactionLocked.value || loadingDraft.value) return
    const token = uploadGeneration
    autoSavingDraft.value = true
    try {
      if (uploadingImageCount.value > 0 || pendingPackCount.value > 0) {
        waitingUploads.value = true
        await waitForUploads(token)
        await packCommitChain
      }
      if (token !== uploadGeneration || !visible.value) return
      if (!hasUnsavedChanges()) {
        done()
        return
      }
      const saved = await persistDraft(false)
      if (saved) done()
    } finally {
      waitingUploads.value = false
      autoSavingDraft.value = false
    }
  }

  function pickCover() {
    coverInput.value?.click()
  }

  function pickPack() {
    if (displayPackCount.value >= PACK_MAX) {
      ElMessage.warning(`最多 ${PACK_MAX} 张`)
      return
    }
    packInput.value?.click()
  }

  async function onCoverFile(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || !validateUploadFile(file)) return
    const token = uploadGeneration
    const result = await enqueueUpload(file, token)
    if (token === uploadGeneration && visible.value && result.status === 'success') {
      form.coverUrl = result.url
    }
  }

  function onPackFiles(e) {
    const files = [...(e.target.files || [])]
    e.target.value = ''
    const room = PACK_MAX - form.imageUrls.length - pendingPackCount.value
    const selected = files.slice(0, Math.max(0, room))
    const validFiles = selected.filter(validateUploadFile)
    const validationFailures = selected.length - validFiles.length
    if (!validFiles.length) {
      if (validationFailures > 0) failedUploadCount.value += validationFailures
      return
    }
    failedUploadCount.value += validationFailures
    const slots = validFiles.map((file) => ({
      id: `pack-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      previewUrl: URL.createObjectURL(file),
      file,
    }))
    pendingPackSlots.value.push(...slots)
    pendingPackCount.value += slots.length
    syncUploadingCount()
    const token = uploadGeneration
    packPage.value = packPageCount.value
    packCommitChain = packCommitChain.then(async () => {
      const successfulUrls = []
      let uploadFailures = 0
      try {
        for (let offset = 0; offset < slots.length; offset += PACK_BATCH_SIZE) {
          if (token !== uploadGeneration || !visible.value) break
          const chunk = slots.slice(offset, offset + PACK_BATCH_SIZE)
          try {
            const res = await uploadEmojiShopImages(
              chunk.map((slot) => slot.file),
              { silentHttpError: true },
            )
            const successList = Array.isArray(res?.data?.success) ? res.data.success : []
            const successByIndex = new Map(
              successList
                .filter((item) => item?.url != null)
                .map((item) => [Number(item.index), String(item.url)]),
            )
            chunk.forEach((_, localIndex) => {
              const url = successByIndex.get(localIndex)
              if (url) successfulUrls.push(url)
              else uploadFailures += 1
            })
          } catch {
            uploadFailures += chunk.length
          }
        }
      } finally {
        if (token === uploadGeneration) {
          const slotIds = new Set(slots.map((slot) => slot.id))
          pendingPackSlots.value.forEach((slot) => {
            if (slotIds.has(slot.id) && slot.previewUrl) URL.revokeObjectURL(slot.previewUrl)
          })
          pendingPackSlots.value = pendingPackSlots.value.filter((slot) => !slotIds.has(slot.id))
          pendingPackCount.value = Math.max(0, pendingPackCount.value - slots.length)
          syncUploadingCount()
          if (visible.value) {
            form.imageUrls.push(...successfulUrls)
            failedUploadCount.value += uploadFailures
            packPage.value = packPageCount.value
          }
        }
      }
    })
  }

  function removePack(index) {
    form.imageUrls.splice(index, 1)
  }

  function setPriceMode(mode) {
    if (mode === 'free') {
      form.price = 0
      return
    }
    if (Number(form.price) <= 0) form.price = 10
  }

  function buildPayload() {
    return {
      draftId: draftId.value || undefined,
      name: form.name.trim(),
      category: form.category || undefined,
      description: form.description.trim() || undefined,
      coverUrl: form.coverUrl || undefined,
      price: Math.floor(Number(form.price)) || 0,
      imageUrls: [...form.imageUrls],
    }
  }

  async function persistDraft(showSuccess = true) {
    if (editMode.value) return
    if (uploadingImageCount.value > 0) {
      ElMessage.warning('图片上传中，请完成后再保存草稿')
      return false
    }
    savingDraft.value = true
    try {
      const res = await saveShopDraft(buildPayload())
      if (res.code === 0 && res.data) {
        draftId.value = Number(res.data)
        rememberSavedForm()
        if (showSuccess) ElMessage.success('草稿已保存')
        onDraftSaved?.(draftId.value)
        return true
      }
      return false
    } finally {
      savingDraft.value = false
    }
  }

  async function saveDraft() {
    return persistDraft(true)
  }

  function validatePublish() {
    const name = form.name.trim()
    if (!name) {
      ElMessage.warning('请填写表情包名称')
      return false
    }
    if (name.length > NAME_MAX) {
      ElMessage.warning(`表情包名称必须 1-${NAME_MAX} 字`)
      return false
    }
    if (!form.category) {
      ElMessage.warning('请选择表情包分类')
      return false
    }
    const description = form.description.trim()
    if (!description) {
      ElMessage.warning('请填写表情包说明')
      return false
    }
    if (description.length > DESCRIPTION_MAX) {
      ElMessage.warning(`表情包说明必须 1-${DESCRIPTION_MAX} 字`)
      return false
    }
    if (!form.coverUrl) {
      ElMessage.warning('请上传封面')
      return false
    }
    if (!form.imageUrls.length) {
      ElMessage.warning('请至少添加 1 张包内图')
      return false
    }
    return true
  }

  async function submit() {
    const token = uploadGeneration
    submitting.value = true
    try {
      if (uploadingImageCount.value > 0 || pendingPackCount.value > 0) {
        waitingUploads.value = true
        await waitForUploads(token)
        await packCommitChain
        waitingUploads.value = false
      }
      if (token !== uploadGeneration || !visible.value) return
      if (failedUploadCount.value > 0) {
        ElMessage.error(`${failedUploadCount.value} 张图片上传失败，请重新选择后再提交`)
        return
      }
      if (!validatePublish()) return
      const payload = buildPayload()
      if (editMode.value) {
        delete payload.draftId
        const res = await updateShopMyPublished(publishedShopId.value, payload)
        if (res.code === 0) {
          const shopId = publishedShopId.value
          ElMessage.success('修改已保存')
          closeImmediately()
          onPublishedUpdated?.(shopId)
        }
        return
      }
      const res = draftId.value ? await submitShopDraft(payload) : await createShop(payload)
      if (res.code === 0) {
        ElMessage.success('已提交审核，通过后将自动上架')
        const shopId = Number(res.data)
        closeImmediately()
        onCreated?.(shopId)
        router.replace({ path: '/emoji-shop', query: { detail: String(shopId) } }).catch(() => {})
      }
    } finally {
      waitingUploads.value = false
      submitting.value = false
    }
  }

  async function deletePublished() {
    if (!editMode.value || submitting.value) return
    try {
      await confirmDialog(
        '删除后商城不再展示，但历史消息和已发送图片仍会保留。该操作在产品中不可恢复。',
        '删除表情包系列',
        { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
      )
      const shopId = publishedShopId.value
      const res = await deleteShopMyPublished(shopId)
      if (res.code === 0) {
        ElMessage.success('表情包系列已删除')
        close()
        onPublishedDeleted?.(shopId)
      }
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') throw error
    }
  }

  async function relistPublished() {
    if (!isOfflinePublished.value || interactionLocked.value) return
    const token = uploadGeneration
    relisting.value = true
    try {
      if (uploadingImageCount.value > 0 || pendingPackCount.value > 0) {
        waitingUploads.value = true
        await waitForUploads(token)
        await packCommitChain
        waitingUploads.value = false
      }
      if (token !== uploadGeneration || !visible.value || !validatePublish()) return
      if (failedUploadCount.value > 0) {
        ElMessage.error(`${failedUploadCount.value} 张图片上传失败，请重新选择后再上架`)
        return
      }
      const payload = buildPayload()
      delete payload.draftId
      const updateRes = await updateShopMyPublished(publishedShopId.value, payload)
      if (updateRes.code !== 0) return
      const relistRes = await relistShopMyPublished(publishedShopId.value)
      if (relistRes.code !== 0) return
      publishedStatus.value = 1
      rememberSavedForm()
      ElMessage.success('表情包已重新上架')
      onPublishedUpdated?.(publishedShopId.value)
    } finally {
      waitingUploads.value = false
      relisting.value = false
    }
  }

  watch(
    () => packSlotItems.value.length,
    () => {
      if (packPage.value > packPageCount.value) packPage.value = packPageCount.value
    },
  )

  watch(visible, (v, prev) => {
    if (prev && !v) onClosed?.()
    if (!v) resetForm()
  })

  return {
    uploadHeaderIconUrl,
    warnIconUrl,
    submitIconUrl,
    UPLOAD_NOTICE_ITEMS,
    EMOJI_SHOP_CATEGORY_OPTIONS,
    DESCRIPTION_MAX,
    NAME_MAX,
    PACK_MAX,
    visible,
    submitting,
    savingDraft,
    autoSavingDraft,
    interactionLocked,
    loadingDraft,
    waitingUploads,
    relisting,
    uploadingImageCount,
    displayPackCount,
    editMode,
    isOfflinePublished,
    dialogTitle,
    coverInput,
    packInput,
    form,
    priceMode,
    packPage,
    packPageCount,
    visiblePackImages,
    showPackAdd,
    open,
    close,
    handleDialogBeforeClose,
    pickCover,
    pickPack,
    onCoverFile,
    onPackFiles,
    removePack,
    setPriceMode,
    saveDraft,
    submit,
    deletePublished,
    relistPublished,
  }
}
