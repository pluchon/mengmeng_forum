import request from './request'

// 上传商城商品图 封面 / 包内图共用
export function uploadEmojiShopImage(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadEmojiShopImage',
    method: 'post',
    data: formData,
    timeout: 120000,
    onUploadProgress,
  })
}

// 批量上传商城包内图（一次最多 9 张，支持部分成功）
export function uploadEmojiShopImages(files, { onUploadProgress, silentHttpError } = {}) {
  const formData = new FormData()
  const list = Array.isArray(files) ? files : [files]
  list.forEach((file) => {
    if (file) formData.append('files', file)
  })
  return request({
    url: '/file/uploadEmojiShopImages',
    method: 'post',
    data: formData,
    timeout: 300000,
    onUploadProgress,
    silentHttpError: !!silentHttpError,
  })
}

export function createShop(data) {
  return request({ url: '/shop/createShop', method: 'post', data })
}

export function saveShopDraft(data) {
  return request({ url: '/shop/draft', method: 'post', data })
}

export function submitShopDraft(data) {
  return request({ url: '/shop/draft/submit', method: 'post', data })
}

export function getShopMyDrafts(params) {
  return request({ url: '/shop/myDrafts', method: 'get', params })
}

export function getShopDraft(draftId) {
  return request({ url: '/shop/draft', method: 'get', params: { draftId } })
}

export function getShopMyPublished(params) {
  return request({ url: '/shop/myPublished', method: 'get', params })
}

export function getShopMyPublishedDetail(shopId) {
  return request({ url: `/shop/myPublished/${shopId}`, method: 'get' })
}

export function updateShopMyPublished(shopId, data) {
  return request({ url: `/shop/myPublished/${shopId}`, method: 'put', data })
}

export function relistShopMyPublished(shopId) {
  return request({ url: `/shop/myPublished/${shopId}/relist`, method: 'put' })
}

export function deleteShopMyPublished(shopId) {
  return request({ url: `/shop/myPublished/${shopId}`, method: 'delete' })
}

export function updateShopStatus(shopId, status) {
  return request({ url: '/shop/updateStatus', method: 'put', params: { shopId, status } })
}

export function getShopList(params) {
  return request({ url: '/shop/list', method: 'get', params })
}

export function getShopDetail(shopId, params = {}) {
  return request({ url: '/shop/detail', method: 'get', params: { shopId, ...params } })
}

export function purchaseShop(shopId) {
  return request({ url: '/shop/purchase', method: 'post', params: { shopId } })
}

export function getShopMyPacks() {
  return request({ url: '/shop/myPacks', method: 'get' })
}

export function getShopMyPurchases(params) {
  return request({ url: '/shop/myPurchases', method: 'get', params })
}

export function getShopEmojiAvailability(params) {
  return request({ url: '/shop/emoji/availability', method: 'get', params })
}
