import request from './request'

export function getBagItems(params = {}) {
  return request({ url: '/bag/items', method: 'get', params })
}

export function useBagItem(bagItemId) {
  return request({ url: '/bag/use', method: 'post', data: { bagItemId } })
}

export function getBagUnusedCount() {
  return request({ url: '/bag/unused-count', method: 'get' })
}
