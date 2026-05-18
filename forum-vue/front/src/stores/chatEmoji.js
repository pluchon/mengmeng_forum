import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  uploadChatEmoji,
  favoriteEmoji,
  deleteFavoriteEmoji,
  getEmojiList,
} from '@/api/message'
import { canFavoriteChatMediaMessage } from '@/utils/chatMedia'

export const useChatEmojiStore = defineStore('chatEmoji', () => {
  const list = ref([])
  const loaded = ref(false)
  const loading = ref(false)

  async function fetchList(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      const res = await getEmojiList()
      if (res.code === 0 && Array.isArray(res.data)) {
        list.value = res.data
        loaded.value = true
      }
    } finally {
      loading.value = false
    }
  }

  function invalidate() {
    loaded.value = false
  }

  /** @returns {Promise<object|null>} 新增的收藏项；1132 返回 null */
  async function uploadAndFavorite(file) {
    const up = await uploadChatEmoji(file)
    const rawUrl = up?.data
    const mediaUrl = typeof rawUrl === 'string' ? rawUrl.trim() : ''
    const isGif = file.type === 'image/gif'
    try {
      // 不自传 originMessageId，避免某些环境下被序列化成 0 导致服务端误判为「引用聊天图」
      const fav = await favoriteEmoji({
        mediaUrl,
        mediaType: isGif ? 1 : 0,
        mediaMime: isGif ? 'image/gif' : undefined,
        mediaSize: file.size,
      })
      if (fav.data) {
        list.value.unshift(fav.data)
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

  /** @param message 私信详情里的 message 对象（含 id / mediaUrl / messageType …） */
  async function favoriteFromChatMessage(message) {
    if (!canFavoriteChatMediaMessage(message)) {
      ElMessage.warning('商城表情不支持添加到收藏')
      return null
    }
    try {
      const fav = await favoriteEmoji({
        mediaUrl: message.mediaUrl,
        mediaType: Number(message.messageType) === 2 ? 1 : 0,
        mediaMime: message.mediaMime,
        mediaSize: message.mediaSize,
        originMessageId: message.id,
      })
      if (fav.data) {
        list.value.unshift(fav.data)
        ElMessage.success('已添加到表情')
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
      await ElMessageBox.confirm('确定从表情收藏中移除这张吗？', '移除表情', {
        type: 'warning',
        confirmButtonText: '移除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deleteFavoriteEmoji(emojiId)
      list.value = list.value.filter((e) => e.id !== emojiId)
      ElMessage.success('已移除')
    } catch {
      /* 全局拦截器已提示 */
    }
  }

  return {
    list,
    loaded,
    loading,
    fetchList,
    invalidate,
    uploadAndFavorite,
    favoriteFromChatMessage,
    remove,
  }
})
