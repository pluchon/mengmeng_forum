import request from './request'

/**
 * 天爱生成：响应为 ApiResponse { code, msg, data }，code 200 表示成功。
 * @param {Record<string, unknown>} [body]
 */
export function generateCaptcha(body = {}) {
  return request({ url: '/captcha/generate', method: 'post', data: body })
}

/**
 * 校验轨迹并换取业务票据；响应为论坛 Result { code, message, data }。
 * @param {Record<string, unknown>} payload
 */
export function checkCaptcha(payload) {
  return request({
    url: '/captcha/check',
    method: 'post',
    data: payload,
    silentBizCodes: [1168],
  })
}
