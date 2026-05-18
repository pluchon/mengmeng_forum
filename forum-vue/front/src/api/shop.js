import request from './request'

/** 上传商城商品图（封面 / 包内图共用） */
export function uploadEmojiShopImage(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadEmojiShopImage',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

export function createShop(data) {
  return request({ url: '/shop/createShop', method: 'post', data })
}

export function updateShopStatus(shopId, status) {
  return request({ url: '/shop/updateStatus', method: 'put', params: { shopId, status } })
}

export function getShopList(params) {
  return request({ url: '/shop/list', method: 'get', params })
}

export function getShopDetail(shopId) {
  return request({ url: '/shop/detail', method: 'get', params: { shopId } })
}

export function purchaseShop(shopId) {
  return request({ url: '/shop/purchase', method: 'post', params: { shopId } })
}

export function getShopMyPacks() {
  return request({ url: '/shop/myPacks', method: 'get' })
}
