import request from './request'

export function getGameCenterOverview() {
  return request({ url: '/game/center/overview', method: 'get' })
}

export function getGobangProfile() {
  return request({ url: '/game/gobang/profile', method: 'get' })
}

export function getGobangRecords(params) {
  return request({ url: '/game/gobang/records', method: 'get', params })
}

export function getGobangLeaderboard(params) {
  return request({ url: '/game/gobang/leaderboard', method: 'get', params })
}

export function getGobangActiveRooms() {
  return request({ url: '/game/gobang/rooms/active', method: 'get' })
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

export function getJinziLeaderboard(params) {
  return request({ url: '/game/jinzi/leaderboard', method: 'get', params })
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
