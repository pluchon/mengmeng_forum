import request from './request'

// 发送弹幕
export function sendDanmaku(data) {
  return request({ url: '/articleDanmaku/send', method: 'put', data })
}

// 按时间窗口拉取弹幕
export function listDanmakuByTimeWindow(params) {
  return request({ url: '/articleDanmaku/listByTimeWindow', method: 'get', params })
}
