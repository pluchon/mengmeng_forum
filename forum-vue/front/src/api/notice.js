import request from './request'

// 门户公告中心公开列表
export function getNoticeCenterList() {
  return request({ url: '/notice/center/list', method: 'get' })
}
