import request from './request'

export function searchArticles(params) {
  return request({ url: '/search/article', method: 'get', params })
}

export function searchUsers(params) {
  return request({ url: '/search/user', method: 'get', params })
}

export function searchCreatorArticles(params) {
  return request({ url: '/search/article/creator', method: 'get', params })
}
