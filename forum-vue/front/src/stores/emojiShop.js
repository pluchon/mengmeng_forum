import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getShopMyPacks } from '@/api/shop'

// 已购表情包 供私信面板「已购」Tab；与收藏散图独立
export const useEmojiShopStore = defineStore('emojiShop', () => {
  const myPacks = ref([])
  const myPacksLoading = ref(false)

  async function fetchMyPacks() {
    myPacksLoading.value = true
    try {
      const res = await getShopMyPacks()
      if (res.code === 0 && Array.isArray(res.data)) {
        myPacks.value = res.data
      }
    } finally {
      myPacksLoading.value = false
    }
  }

  return { myPacks, myPacksLoading, fetchMyPacks }
})
