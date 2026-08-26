import request from './request'

function newRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now()}${Math.random().toString(16).slice(2)}`
}

export function getStarlightWallet() {
  return request({ url: '/starlight/wallet', method: 'get' })
}

export function getStarlightShopItems(params = {}) {
  return request({ url: '/starlight/shop/items', method: 'get', params })
}

export function exchangeStarlightItem(data) {
  return request({
    url: '/starlight/shop/exchange',
    method: 'post',
    data: {
      itemId: data.itemId,
      requestId: data.requestId || newRequestId(),
    },
  })
}

export function useStarlightItem(data) {
  return request({
    url: '/starlight/shop/use',
    method: 'post',
    data: {
      exchangeId: data.exchangeId,
    },
  })
}

export function getStarlightExchanges(params = {}) {
  return request({ url: '/starlight/shop/exchanges', method: 'get', params })
}
