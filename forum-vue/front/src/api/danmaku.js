import request from './request'

// 发送弹幕
export function sendDanmaku(data) {
  return request({ url: '/articleDanmaku/send', method: 'put', data })
}

// 按时间窗口拉取弹幕
export function listDanmakuByTimeWindow(params) {
  return request({ url: '/articleDanmaku/listByTimeWindow', method: 'get', params })
}

// 点赞弹幕
export function likeDanmaku(danmakuId) {
  return request({ url: '/articleDanmaku/like', method: 'put', params: { danmakuId } })
}

// 取消点赞弹幕
export function unlikeDanmaku(danmakuId) {
  return request({ url: '/articleDanmaku/like', method: 'delete', params: { danmakuId } })
}
