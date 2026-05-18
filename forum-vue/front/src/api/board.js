import request from './request'

export function getTopBoardList(orderByStatus = 0) {
  return request({
    url: '/board/topBoardList',
    method: 'get',
    params: { orderByStatus }
  })
}

export function getCategoryWithBoards() {
  return request({
    url: '/category/getCategoryWithBoards',
    method: 'get'
  })
}