import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useEmojiShopStore } from '@/stores/emojiShop'

// 游戏房间聊天表情选择器，复用帖子评论区已购表情包浏览逻辑
export function useGameRoomEmojiPicker() {
  const emojiShopStore = useEmojiShopStore()
  const emojiPanelOpen = ref(false)
  const selectedPackId = ref(null)
  const packBarRef = ref(null)
  const packBarCanScrollLeft = ref(false)
  const packBarCanScrollRight = ref(false)

  const visiblePacks = computed(() => emojiShopStore.myPacks)

  const selectedPack = computed(() => {
    const id = selectedPackId.value
    if (id == null) return visiblePacks.value[0] || null
    return visiblePacks.value.find((pack) => Number(pack.shopId) === Number(id)) || visiblePacks.value[0] || null
  })

  function updatePackBarScrollState() {
    const el = packBarRef.value
    if (!el) {
      packBarCanScrollLeft.value = false
      packBarCanScrollRight.value = false
      return
    }
    const overflow = el.scrollWidth > el.clientWidth + 4
    packBarCanScrollLeft.value = overflow && el.scrollLeft > 2
    packBarCanScrollRight.value = overflow && el.scrollLeft < el.scrollWidth - el.clientWidth - 2
  }

  function onPackBarScroll() {
    updatePackBarScrollState()
  }

  function scrollPackBarLeft() {
    packBarRef.value?.scrollBy({ left: -120, behavior: 'smooth' })
  }

  function scrollPackBarRight() {
    packBarRef.value?.scrollBy({ left: 120, behavior: 'smooth' })
  }

  function selectPack(pack) {
    selectedPackId.value = pack?.shopId ?? null
    nextTick(updatePackBarScrollState)
  }

  async function onEmojiPanelShow() {
    try {
      await emojiShopStore.fetchMyPacks()
      const packs = visiblePacks.value
      if (packs.length && selectedPackId.value == null) {
        selectedPackId.value = packs[0].shopId
      }
      nextTick(updatePackBarScrollState)
    } catch {
      // 已提示
    }
  }

  function pickEmojiUrl(url, onPick) {
    const pack = selectedPack.value
    if (!pack || !url) return
    if (typeof onPick === 'function') {
      onPick(url)
    }
    emojiPanelOpen.value = false
  }

  return {
    emojiShopStore,
    emojiPanelOpen,
    selectedPackId,
    packBarRef,
    packBarCanScrollLeft,
    packBarCanScrollRight,
    visiblePacks,
    selectedPack,
    updatePackBarScrollState,
    onPackBarScroll,
    scrollPackBarLeft,
    scrollPackBarRight,
    selectPack,
    onEmojiPanelShow,
    pickEmojiUrl,
  }
}
