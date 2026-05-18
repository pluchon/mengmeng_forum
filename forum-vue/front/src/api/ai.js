import request from './request'

/**
 * 封面推荐配图要点（不计入写作配额）
 * @param {{ articleText: string }} data
 */
export function aiCoverHints(data) {
  return request({
    url: '/ai/cover-hints',
    method: 'post',
    data,
    timeout: 120000,
  })
}

/**
 * AI 生图（Java BFF -> ai-server）
 * @param {{ prompt: string, quality: 'normal' | 'premium' }} data
 */
export function aiImage(data) {
  return request({
    url: '/ai/image',
    method: 'post',
    data,
    timeout: 180000,
  })
}

/**
 * 预估 AI 消耗积分
 * @param {{ skill?: string, route?: string, quality?: string }} params
 */
export function aiPriceEstimate(params) {
  return request({
    url: '/ai/price-estimate',
    method: 'get',
    params,
  })
}

