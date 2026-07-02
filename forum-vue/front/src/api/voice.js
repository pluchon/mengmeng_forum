import request from './request'

// 查询 WebRTC ICE 配置
export function getVoiceIceConfig() {
  return request({ url: '/voice/ice-config', method: 'get' })
}
