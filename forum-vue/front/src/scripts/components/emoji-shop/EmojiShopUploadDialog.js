import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { uploadEmojiShopImage, createShop } from '@/api/shop'
import { validateLocalImageFile, openImageUploadLoading } from '@/utils/imageUploadFeedback'
import { validateChatImageMime } from '@/utils/chatMedia'
import uploadHeaderIconUrl from '@/assets/svg/上传.svg?url'
import warnIconUrl from '@/assets/svg/警告.svg?url'
import submitIconUrl from '@/assets/svg/表情包上传提交.svg?url'

export const UPLOAD_NOTICE_ITEMS = [
  '名称需通过文本审核',
  '图片走 AI 内容审核',
  '须至少上传 1 张',
  '最多 60 张图片',
  '售价 0 表示免费领取',
]

const DESCRIPTION_MAX = 100
const NAME_MAX = 100
const PACK_MAX = 60

export function useEmojiShopUploadDialog({ onCreated, onClosed } = {}) {
  const router = useRouter()

  const visible = ref(false)
  const submitting = ref(false)
  const coverInput = ref(null)
  const packInput = ref(null)

  const form = reactive({
    name: '',
    description: '',
    price: 10,
    coverUrl: '',
    imageUrls: [],
  })

  function resetForm() {
    form.name = ''
    form.description = ''
    form.price = 10
    form.coverUrl = ''
    form.imageUrls = []
  }

  function open() {
    resetForm()
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  function pickCover() {
    coverInput.value?.click()
  }

  function pickPack() {
    if (form.imageUrls.length >= PACK_MAX) {
      ElMessage.warning(`最多 ${PACK_MAX} 张`)
      return
    }
    packInput.value?.click()
  }

  function onCoverFile(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    void uploadOne(file, (url) => {
      form.coverUrl = url
    })
  }

  function onPackFiles(e) {
    const files = [...(e.target.files || [])]
    e.target.value = ''
    const room = PACK_MAX - form.imageUrls.length
    const slice = files.slice(0, Math.max(0, room))
    for (const file of slice) {
      void uploadOne(file, (url) => form.imageUrls.push(url))
    }
  }

  async function uploadOne(file, onOk) {
    const mimeOk = validateChatImageMime(file)
    if (!mimeOk.ok) {
      ElMessage.warning(mimeOk.message)
      return
    }
    const sizeOk = validateLocalImageFile(file)
    if (!sizeOk.ok) {
      ElMessage.warning(sizeOk.message)
      return
    }
    const loading = openImageUploadLoading(file, '正在上传…')
    try {
      const res = await uploadEmojiShopImage(file)
      if (res.code === 0 && res.data) onOk(String(res.data).trim())
    } catch {
      /* 拦截器已提示 */
    } finally {
      loading.close()
    }
  }

  function removePack(i) {
    form.imageUrls.splice(i, 1)
  }

  async function submit() {
    const name = form.name.trim()
    if (!name) {
      ElMessage.warning('请填写表情包名称')
      return
    }
    if (!form.coverUrl) {
      ElMessage.warning('请上传封面')
      return
    }
    if (!form.imageUrls.length) {
      ElMessage.warning('请至少添加 1 张包内图')
      return
    }
    submitting.value = true
    try {
      const desc = form.description.trim()
      const res = await createShop({
        name,
        description: desc || undefined,
        coverUrl: form.coverUrl,
        price: Math.floor(Number(form.price)) || 0,
        imageUrls: [...form.imageUrls],
      })
      if (res.code === 0) {
        ElMessage.success('创建成功')
        close()
        onCreated?.(res.data)
        router.replace({ path: '/emoji-shop', query: { detail: String(res.data) } }).catch(() => {})
      }
    } finally {
      submitting.value = false
    }
  }

  watch(visible, (v, prev) => {
    if (prev && !v) onClosed?.()
    if (!v) resetForm()
  })

  return {
    uploadHeaderIconUrl,
    warnIconUrl,
    submitIconUrl,
    UPLOAD_NOTICE_ITEMS,
    DESCRIPTION_MAX,
    NAME_MAX,
    PACK_MAX,
    visible,
    submitting,
    coverInput,
    packInput,
    form,
    open,
    close,
    pickCover,
    pickPack,
    onCoverFile,
    onPackFiles,
    removePack,
    submit,
  }
}
