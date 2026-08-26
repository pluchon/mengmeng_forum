import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDialog } from '@/utils/appDialog'
import {
  uploadChatEmoji,
  uploadChatEmojis,
  favoriteEmoji,
  deleteFavoriteEmoji,
  getEmojiList,
} from '@/api/message'
import { canFavoriteChatMediaMessage } from '@/utils/chatMedia'

export const useChatEmojiStore = defineStore('chatEmoji', () => {
  const uploadedItems = ref([])
  const favoriteItems = ref([])
  const pagination = reactive({
    uploaded: { pageNum: 1, pageSize: 8, total: 0, pages: 1 },
    favorite: { pageNum: 1, pageSize: 8, total: 0, pages: 1 },
  })
  const loading = ref(false)

  async function fetchPage(source, pageNum = 1, pageSize = 8) {
    if (source !== 'uploaded' && source !== 'favorite') return null
    loading.value = true
    try {
      const res = await getEmojiList({ source, pageNum, pageSize })
      const page = res?.data || {}
      const records = Array.isArray(page.records) ? page.records : []
      if (source === 'uploaded') {
        uploadedItems.value = records
      } else {
        favoriteItems.value = records
      }
      pagination[source].pageNum = Number(page.pageNum) || 1
      pagination[source].pageSize = Number(page.pageSize) || pageSize
      pagination[source].total = Number(page.total) || 0
      pagination[source].pages = Math.max(1, Number(page.pages) || 1)
      return page
    } finally {
      loading.value = false
    }
  }

  async function fetchBoth() {
    await Promise.all([
      fetchPage('favorite', pagination.favorite.pageNum, pagination.favorite.pageSize),
      fetchPage('uploaded', pagination.uploaded.pageNum, pagination.uploaded.pageSize),
    ])
  }

  async function favoriteUploadedUrl(mediaUrl, file) {
    const isGif = file?.type === 'image/gif'
    try {
      const fav = await favoriteEmoji({
        mediaUrl,
        mediaType: isGif ? 1 : 0,
        mediaMime: isGif ? 'image/gif' : undefined,
        mediaSize: file?.size,
      })
      if (fav.data) return fav.data
    } catch (e) {
      if (e?.code === 1132) {
        ElMessage.info('已在你的收藏中')
        return null
      }
      throw e
    }
    return null
  }

  async function uploadAndFavorite(file) {
    const up = await uploadChatEmoji(file)
    const rawUrl = up?.data
    const mediaUrl = typeof rawUrl === 'string' ? rawUrl.trim() : ''
    if (!mediaUrl) return null
    return favoriteUploadedUrl(mediaUrl, file)
  }

  // 多选：分槽并行上传（每张独立审图/入库），避免整页锁死且便于刷新「我的上传」
  async function uploadAndFavoriteMany(files, { onItemDone } = {}) {
    const list = Array.isArray(files) ? files.filter(Boolean) : []
    if (!list.length) return { okCount: 0, items: [] }
    const CONCURRENCY = 3
    let cursor = 0
    const items = []
    let okCount = 0
    const workers = Array.from({ length: Math.min(CONCURRENCY, list.length) }, async () => {
      while (cursor < list.length) {
        const index = cursor
        cursor += 1
        const file = list[index]
        try {
          const up = await uploadChatEmoji(file)
          const mediaUrl = typeof up?.data === 'string' ? up.data.trim() : ''
          if (!mediaUrl) {
            onItemDone?.({ index, ok: false })
            continue
          }
          const fav = await favoriteUploadedUrl(mediaUrl, file)
          if (fav) {
            okCount += 1
            items.push(fav)
            onItemDone?.({ index, ok: true, item: fav })
          } else {
            onItemDone?.({ index, ok: false })
          }
        } catch {
          onItemDone?.({ index, ok: false })
        }
      }
    })
    await Promise.all(workers)
    return { okCount, items }
  }

  async function favoriteFromChatMessage(message) {
    if (!canFavoriteChatMediaMessage(message)) {
      ElMessage.warning('商城表情无需重复收藏')
      return null
    }
    try {
      const fromGroup = Number(message.groupId) > 0
      const fav = await favoriteEmoji({
        mediaUrl: message.mediaUrl,
        mediaType: fromGroup ? 0 : (Number(message.messageType) === 2 ? 1 : 0),
        mediaMime: fromGroup ? undefined : message.mediaMime,
        mediaSize: message.mediaSize,
        originMessageId: fromGroup ? undefined : message.id,
        originGroupMessageId: fromGroup ? message.id : undefined,
      })
      if (fav.data) {
        ElMessage.success('已收藏')
        return fav.data
      }
    } catch (e) {
      if (e?.code === 1132) {
        ElMessage.info('已在你的收藏中')
        return null
      }
      throw e
    }
    return null
  }

  async function remove(emojiId) {
    try {
      await confirmDialog('确定从表情收藏中移除这张吗？', '移除表情', {
        type: 'warning',
        confirmButtonText: '移除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deleteFavoriteEmoji(emojiId)
      ElMessage.success('已移除')
      return true
    } catch {
      // 全局拦截器已提示
      return false
    }
  }

  return {
    favoriteItems,
    loading,
    pagination,
    uploadedItems,
    fetchBoth,
    fetchPage,
    uploadAndFavorite,
    uploadAndFavoriteMany,
    favoriteFromChatMessage,
    remove,
  }
})
