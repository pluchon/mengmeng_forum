import { computed } from 'vue'
import { isEmojiShopMediaUrl } from '@/utils/chatMedia'
import emojiPackIconUrl from '@/assets/svg/表情包.svg?url'

const REPLY_MEDIA_TYPE_IMAGE = 1
const REPLY_MEDIA_TYPE_SHOP_EMOJI = 2

const props = defineProps({
  mediaList: { type: Array, default: () => [] },
})

const emit = defineEmits(['open-shop'])

const imagePreviewUrls = computed(() =>
  (props.mediaList || [])
    .filter((item) => isImageItem(item))
    .map((item) => item.mediaUrl),
)

const imageIndexMap = computed(() => {
  const map = {}
  let imageIdx = 0
  ;(props.mediaList || []).forEach((item, idx) => {
    if (isImageItem(item)) {
      map[idx] = imageIdx
      imageIdx += 1
    }
  })
  return map
})

function isImageItem(item) {
  return Number(item?.mediaType) === REPLY_MEDIA_TYPE_IMAGE && !isEmojiShopMediaUrl(item?.mediaUrl)
}

function isShopEmoji(item) {
  return Number(item?.mediaType) === REPLY_MEDIA_TYPE_SHOP_EMOJI || isEmojiShopMediaUrl(item?.mediaUrl)
}

function onEmojiClick(item) {
  if (isShopEmoji(item) && item?.shopId) {
    emit('open-shop', Number(item.shopId))
  }
}

defineExpose({
  emojiPackIconUrl,
  imageIndexMap,
  imagePreviewUrls,
  isImageItem,
  onEmojiClick,
})
