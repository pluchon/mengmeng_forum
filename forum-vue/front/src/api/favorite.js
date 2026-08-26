import request from './request'

// 收藏夹 Folder

export function createFavoriteFolder(data) {
  return request({ url: '/favorite/folder/create', method: 'post', data })
}

export function updateFavoriteFolder(data) {
  return request({ url: '/favorite/folder/update', method: 'put', data })
}

export function deleteFavoriteFolder(folderId) {
  return request({ url: `/favorite/folder/${folderId}`, method: 'delete' })
}

export function uploadFavoriteFolderCover(file, { onUploadProgress } = {}) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/file/uploadFavoriteFolderCover',
    method: 'post',
    data: formData,
    onUploadProgress,
  })
}

export function getMyFavoriteFolders(params = {}) {
  return request({ url: '/favorite/folder/myList', method: 'get', params })
}

export function getUserFavoriteFolders(userId, params = {}) {
  return request({ url: '/favorite/folder/userList', method: 'get', params: { userId, ...params } })
}

export function getFavoriteFolderArticles(folderId, params) {
  return request({ url: `/favorite/folder/${folderId}/articles`, method: 'get', params })
}

// 帖子收藏 Article Favorite

export function saveArticleFavorite(data) {
  return request({ url: '/favorite/article/save', method: 'post', data })
}

export function cancelArticleFavorite(articleId) {
  return request({ url: '/favorite/article/cancel', method: 'delete', params: { articleId } })
}

export function moveArticleFavorite(data) {
  return request({ url: '/favorite/article/move', method: 'put', data })
}
