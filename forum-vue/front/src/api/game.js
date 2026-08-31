import request from './request'

export function getGameCenterOverview() {
  return request({ url: '/game/center/overview', method: 'get' })
}

export function getGamePage(params) {
  return request({ url: '/game/page', method: 'get', params })
}

export function getGameCategories() {
  return request({ url: '/game/categories', method: 'get' })
}

export function getGameStatisticsSummary() {
  return request({ url: '/game/statistics/summary', method: 'get' })
}

export function getGameStatisticsRecords(params) {
  return request({ url: '/game/statistics/records', method: 'get', params })
}

export function getGobangProfile() {
  return request({ url: '/game/gobang/profile', method: 'get' })
}

export function getGobangRecords(params) {
  return request({ url: '/game/gobang/records', method: 'get', params })
}

export function getGobangActiveRooms(params = {}) {
  return request({ url: '/game/gobang/rooms/active', method: 'get', params })
}

export function getGobangReplay(recordId) {
  return request({ url: `/game/gobang/records/${encodeURIComponent(recordId)}/replay`, method: 'get' })
}

export function getGobangRoom(roomId) {
  return request({ url: `/game/gobang/rooms/${encodeURIComponent(roomId)}`, method: 'get' })
}

export function surrenderGobangRoom(roomId) {
  return request({ url: `/game/gobang/rooms/${encodeURIComponent(roomId)}/surrender`, method: 'post' })
}

export function getJinziProfile() {
  return request({ url: '/game/jinzi/profile', method: 'get' })
}

export function getJinziRecords(params) {
  return request({ url: '/game/jinzi/records', method: 'get', params })
}

export function getJinziReplay(recordId) {
  return request({ url: `/game/jinzi/records/${encodeURIComponent(recordId)}/replay`, method: 'get' })
}

export function getJinziRoom(roomId) {
  return request({ url: `/game/jinzi/rooms/${encodeURIComponent(roomId)}`, method: 'get' })
}

export function surrenderJinziRoom(roomId) {
  return request({ url: `/game/jinzi/rooms/${encodeURIComponent(roomId)}/surrender`, method: 'post' })
}

export function getTetrisProfile() {
  return request({ url: '/game/tetris/profile', method: 'get' })
}

export function getTetrisRecords(params) {
  return request({ url: '/game/tetris/records', method: 'get', params })
}

export function getTetrisLeaderboard(params) {
  return request({ url: '/game/tetris/leaderboard', method: 'get', params })
}

export function settleTetris(body) {
  return request({ url: '/game/tetris/settle', method: 'post', data: body })
}

export function getTetrisReplay(recordId) {
  return request({ url: `/game/tetris/records/${encodeURIComponent(recordId)}/replay`, method: 'get' })
}

export function getTetrisPkProfile() {
  return request({ url: '/game/tetris/pk/profile', method: 'get' })
}

export function getTetrisPkRecords(params) {
  return request({ url: '/game/tetris/pk/records', method: 'get', params })
}

export function getTetrisPkLeaderboard(params) {
  return request({ url: '/game/tetris/pk/leaderboard', method: 'get', params })
}

export function getTetrisPkActiveRooms(params = {}) {
  return request({ url: '/game/tetris-pk/rooms/active', method: 'get', params })
}

export function getTetrisPkRoom(roomId) {
  return request({ url: `/game/tetris/pk/rooms/${encodeURIComponent(roomId)}`, method: 'get' })
}

export function surrenderTetrisPkRoom(roomId) {
  return request({ url: `/game/tetris/pk/rooms/${encodeURIComponent(roomId)}/surrender`, method: 'post' })
}

export function getTetrisPkReplay(recordId) {
  return request({ url: `/game/tetris/pk/records/${encodeURIComponent(recordId)}/replay`, method: 'get' })
}
