import request from './request'

/** 用户端公告中心：已发布公告（每次打开弹窗请求，直查库） */
export function getNoticeCenterList() {
  return request({
    url: '/notice/center/list',
    method: 'get',
    params: { _t: Date.now() },
    headers: { 'Cache-Control': 'no-cache' },
  })
}
