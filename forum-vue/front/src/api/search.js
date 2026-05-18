import request from './request'

/** @param params {{ keyword: string, pageNum?: number, pageSize?: number, ai?: boolean }} */
export function searchArticles(params) {
  return request({ url: '/search/article', method: 'get', params })
}

/** @param params {{ keyword: string, pageNum?: number, pageSize?: number, ai?: boolean }} */
export function searchUsers(params) {
  return request({ url: '/search/user', method: 'get', params })
}
