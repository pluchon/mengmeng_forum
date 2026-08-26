import request from './request'

// POST /sms/login：不传 code 发码；传 code 登录 查询 参数，与后端一致
export function smsLogin(phoneNumber, code, captchaTicket) {
  const params = { phoneNumber }
  if (code !== undefined && code !== null && String(code).trim() !== '') {
    params.code = code
  }
  if (captchaTicket) {
    params.captchaTicket = captchaTicket
  }
  return request({ url: '/sms/login', method: 'post', params })
}

// POST /mail/login：同上
export function mailLogin(email, code, captchaTicket) {
  const params = { email }
  if (code !== undefined && code !== null && String(code).trim() !== '') {
    params.code = code
  }
  if (captchaTicket) {
    params.captchaTicket = captchaTicket
  }
  return request({ url: '/mail/login', method: 'post', params })
}

export function register(data) {
  return request({ url: '/user/register', method: 'post', data })
}

export function login(data, captchaTicket) {
  const headers = {}
  if (captchaTicket) {
    headers['X-Captcha-Ticket'] = captchaTicket
  }
  return request({ url: '/user/login', method: 'post', data, headers })
}

// 找回密码阶段一：仅账号，发重置验证码
export function sendMailCodeForReset(email, captchaTicket) {
  return request({
    url: '/user/findPasswordByMail',
    method: 'post',
    params: { email, captchaTicket },
  })
}

export function sendSmsCodeForReset(phoneNumber, captchaTicket) {
  return request({
    url: '/user/findPasswordBySms',
    method: 'post',
    params: { phoneNumber, captchaTicket },
  })
}

// 找回密码阶段二
export function findPasswordByMail(email, code, newPassword, captchaTicket) {
  return request({
    url: '/user/findPasswordByMail',
    method: 'post',
    params: { email, code, newPassword, captchaTicket },
  })
}

export function findPasswordBySms(phoneNumber, code, newPassword, captchaTicket) {
  return request({
    url: '/user/findPasswordBySms',
    method: 'post',
    params: { phoneNumber, code, newPassword, captchaTicket },
  })
}
