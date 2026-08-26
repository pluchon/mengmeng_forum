import request from './request'

// 天爱生成：响应为 ApiResponse { code, msg, data }，code 200 表示成功
export function generateCaptcha(body = {}) {
  return request({
    url: '/captcha/generate',
    method: 'post',
    data: body,
    // 验证码系统不可用时由弹窗统一提示，避免再弹一条网络错误
    silentHttpError: true,
  })
}

// 校验轨迹并换取业务票据；响应为论坛 Result { code, message, data }
export function checkCaptcha(payload) {
  return request({
    url: '/captcha/check',
    method: 'post',
    data: payload,
    silentBizCodes: [1168],
    silentHttpError: true,
  })
}
