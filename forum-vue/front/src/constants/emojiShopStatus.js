// 表情包商店上架状态 与后端 shop 状态码一致
export const EMOJI_SHOP_STATUS = {
  PENDING: 0,
  ON_SHELF: 1,
  OFF_SHELF: 2,
}

export function emojiShopStatusLabel(status) {
  if (status === EMOJI_SHOP_STATUS.ON_SHELF) {
    return { text: '上架中', type: 'success' }
  }
  if (status === EMOJI_SHOP_STATUS.OFF_SHELF) {
    return { text: '已下架', type: 'warning' }
  }
  if (status === EMOJI_SHOP_STATUS.PENDING) {
    return { text: '待审核', type: 'info' }
  }
  return { text: '未知', type: 'info' }
}
